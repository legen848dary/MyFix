package com.llexsimulator.disruptor.handler;

import com.llexsimulator.aeron.MetricsPublisher;
import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.engine.FixConnection;
import com.llexsimulator.engine.FixOutboundSender;
import com.llexsimulator.engine.OrderSessionRegistry;
import com.llexsimulator.fill.FillProfileManager;
import com.llexsimulator.metrics.MetricsRegistry;
import com.llexsimulator.order.OrderRepository;
import com.llexsimulator.order.OrderState;
import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.OrderSide;
import com.llexsimulator.sbe.OrderType;
import com.llexsimulator.sbe.RejectReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.co.real_logic.artio.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static com.llexsimulator.testutil.OrderEventFixtures.decodeFillInstruction;
import static com.llexsimulator.testutil.OrderEventFixtures.newAmendRequestEvent;
import static com.llexsimulator.testutil.OrderEventFixtures.newCancelRequestEvent;
import static com.llexsimulator.testutil.OrderEventFixtures.newLimitOrderEvent;
import static com.llexsimulator.testutil.OrderEventFixtures.ascii16;
import static com.llexsimulator.testutil.OrderEventFixtures.ascii36;
import static com.llexsimulator.testutil.OrderEventFixtures.writeFillInstruction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        OrderEvent invalidCancel = newCancelRequestEvent(5L, 1L, "CXL-5", "", OrderSide.BUY, "AAPL");
        handler.onEvent(invalidCancel, 0L, true);
        assertFalse(invalidCancel.isValid);
        assertEquals(FillBehaviorType.REJECT, decodeFillInstruction(invalidCancel).fillBehavior());

        OrderEvent invalidCancelSide = newCancelRequestEvent(55L, 1L, "CXL-55", "ORIG-55", OrderSide.NULL_VAL, "AAPL");
        handler.onEvent(invalidCancelSide, 0L, true);
        assertFalse(invalidCancelSide.isValid);
        assertEquals(FillBehaviorType.REJECT, decodeFillInstruction(invalidCancelSide).fillBehavior());

        OrderEvent invalidAmend = newAmendRequestEvent(6L, 1L, "AMD-6", "", OrderSide.BUY, 10_000L, 10_000_000_000L, "AAPL");
        handler.onEvent(invalidAmend, 0L, true);
        assertFalse(invalidAmend.isValid);
        assertEquals(FillBehaviorType.REJECT, decodeFillInstruction(invalidAmend).fillBehavior());
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
    void fillStrategyHandlerResolvesCancelAndAmendRequestsAgainstActiveOrders() {
        FillProfileManager manager = new FillProfileManager();
        OrderRepository repository = new OrderRepository(4);
        FillStrategyHandler handler = new FillStrategyHandler(manager, repository);

        seedActiveOrder(repository, 51L, 91L, "ORIG-51", OrderSide.BUY, 20_000L, 1_500_000_000L, "AAPL");

        OrderEvent cancelEvent = newCancelRequestEvent(60L, 91L, "CXL-60", "ORIG-51", OrderSide.BUY, "AAPL");
        cancelEvent.isValid = true;
        handler.onEvent(cancelEvent, 0L, true);
        assertEquals(FillBehaviorType.NO_FILL_IOC_CANCEL, decodeFillInstruction(cancelEvent).fillBehavior());
        assertEquals(51L, cancelEvent.referencedCorrelationId);

        OrderEvent amendEvent = newAmendRequestEvent(61L, 91L, "AMD-61", "ORIG-51", OrderSide.BUY, 25_000L, 1_600_000_000L, "AAPL");
        amendEvent.isValid = true;
        handler.onEvent(amendEvent, 1L, true);
        assertEquals(FillBehaviorType.IMMEDIATE_FULL_FILL, decodeFillInstruction(amendEvent).fillBehavior());
        assertEquals(51L, amendEvent.referencedCorrelationId);
        assertNotNull(repository.get(61L));
        assertSame(repository.get(61L), repository.findByClOrdId(ascii36("AMD-61"), 36));
    }

    @Test
    void fillStrategyHandlerRejectsUnknownCancelAndAmendTargets() {
        FillProfileManager manager = new FillProfileManager();
        OrderRepository repository = new OrderRepository(1);
        FillStrategyHandler handler = new FillStrategyHandler(manager, repository);

        OrderEvent cancelEvent = newCancelRequestEvent(62L, 91L, "CXL-62", "MISSING", OrderSide.BUY, "AAPL");
        cancelEvent.isValid = true;
        handler.onEvent(cancelEvent, 0L, true);
        assertFalse(cancelEvent.isValid);
        assertEquals(FillBehaviorType.REJECT, decodeFillInstruction(cancelEvent).fillBehavior());

        OrderEvent amendEvent = newAmendRequestEvent(63L, 91L, "AMD-63", "MISSING", OrderSide.BUY, 25_000L, 1_600_000_000L, "AAPL");
        amendEvent.isValid = true;
        handler.onEvent(amendEvent, 1L, true);
        assertFalse(amendEvent.isValid);
        assertEquals(FillBehaviorType.REJECT, decodeFillInstruction(amendEvent).fillBehavior());
        assertNull(repository.get(63L));
    }

    @Test
    void fillStrategyHandlerRejectsAmendWhenReplacementPoolIsExhausted() {
        FillProfileManager manager = new FillProfileManager();
        OrderRepository repository = new OrderRepository(1);
        FillStrategyHandler handler = new FillStrategyHandler(manager, repository);

        seedActiveOrder(repository, 64L, 91L, "ORIG-64", OrderSide.BUY, 20_000L, 1_500_000_000L, "AAPL");

        OrderEvent amendEvent = newAmendRequestEvent(65L, 91L, "AMD-65", "ORIG-64", OrderSide.BUY, 25_000L, 1_600_000_000L, "AAPL");
        amendEvent.isValid = true;
        handler.onEvent(amendEvent, 0L, true);

        assertFalse(amendEvent.isValid);
        assertEquals(0L, amendEvent.referencedCorrelationId);
        assertEquals(FillBehaviorType.REJECT, decodeFillInstruction(amendEvent).fillBehavior());
        assertNull(repository.get(65L));
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
    void metricsPublishHandlerCountsExplicitCancelAndAmendFlows() {
        MetricsRegistry registry = new MetricsRegistry();
        MetricsPublishHandler handler = new MetricsPublishHandler(registry, mock(MetricsPublisher.class), 1, false);

        OrderEvent cancelEvent = newCancelRequestEvent(70L, 1L, "CXL-70", "ORIG-70", OrderSide.BUY, "AAPL");
        cancelEvent.referencedCorrelationId = 700L;
        writeFillInstruction(cancelEvent, FillBehaviorType.NO_FILL_IOC_CANCEL, 0, 0, 0L, 0L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(cancelEvent, 0L, true);

        OrderEvent amendEvent = newAmendRequestEvent(71L, 1L, "AMD-71", "ORIG-71", OrderSide.BUY, 25_000L, 1_200_000_000L, "AAPL");
        amendEvent.referencedCorrelationId = 701L;
        writeFillInstruction(amendEvent, FillBehaviorType.IMMEDIATE_FULL_FILL, 10_000, 1, 0L, 1_200_000_000L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(amendEvent, 1L, true);

        assertEquals(2L, registry.getOrdersReceived());
        assertEquals(3L, registry.getExecReportsSent());
        assertEquals(1L, registry.getFillsSent());
        assertEquals(0L, registry.getRejectsSent());
        assertEquals(2L, registry.getCancelsSent());
    }

    @Test
    void metricsPublishHandlerCountsCancelRejectAndAmendCancelVariants() {
        MetricsRegistry registry = new MetricsRegistry();
        MetricsPublishHandler handler = new MetricsPublishHandler(registry, mock(MetricsPublisher.class), 1, false);

        OrderEvent cancelRejectEvent = newCancelRequestEvent(72L, 1L, "CXL-72", "MISSING", OrderSide.BUY, "AAPL");
        writeFillInstruction(cancelRejectEvent, FillBehaviorType.REJECT, 0, 0, 0L, 0L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(cancelRejectEvent, 0L, true);

        OrderEvent amendRejectEvent = newAmendRequestEvent(721L, 1L, "AMD-721", "MISSING", OrderSide.BUY, 25_000L, 1_200_000_000L, "AAPL");
        writeFillInstruction(amendRejectEvent, FillBehaviorType.REJECT, 0, 0, 0L, 0L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(amendRejectEvent, 1L, true);

        OrderEvent amendCancelEvent = newAmendRequestEvent(73L, 1L, "AMD-73", "ORIG-73", OrderSide.BUY, 25_000L, 1_200_000_000L, "AAPL");
        amendCancelEvent.referencedCorrelationId = 730L;
        writeFillInstruction(amendCancelEvent, FillBehaviorType.NO_FILL_IOC_CANCEL, 0, 0, 0L, 0L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(amendCancelEvent, 2L, true);

        OrderEvent amendPartialCancelEvent = newAmendRequestEvent(74L, 1L, "AMD-74", "ORIG-74", OrderSide.BUY, 25_000L, 1_200_000_000L, "AAPL");
        amendPartialCancelEvent.referencedCorrelationId = 740L;
        writeFillInstruction(amendPartialCancelEvent, FillBehaviorType.PARTIAL_THEN_CANCEL, 5_000, 1, 0L, 1_200_000_000L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(amendPartialCancelEvent, 3L, true);

        assertEquals(4L, registry.getOrdersReceived());
        assertEquals(7L, registry.getExecReportsSent());
        assertEquals(1L, registry.getFillsSent());
        assertEquals(2L, registry.getRejectsSent());
        assertEquals(4L, registry.getCancelsSent());
    }

    @Test
    void executionReportHandlerProcessesCancelAndAmendRequests() {
        OrderSessionRegistry registry = mock(OrderSessionRegistry.class);
        FixOutboundSender outboundSender = mock(FixOutboundSender.class);
        OrderRepository repository = new OrderRepository(4);
        FixConnection connection = mock(FixConnection.class);
        when(connection.session()).thenReturn(mock(Session.class));
        when(registry.get(91L)).thenReturn(connection);

        seedActiveOrder(repository, 81L, 91L, "ORIG-81", OrderSide.BUY, 20_000L, 1_500_000_000L, "AAPL");
        ExecutionReportHandler handler = new ExecutionReportHandler(registry, repository, outboundSender);

        OrderEvent cancelEvent = newCancelRequestEvent(82L, 91L, "CXL-82", "ORIG-81", OrderSide.BUY, "AAPL");
        cancelEvent.referencedCorrelationId = 81L;
        writeFillInstruction(cancelEvent, FillBehaviorType.NO_FILL_IOC_CANCEL, 0, 0, 0L, 0L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(cancelEvent, 0L, true);
        verify(outboundSender, times(1)).enqueueExecutionReport(eq(connection), eq("CANCELED/CANCELED"), any(), anyInt(), any(), anyInt(), any(), anyInt(), any(), anyInt(), anyChar(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyChar(), anyChar(), anyInt());
        assertNull(repository.get(81L));

        seedActiveOrder(repository, 83L, 91L, "ORIG-83", OrderSide.BUY, 20_000L, 1_500_000_000L, "AAPL");
        OrderState replacement = repository.claim(84L);
        assertNotNull(replacement);
        replacement.setCorrelationId(84L);
        replacement.setSessionConnectionId(91L);
        replacement.setOrderQty(25_000L);
        replacement.setPrice(1_600_000_000L);
        replacement.setLeavesQty(25_000L);
        replacement.setCumQty(0L);
        replacement.setSide((byte) OrderSide.BUY.value());
        replacement.setOrderType((byte) OrderType.LIMIT.value());
        replacement.setClOrdId(ascii36("AMD-84"), 0, 36);
        replacement.setSymbol(ascii16("AAPL"), 0, 16);
        repository.indexClOrdId(84L, ascii36("AMD-84"), 36);

        OrderEvent amendEvent = newAmendRequestEvent(84L, 91L, "AMD-84", "ORIG-83", OrderSide.BUY, 25_000L, 1_600_000_000L, "AAPL");
        amendEvent.referencedCorrelationId = 83L;
        writeFillInstruction(amendEvent, FillBehaviorType.IMMEDIATE_FULL_FILL, 10_000, 1, 0L, 1_600_000_000L, RejectReason.SIMULATOR_REJECT);
        handler.onEvent(amendEvent, 1L, true);

        ArgumentCaptor<String> outboundEvents = ArgumentCaptor.forClass(String.class);
        verify(outboundSender, times(3)).enqueueExecutionReport(eq(connection), outboundEvents.capture(), any(), anyInt(), any(), anyInt(), any(), anyInt(), any(), anyInt(), anyChar(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyChar(), anyChar(), anyInt());
        assertEquals(List.of("CANCELED/CANCELED", "CANCELED/CANCELED", "FILL/FILLED"), outboundEvents.getAllValues());
        assertNull(repository.get(83L));
        assertNull(repository.get(84L));
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

    private static void seedActiveOrder(OrderRepository repository, long correlationId, long sessionConnectionId,
                                        String clOrdId, OrderSide side, long orderQty, long price, String symbol) {
        OrderState state = repository.claim(correlationId);
        assertNotNull(state);
        state.setCorrelationId(correlationId);
        state.setSessionConnectionId(sessionConnectionId);
        state.setOrderQty(orderQty);
        state.setPrice(price);
        state.setLeavesQty(orderQty);
        state.setCumQty(0L);
        state.setSide((byte) side.value());
        state.setOrderType((byte) OrderType.LIMIT.value());
        state.setClOrdId(ascii36(clOrdId), 0, 36);
        state.setSymbol(ascii16(symbol), 0, 16);
        repository.indexClOrdId(correlationId, ascii36(clOrdId), 36);
    }
}

