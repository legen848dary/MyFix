package com.llexsimulator.order;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.llexsimulator.testutil.OrderEventFixtures.ascii36;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class OrderDomainCoverageTest {

    @Test
    void orderIdGeneratorSupportsNumericByteCharAndHelperConversions() {
        OrderIdGenerator generator = new OrderIdGenerator("ORD-", 41L);

        assertEquals(42L, generator.nextId());

        byte[] byteDest = new byte[40];
        int writtenBytes = generator.nextId(byteDest, 2);
        assertEquals(6, writtenBytes);
        assertEquals("ORD-43", new String(byteDest, 2, writtenBytes, StandardCharsets.US_ASCII));
        assertEquals(' ', byteDest[2 + writtenBytes]);

        char[] charDest = new char[40];
        Arrays.fill(charDest, '_');
        int writtenChars = generator.nextId(charDest, 1);
        assertEquals(6, writtenChars);
        assertEquals("ORD-44", new String(charDest, 1, writtenChars));
        assertEquals(' ', charDest[1 + writtenChars]);

        byte[] longBytes = new byte[16];
        assertEquals(1, OrderIdGenerator.longToBytes(0L, longBytes, 0));
        assertEquals('0', longBytes[0]);
        assertEquals(3, OrderIdGenerator.longToBytes(-12L, longBytes, 0));
        assertEquals("-12", new String(longBytes, 0, 3, StandardCharsets.US_ASCII));

        char[] longChars = new char[16];
        assertEquals(1, OrderIdGenerator.longToChars(0L, longChars, 0));
        assertEquals('0', longChars[0]);
        assertEquals(5, OrderIdGenerator.longToChars(-9876L, longChars, 0));
        assertEquals("-9876", new String(longChars, 0, 5));
    }

    @Test
    void execIdGeneratorDelegatesToExecutionPrefixForAllVariants() {
        ExecIdGenerator generator = new ExecIdGenerator();

        assertEquals(generator.nextId() + 1L, generator.nextId());

        byte[] bytes = new byte[40];
        int byteLen = generator.nextId(bytes, 0);
        assertEquals('E', (char) bytes[0]);
        assertEquals(' ', bytes[byteLen]);

        char[] chars = new char[40];
        int charLen = generator.nextId(chars, 0);
        assertEquals('E', chars[0]);
        assertEquals(' ', chars[charLen]);
    }

    @Test
    void orderStateStoresReadsAndResetsAllFields() {
        OrderState state = new OrderState();
        byte[] clOrdId = "CLIENT-ORDER-1".getBytes(StandardCharsets.US_ASCII);
        byte[] orderId = "SERVER-ORDER-1".getBytes(StandardCharsets.US_ASCII);
        byte[] symbol = "AAPL".getBytes(StandardCharsets.US_ASCII);

        state.setCorrelationId(11L);
        state.setSessionConnectionId(22L);
        state.setClOrdId(clOrdId, 0, clOrdId.length);
        state.setOrderId(orderId, 0, orderId.length);
        state.setSymbol(symbol, 0, symbol.length);
        state.setSide((byte) 1);
        state.setOrderType((byte) 2);
        state.setTimeInForce((byte) 3);
        state.setOrdStatus((byte) 4);
        state.setOrderQty(500_000L);
        state.setPrice(12_340_000_000L);
        state.setCumQty(123_000L);
        state.setLeavesQty(377_000L);
        state.addAvgPxNumerator(5L);
        state.addAvgPxNumerator(7L);
        state.incrementFillCount();
        state.incrementFillCount();
        state.setArrivalTimeNs(999L);

        byte[] readClOrdId = new byte[36];
        byte[] readSymbol = new byte[16];
        state.getClOrdId(readClOrdId, 0);
        state.getSymbol(readSymbol, 0);

        assertEquals(11L, state.getCorrelationId());
        assertEquals(22L, state.getSessionConnectionId());
        assertEquals("CLIENT-ORDER-1", new String(readClOrdId, 0, clOrdId.length, StandardCharsets.US_ASCII));
        assertEquals('S', (char) state.buffer.getByte(OrderState.ORDER_ID_OFFSET));
        assertEquals("AAPL", new String(readSymbol, 0, symbol.length, StandardCharsets.US_ASCII));
        assertEquals(1, state.getSide());
        assertEquals(2, state.getOrderType());
        assertEquals(3, state.buffer.getByte(OrderState.TIF_OFFSET));
        assertEquals(4, state.getOrdStatus());
        assertEquals(500_000L, state.getOrderQty());
        assertEquals(12_340_000_000L, state.getPrice());
        assertEquals(123_000L, state.getCumQty());
        assertEquals(377_000L, state.getLeavesQty());
        assertEquals(12L, state.getAvgPxNumerator());
        assertEquals(2, state.getFillCount());
        assertEquals(999L, state.getArrivalTimeNs());

        state.reset();

        byte[] zeros = new byte[OrderState.BUFFER_SIZE];
        byte[] actual = new byte[OrderState.BUFFER_SIZE];
        state.buffer.getBytes(0, actual);
        assertArrayEquals(zeros, actual);
    }

    @Test
    void orderRepositoryClaimsTracksReleasesAndExhaustsPool() {
        OrderRepository repository = new OrderRepository(1);

        assertEquals(1, repository.pooledCount());
        assertEquals(0, repository.activeCount());

        OrderState claimed = repository.claim(101L);
        assertNotNull(claimed);
        claimed.setCorrelationId(101L);
        claimed.setOrderQty(777L);
        claimed.setClOrdId(ascii36("CL-101"), 0, 36);
        repository.indexClOrdId(101L, ascii36("CL-101"), 36);

        assertSame(claimed, repository.get(101L));
        assertSame(claimed, repository.findByClOrdId(ascii36("CL-101"), 36));
        assertEquals(1, repository.activeCount());
        assertEquals(0, repository.pooledCount());
        assertNull(repository.claim(202L));

        repository.release(101L);
        assertEquals(0, repository.activeCount());
        assertEquals(1, repository.pooledCount());
        assertEquals(0L, claimed.getCorrelationId());
        assertEquals(0L, claimed.getOrderQty());
        assertNull(repository.findByClOrdId(ascii36("CL-101"), 36));

        repository.release(999L);
        assertEquals(1, repository.pooledCount());
    }
}

