package com.llexsimulator.order;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void orderRepositoryClOrdIdIndexRemainsUsableAcrossRepeatedInsertReleaseChurn() {
        OrderRepository repository = new OrderRepository(8);

        for (int i = 0; i < 2_000; i++) {
            long correlationId = i + 1L;
            String clOrdId = "CL-" + correlationId;

            OrderState state = repository.claim(correlationId);
            assertNotNull(state);
            state.setCorrelationId(correlationId);
            state.setClOrdId(ascii36(clOrdId), 0, 36);
            repository.indexClOrdId(correlationId, ascii36(clOrdId), 36);

            assertSame(state, repository.findByClOrdId(ascii36(clOrdId), 36));

            repository.release(correlationId);
            assertNull(repository.findByClOrdId(ascii36(clOrdId), 36));
        }

        OrderState finalState = repository.claim(9_999L);
        assertNotNull(finalState);
        finalState.setCorrelationId(9_999L);
        finalState.setClOrdId(ascii36("FINAL-CL-9999"), 0, 36);
        repository.indexClOrdId(9_999L, ascii36("FINAL-CL-9999"), 36);

        assertSame(finalState, repository.findByClOrdId(ascii36("FINAL-CL-9999"), 36));
    }

    @Test
    void orderRepositoryClOrdIdIndexHandlesBlankIdsDuplicateKeysAndCollisionCompaction() {
        OrderRepository repository = new OrderRepository(4);

        OrderState blankState = repository.claim(1L);
        assertNotNull(blankState);
        repository.indexClOrdId(1L, new byte[36], 36);
        assertNull(repository.findByClOrdId(new byte[36], 36));
        repository.release(1L);

        List<String> collidingIds = findCollidingClOrdIds(2);
        String firstCollision = collidingIds.get(0);
        String secondCollision = collidingIds.get(1);

        OrderState first = repository.claim(101L);
        assertNotNull(first);
        first.setCorrelationId(101L);
        first.setClOrdId(ascii36(firstCollision), 0, 36);
        repository.indexClOrdId(101L, ascii36(firstCollision), 36);

        OrderState second = repository.claim(102L);
        assertNotNull(second);
        second.setCorrelationId(102L);
        second.setClOrdId(ascii36(secondCollision), 0, 36);
        repository.indexClOrdId(102L, ascii36(secondCollision), 36);

        assertSame(second, repository.findByClOrdId(ascii36(secondCollision), 36));

        repository.release(102L);
        assertNull(repository.findByClOrdId(ascii36(secondCollision), 36));

        OrderState reinsertedSecond = repository.claim(103L);
        assertNotNull(reinsertedSecond);
        reinsertedSecond.setCorrelationId(103L);
        reinsertedSecond.setClOrdId(ascii36(secondCollision), 0, 36);
        repository.indexClOrdId(103L, ascii36(secondCollision), 36);

        repository.release(101L);
        assertNull(repository.findByClOrdId(ascii36(firstCollision), 36));
        assertSame(reinsertedSecond, repository.findByClOrdId(ascii36(secondCollision), 36));

        byte[] duplicateKey = ascii36("DUP-KEY-200");
        OrderState originalDuplicate = repository.claim(200L);
        assertNotNull(originalDuplicate);
        originalDuplicate.setCorrelationId(200L);
        originalDuplicate.setClOrdId(duplicateKey, 0, 36);
        repository.indexClOrdId(200L, duplicateKey, 36);

        OrderState replacementDuplicate = repository.claim(201L);
        assertNotNull(replacementDuplicate);
        replacementDuplicate.setCorrelationId(201L);
        replacementDuplicate.setClOrdId(duplicateKey, 0, 36);
        repository.indexClOrdId(201L, duplicateKey, 36);

        assertSame(replacementDuplicate, repository.findByClOrdId(duplicateKey, 36));

        repository.release(200L);
        repository.release(201L);
        repository.release(103L);

        OrderState staleIndexed = repository.claim(300L);
        assertNotNull(staleIndexed);
        staleIndexed.setCorrelationId(300L);
        staleIndexed.setClOrdId(ascii36("STALE-300"), 0, 36);
        repository.indexClOrdId(300L, ascii36("STALE-300"), 36);
        staleIndexed.setClOrdId(ascii36("STALE-301"), 0, 36);
        repository.release(300L);
    }

    @Test
    void orderRepositoryClOrdIdIndexCoversFullFinalSegmentPacking() {
        OrderRepository repository = new OrderRepository(2);
        byte[] fullWidthClOrdId = ascii36("ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");

        OrderState state = repository.claim(501L);
        assertNotNull(state);
        state.setCorrelationId(501L);
        state.setClOrdId(fullWidthClOrdId, 0, 36);
        repository.indexClOrdId(501L, fullWidthClOrdId, 36);

        assertSame(state, repository.findByClOrdId(fullWidthClOrdId, 36));

        repository.release(501L);

        assertNull(repository.findByClOrdId(fullWidthClOrdId, 36));
    }

    @Test
    void clOrdIdIndexCoversNonOccupiedProbeAndNoMoveCompactionBranches() throws Exception {
        Constructor<?> constructor = Class.forName("com.llexsimulator.order.OrderRepository$ClOrdIdIndex")
                .getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        Object index = constructor.newInstance(4);

        byte[] probeKey = ascii36("REFLECT-PROBE-1");
        int probeHomeSlot = homeSlot(probeKey, 15);
        byte[] slotState = (byte[]) getField(index, "slotState");
        slotState[probeHomeSlot] = 2;

        Method getMethod = index.getClass().getDeclaredMethod("get", byte[].class, int.class);
        getMethod.setAccessible(true);
        assertEquals(Long.MIN_VALUE, getMethod.invoke(index, probeKey, 36));

        byte[] compactKey = ascii36("REFLECT-COMPACT-1");
        int compactHomeSlot = homeSlot(compactKey, 15);
        if (compactHomeSlot != 1) {
            compactKey = firstCandidateForHomeSlot(1);
        }

        writeSlot(index, 1, compactKey, 401L);

        Method compactFrom = index.getClass().getDeclaredMethod("compactFrom", int.class);
        compactFrom.setAccessible(true);
        compactFrom.invoke(index, 0);

        slotState = (byte[]) getField(index, "slotState");
        assertEquals(0, slotState[0]);
        assertEquals(1, slotState[1]);
        assertEquals(401L, ((long[]) getField(index, "correlationIds"))[1]);
    }

    private static byte[] firstCandidateForHomeSlot(int targetHomeSlot) {
        for (int i = 0; i < 10_000; i++) {
            byte[] candidate = ascii36("REFLECT-" + i);
            if (homeSlot(candidate, 15) == targetHomeSlot) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find test candidate for target home slot " + targetHomeSlot);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeSlot(Object index, int slot, byte[] clOrdId, long correlationId) throws Exception {
        int effectiveLength = effectiveLength(clOrdId, 36);
        ((byte[]) getField(index, "slotState"))[slot] = 1;
        ((long[]) getField(index, "segment0"))[slot] = packLong(clOrdId, effectiveLength, 0);
        ((long[]) getField(index, "segment1"))[slot] = packLong(clOrdId, effectiveLength, 8);
        ((long[]) getField(index, "segment2"))[slot] = packLong(clOrdId, effectiveLength, 16);
        ((long[]) getField(index, "segment3"))[slot] = packLong(clOrdId, effectiveLength, 24);
        ((int[]) getField(index, "segment4"))[slot] = packInt(clOrdId, effectiveLength, 32);
        ((long[]) getField(index, "correlationIds"))[slot] = correlationId;
    }

    private static List<String> findCollidingClOrdIds(int needed) {
        Map<Integer, List<String>> byHomeSlot = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            String candidate = "COLLIDE-" + i;
            int homeSlot = homeSlot(ascii36(candidate), 15);
            List<String> bucket = byHomeSlot.computeIfAbsent(homeSlot, ignored -> new java.util.ArrayList<>());
            bucket.add(candidate);
            if (bucket.size() >= needed) {
                return bucket;
            }
        }
        throw new IllegalStateException("Unable to find enough colliding ClOrdIds for test coverage");
    }

    private static int homeSlot(byte[] bytes, int mask) {
        int effectiveLength = effectiveLength(bytes, 36);
        long s0 = packLong(bytes, effectiveLength, 0);
        long s1 = packLong(bytes, effectiveLength, 8);
        long s2 = packLong(bytes, effectiveLength, 16);
        long s3 = packLong(bytes, effectiveLength, 24);
        int s4 = packInt(bytes, effectiveLength, 32);
        return mix(s0, s1, s2, s3, s4) & mask;
    }

    private static int effectiveLength(byte[] bytes, int length) {
        int safeLength = Math.max(0, Math.min(length, bytes.length));
        while (safeLength > 0) {
            byte value = bytes[safeLength - 1];
            if (value != 0 && value != ' ') {
                break;
            }
            safeLength--;
        }
        return safeLength;
    }

    private static long packLong(byte[] bytes, int effectiveLength, int segmentOffset) {
        long packed = 0L;
        for (int i = 0; i < 8; i++) {
            packed <<= 8;
            int index = segmentOffset + i;
            if (index < effectiveLength) {
                packed |= bytes[index] & 0xFFL;
            }
        }
        return packed;
    }

    private static int packInt(byte[] bytes, int effectiveLength, int segmentOffset) {
        int packed = 0;
        for (int i = 0; i < 4; i++) {
            packed <<= 8;
            int index = segmentOffset + i;
            if (index < effectiveLength) {
                packed |= bytes[index] & 0xFF;
            }
        }
        return packed;
    }

    private static int mix(long s0, long s1, long s2, long s3, int s4) {
        long hash = 0x9E3779B97F4A7C15L;
        hash ^= s0 + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        hash ^= s1 + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        hash ^= s2 + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        hash ^= s3 + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        hash ^= (s4 & 0xFFFFFFFFL) + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        return (int) (hash ^ (hash >>> 32));
    }
}

