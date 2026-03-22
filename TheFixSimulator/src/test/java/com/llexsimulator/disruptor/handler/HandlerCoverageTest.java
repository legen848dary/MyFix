package com.llexsimulator.disruptor.handler;

import com.llexsimulator.aeron.MetricsPublisher;
import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.fill.FillProfileManager;
import com.llexsimulator.metrics.MetricsRegistry;
import com.llexsimulator.order.OrderRepository;
import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.OrderSide;
import com.llexsimulator.sbe.OrderType;
import com.llexsimulator.sbe.RejectReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static com.llexsimulator.testutil.OrderEventFixtures.decodeFillInstruction;
import static com.llexsimulator.testutil.OrderEventFixtures.newLimitOrderEvent;
import static com.llexsimulator.testutil.OrderEventFixtures.writeFillInstruction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HandlerCoverageTest {

    @Test
    void validationHandlerMarksValidAndInvalidOrders() {
        ValidationHandler handler = new ValidationHandler();

        OrderEvent valid = newLimitOrderEvent(1L, 1L, OrderSide.BUY, 10_000L, 10_000_000_000L);
        handler.onEvent(valid, 0L, true);
        assertTrue(valid.isValid);

        OrderEvent missingSide = newLimitOrderEvent(2L, 1L, OrderSide.NULL_VAL, 10_000L, 10_000_000_000L);
        handler.onEvent(missingSide, 0L, true);
        assertFalse(missingSide.isValid);
        assertEquals(FillBehaviorType.REJECT, decodeFillInstruction(missingSide).fillBehavior());

        OrderEvent invalidQty = newLimitOrderEvent(3L, 1L, OrderSide.BUY, 0L, 10_000_000_000L);
        handler.onEvent(invalidQty, 0L, true);
        assertFalse(invalidQty.isValid);

        OrderEvent invalidPrice = newLimitOrderEvent(4L, 1L, OrderSide.SELL, 10_000L, 0L);
        invalidPrice.nosEncoder.wrapAndApplyHeader(invalidPrice.orderBuffer, 0, invalidPrice.headerEncoder)
                .correlationId(4L)
                .sessionConnectionId(1L)
                .arrivalTimeNs(9L)
                .side(OrderSide.SELL)
                .orderType(OrderType.LIMIT)
                .timeInForce(com.llexsimulator.sbe.TimeInForce.DAY)
                .orderQty(10_000L)
                .price(0L)
                .stopPx(0L)
                .transactTimeNs(9L)
                .fixVersion(com.llexsimulator.sbe.FixVersion.FIX44);
        invalidPrice.nosDecoder.wrapAndApplyHeader(invalidPrice.orderBuffer, 0, invalidPrice.headerDecoder);
        handler.onEvent(invalidPrice, 0L, true);
        assertFalse(invalidPrice.isValid);
        assertEquals(RejectReason.INVALID_PRICE, decodeFillInstruction(invalidPrice).rejectReasonCode());
    }

    @Test
    void fillStrategyHandlerPopulatesFillInstructionAndClaimsOrderState() {
        FillProfileManager manager = new FillProfileManager();
        OrderRepository repository = new OrderRepository(2);
        FillStrategyHandler handler = new FillStrategyHandler(manager, repository);

        manager.activate("partial-50pct");
        OrderEvent event = newLimitOrderEvent(10L, 91L, OrderSide.BUY, 50_000L, 2_500_000_000L);
        event.isValid = true;

        handler.onEvent(event, 0L, true);

        assertEquals(FillBehaviorType.PARTIAL_FILL, decodeFillInstruction(event).fillBehavior());
        assertEquals(5_000, decodeFillInstruction(event).fillPctBps());
        assertNotNull(repository.get(10L));
        assertEquals(10L, repository.get(10L).getCorrelationId());
        assertEquals(91L, repository.get(10L).getSessionConnectionId());
        assertEquals(50_000L, repository.get(10L).getOrderQty());
    }

    @Test
    void fillStrategyHandlerSkipsInvalidEventsAndHandlesPoolExhaustion() {
        FillProfileManager manager = new FillProfileManager();
        FillStrategyHandler handlerWithEmptyPool = new FillStrategyHandler(manager, new OrderRepository(0));

        OrderEvent invalid = newLimitOrderEvent(20L, 9L, OrderSide.BUY, 10_000L, 100L);
        invalid.isValid = false;
        handlerWithEmptyPool.onEvent(invalid, 0L, true);
        assertFalse(invalid.isValid);

        OrderEvent valid = newLimitOrderEvent(21L, 9L, OrderSide.BUY, 10_000L, 100L);
        valid.isValid = true;
        handlerWithEmptyPool.onEvent(valid, 0L, true);
        assertEquals(FillBehaviorType.IMMEDIATE_FULL_FILL, decodeFillInstruction(valid).fillBehavior());
    }

    @Test
    void metricsPublishHandlerRecordsCountersPublishesSnapshotsAndEmitsOrderEvents() {
        MetricsRegistry registry = new MetricsRegistry();
        MetricsPublisher publisher = mock(MetricsPublisher.class);
        MetricsPublishHandler handler = new MetricsPublishHandler(registry, publisher, 1, true);
        List<String> payloads = new ArrayList<>();
        handler.setOrderEventCallback(payloads::add);

        OrderEvent rejectEvent = newLimitOrderEvent(30L, 1L, OrderSide.SELL, 20_000L, 9_876_543_210L);
        writeFillInstruction(rejectEvent, FillBehaviorType.REJECT, 0, 0, 0L, 0L, RejectReason.INVALID_PRICE);
        handler.onEvent(rejectEvent, 0L, true);

        OrderEvent cancelEvent = newLimitOrderEvent(31L, 1L, OrderSide.BUY, 20_000L, 9_876_543_210L);
        writeFillInstruction(cancelEvent, FillBehaviorType.NO_FILL_IOC_CANCEL, 0, 0, 0L, 0L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(cancelEvent, 1L, true);

        OrderEvent partialCancelEvent = newLimitOrderEvent(32L, 1L, OrderSide.BUY, 20_000L, 9_876_543_210L);
        writeFillInstruction(partialCancelEvent, FillBehaviorType.PARTIAL_THEN_CANCEL, 5_000, 1, 0L, 9_876_543_210L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(partialCancelEvent, 2L, true);

        OrderEvent partialFillEvent = newLimitOrderEvent(33L, 1L, OrderSide.BUY, 20_000L, 9_876_543_210L);
        writeFillInstruction(partialFillEvent, FillBehaviorType.PARTIAL_FILL, 5_000, 1, 0L, 9_876_543_210L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(partialFillEvent, 3L, true);

        OrderEvent fillEvent = newLimitOrderEvent(34L, 1L, OrderSide.BUY, 20_000L, 9_876_543_210L);
        writeFillInstruction(fillEvent, FillBehaviorType.IMMEDIATE_FULL_FILL, 10_000, 1, 0L, 9_876_543_210L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(fillEvent, 4L, true);

        verify(publisher, times(5)).publish(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
        assertEquals(5L, registry.getOrdersReceived());
        assertEquals(6L, registry.getExecReportsSent());
        assertEquals(3L, registry.getFillsSent());
        assertEquals(1L, registry.getRejectsSent());
        assertEquals(2L, registry.getCancelsSent());
        assertEquals(5, payloads.size());
        assertTrue(payloads.getFirst().contains("REJECTED"));
        assertTrue(payloads.get(1).contains("CANCELED"));
        assertTrue(payloads.get(2).contains("PARTIAL_FILL"));
        assertTrue(payloads.get(4).contains("FILL"));
    }

    @Test
    void metricsPublishHandlerSkipsLivePublishingWhenDisabledOrCallbackMissing() {
        MetricsRegistry registry = new MetricsRegistry();
        MetricsPublisher publisher = mock(MetricsPublisher.class);
        MetricsPublishHandler disabled = new MetricsPublishHandler(registry, publisher, 1, false);
        OrderEvent event = newLimitOrderEvent(40L, 1L, OrderSide.BUY, 10_000L, 100L);
        writeFillInstruction(event, FillBehaviorType.IMMEDIATE_FULL_FILL, 10_000, 1, 0L, 100L, RejectReason.SIMULATOR_REJECT);

        disabled.onEvent(event, 0L, true);
        verify(publisher, never()).publish(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong());

        MetricsPublishHandler enabledWithoutCallback = new MetricsPublishHandler(new MetricsRegistry(), publisher, 1, true);
        enabledWithoutCallback.onEvent(event, 0L, true);
        verify(publisher, times(1)).publish(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void compositeOrderEventHandlerInvokesAllStagesInOrder() {
        ValidationHandler validationHandler = mock(ValidationHandler.class);
        FillStrategyHandler fillStrategyHandler = mock(FillStrategyHandler.class);
        ExecutionReportHandler executionReportHandler = mock(ExecutionReportHandler.class);
        MetricsPublishHandler metricsPublishHandler = mock(MetricsPublishHandler.class);
        CompositeOrderEventHandler composite = new CompositeOrderEventHandler(
                validationHandler, fillStrategyHandler, executionReportHandler, metricsPublishHandler);
        OrderEvent event = new OrderEvent();

        composite.onEvent(event, 7L, false);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(validationHandler).onEvent(captor.capture(), org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(false));
        assertSame(event, captor.getValue());
        verify(fillStrategyHandler).onEvent(event, 7L, false);
        verify(executionReportHandler).onEvent(event, 7L, false);
        verify(metricsPublishHandler).onEvent(event, 7L, false);
    }
}

