package com.llexsimulator.disruptor.handler;

import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.disruptor.OrderRequestType;
import com.llexsimulator.fill.FillBehaviorConfig;
import com.llexsimulator.fill.FillProfileManager;
import com.llexsimulator.fill.FillStrategy;
import com.llexsimulator.fill.FillStrategyFactory;
import com.llexsimulator.order.OrderRepository;
import com.llexsimulator.order.OrderState;
import com.lmax.disruptor.EventHandler;
import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.RejectReason;

/**
 * Stage 2: selects and applies a {@link FillStrategy} to populate the
 * {@code FillInstruction} in the event, then claims an {@link OrderState}.
 *
 * <p>The active config is read with a single {@code volatile} load — no locking.
 */
public final class FillStrategyHandler implements EventHandler<OrderEvent> {

    private final FillProfileManager profileManager;
    private final OrderRepository    orderRepository;

    public FillStrategyHandler(FillProfileManager profileManager, OrderRepository orderRepository) {
        this.profileManager  = profileManager;
        this.orderRepository = orderRepository;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        event.referencedCorrelationId = 0L;
        if (!event.isValid) {
            // ValidationHandler already wrote a REJECT FillInstruction; skip strategy selection
            return;
        }

        if (event.requestType == OrderRequestType.CANCEL) {
            OrderState referencedState = orderRepository.findByClOrdId(event.origClOrdIdBytes, event.origClOrdIdBytes.length);
            if (referencedState == null) {
                writeReject(event);
                return;
            }
            event.referencedCorrelationId = referencedState.getCorrelationId();
            writeCancelInstruction(event);
            return;
        }

        OrderState referencedState = null;
        if (event.requestType == OrderRequestType.AMEND) {
            referencedState = orderRepository.findByClOrdId(event.origClOrdIdBytes, event.origClOrdIdBytes.length);
            if (referencedState == null) {
                writeReject(event);
                return;
            }
        }

        // Single volatile read — no lock, no synchronization
        FillBehaviorConfig config = profileManager.getActiveConfig();

        // Wrap the fill instruction encoder before the strategy writes into it
        event.fillInstructionEncoder.wrapAndApplyHeader(
                event.fillInstructionBuffer, 0, event.headerEncoder);

        // Apply strategy: writes FillInstruction into event.fillInstructionBuffer
        FillStrategy strategy = FillStrategyFactory.getStrategy(config.behaviorType);
        strategy.apply(event, config);

        // Re-wrap decoder for the next handler
        event.fillInstructionDecoder.wrapAndApplyHeader(
                event.fillInstructionBuffer, 0, event.headerDecoder);

        // Claim and initialise an OrderState from the pool
        OrderState state = orderRepository.claim(event.correlationId);
        if (state != null) {
            if (referencedState != null) {
                event.referencedCorrelationId = referencedState.getCorrelationId();
            }
            state.setCorrelationId(event.correlationId);
            state.setSessionConnectionId(event.sessionConnectionId);
            state.setArrivalTimeNs(event.arrivalTimeNs);
            state.setOrderQty(event.nosDecoder.orderQty());
            state.setPrice(event.nosDecoder.price());
            state.setLeavesQty(event.nosDecoder.orderQty());
            state.setCumQty(0L);
            state.setSide((byte) event.nosDecoder.side().value());
            state.setOrderType((byte) event.nosDecoder.orderType().value());

            // Copy clOrdId bytes (SBE decoder uses byte[])
            event.nosDecoder.getClOrdId(event.clOrdIdBytes, 0);
            state.setClOrdId(event.clOrdIdBytes, 0, 36);
            orderRepository.indexClOrdId(event.correlationId, event.clOrdIdBytes, 36);

            // Copy symbol bytes
            event.nosDecoder.getSymbol(event.symbolBytes, 0);
            state.setSymbol(event.symbolBytes, 0, 16);
        } else if (event.requestType == OrderRequestType.AMEND) {
            event.referencedCorrelationId = 0L;
            writeReject(event);
        }
    }


    private static void writeCancelInstruction(OrderEvent event) {
        event.fillInstructionEncoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerEncoder)
                .correlationId(event.correlationId)
                .fillBehavior(FillBehaviorType.NO_FILL_IOC_CANCEL)
                .fillPctBps(0)
                .numPartialFills((short) 0)
                .delayNs(0L)
                .fillPrice(0L)
                .rejectReasonCode(RejectReason.SIMULATOR_REJECT)
                .randomMinQtyPct(0)
                .randomMaxQtyPct(0)
                .randomMinDelayNs(0L)
                .randomMaxDelayNs(0L);
        event.fillInstructionDecoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerDecoder);
    }

    private static void writeReject(OrderEvent event) {
        event.isValid = false;
        event.fillInstructionEncoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerEncoder)
                .correlationId(event.correlationId)
                .fillBehavior(FillBehaviorType.REJECT)
                .fillPctBps(0)
                .numPartialFills((short) 0)
                .delayNs(0L)
                .fillPrice(0L)
                .rejectReasonCode(RejectReason.SIMULATOR_REJECT)
                .randomMinQtyPct(0)
                .randomMaxQtyPct(0)
                .randomMinDelayNs(0L)
                .randomMaxDelayNs(0L);
        event.fillInstructionDecoder.wrapAndApplyHeader(event.fillInstructionBuffer, 0, event.headerDecoder);
    }
}

