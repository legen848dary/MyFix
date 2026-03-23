package com.llexsimulator.order;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pool-backed repository of {@link OrderState} objects.
 *
 * <p>All instances are pre-allocated at startup. No allocation occurs at
 * runtime: {@link #claim(long)} takes from the pool, {@link #release(long)}
 * returns to it. If the pool is exhausted (pool-depth misconfiguration), an
 * error is logged and {@code null} is returned — the caller must handle this.
 *
 * <p>The internal {@link Long2ObjectHashMap} uses open-addressing with no
 * boxing — it is Agrona's zero-GC collection.
 */
public final class OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRepository.class);

    private final Deque<OrderState>            pool;
    private final Long2ObjectHashMap<OrderState> active;
    private final ClOrdIdIndex                 clOrdIdIndex;

    public OrderRepository(int poolSize) {
        this.pool   = new ArrayDeque<>(poolSize);
        this.active = new Long2ObjectHashMap<>(poolSize * 2, 0.5f);
        this.clOrdIdIndex = new ClOrdIdIndex(poolSize);
        for (int i = 0; i < poolSize; i++) pool.offer(new OrderState());
        log.info("OrderRepository initialised with {} pre-allocated slots", poolSize);
    }

    /**
     * Claims a pooled {@link OrderState} and associates it with the given
     * {@code correlationId}. Returns {@code null} if the pool is empty.
     */
    public OrderState claim(long correlationId) {
        OrderState state = pool.poll();
        if (state == null) {
            log.error("Order pool exhausted — increase order.pool.size (correlationId={}, activeCount={}, pooledCount={})",
                    correlationId, active.size(), pool.size());
            return null;
        }
        active.put(correlationId, state);
        return state;
    }

    public OrderState get(long correlationId) {
        return active.get(correlationId);
    }

    public void indexClOrdId(long correlationId, byte[] clOrdIdBytes, int length) {
        clOrdIdIndex.put(clOrdIdBytes, length, correlationId);
    }

    public OrderState findByClOrdId(byte[] clOrdIdBytes, int length) {
        long correlationId = clOrdIdIndex.get(clOrdIdBytes, length);
        return correlationId == ClOrdIdIndex.MISSING ? null : active.get(correlationId);
    }

    /** Returns the {@link OrderState} to the pool and zeroes its buffer. */
    public void release(long correlationId) {
        OrderState state = active.remove(correlationId);
        if (state != null) {
            clOrdIdIndex.remove(state, correlationId);
            state.reset();
            pool.offer(state);
        }
    }

    public int activeCount() { return active.size(); }
    public int pooledCount()  { return pool.size(); }

    private static final class ClOrdIdIndex {

        private static final byte EMPTY = 0;
        private static final byte OCCUPIED = 1;
        private static final long MISSING = Long.MIN_VALUE;
        private static final int CL_ORD_ID_LENGTH = 36;

        private final byte[] slotState;
        private final long[] segment0;
        private final long[] segment1;
        private final long[] segment2;
        private final long[] segment3;
        private final int[]  segment4;
        private final long[] correlationIds;
        private final int    mask;

        private ClOrdIdIndex(int expectedEntries) {
            int capacity = 1;
            int targetCapacity = Math.max(16, expectedEntries * 4);
            while (capacity < targetCapacity) {
                capacity <<= 1;
            }
            this.slotState = new byte[capacity];
            this.segment0 = new long[capacity];
            this.segment1 = new long[capacity];
            this.segment2 = new long[capacity];
            this.segment3 = new long[capacity];
            this.segment4 = new int[capacity];
            this.correlationIds = new long[capacity];
            this.mask = capacity - 1;
        }

        private void put(byte[] clOrdIdBytes, int length, long correlationId) {
            int effectiveLength = effectiveLength(clOrdIdBytes, length);
            if (effectiveLength == 0) {
                return;
            }

            long s0 = packLong(clOrdIdBytes, effectiveLength, 0);
            long s1 = packLong(clOrdIdBytes, effectiveLength, 8);
            long s2 = packLong(clOrdIdBytes, effectiveLength, 16);
            long s3 = packLong(clOrdIdBytes, effectiveLength, 24);
            int s4 = packInt(clOrdIdBytes, effectiveLength, 32);

            int slot = mix(s0, s1, s2, s3, s4) & mask;
            while (true) {
                byte state = slotState[slot];
                if (state == EMPTY) {
                    write(slot, s0, s1, s2, s3, s4, correlationId);
                    return;
                }
                if (matches(slot, s0, s1, s2, s3, s4)) {
                    correlationIds[slot] = correlationId;
                    return;
                }
                slot = (slot + 1) & mask;
            }
        }

        private long get(byte[] clOrdIdBytes, int length) {
            int effectiveLength = effectiveLength(clOrdIdBytes, length);
            if (effectiveLength == 0) {
                return MISSING;
            }

            long s0 = packLong(clOrdIdBytes, effectiveLength, 0);
            long s1 = packLong(clOrdIdBytes, effectiveLength, 8);
            long s2 = packLong(clOrdIdBytes, effectiveLength, 16);
            long s3 = packLong(clOrdIdBytes, effectiveLength, 24);
            int s4 = packInt(clOrdIdBytes, effectiveLength, 32);

            int slot = mix(s0, s1, s2, s3, s4) & mask;
            while (true) {
                byte state = slotState[slot];
                if (state == EMPTY) {
                    return MISSING;
                }
                if (state == OCCUPIED && matches(slot, s0, s1, s2, s3, s4)) {
                    return correlationIds[slot];
                }
                slot = (slot + 1) & mask;
            }
        }

        private void remove(OrderState state, long correlationId) {
            UnsafeBuffer buffer = state.buffer;
            int effectiveLength = effectiveLength(buffer, OrderState.CL_ORD_ID_OFFSET, CL_ORD_ID_LENGTH);
            if (effectiveLength == 0) {
                return;
            }

            long s0 = packLong(buffer, OrderState.CL_ORD_ID_OFFSET, effectiveLength, 0);
            long s1 = packLong(buffer, OrderState.CL_ORD_ID_OFFSET, effectiveLength, 8);
            long s2 = packLong(buffer, OrderState.CL_ORD_ID_OFFSET, effectiveLength, 16);
            long s3 = packLong(buffer, OrderState.CL_ORD_ID_OFFSET, effectiveLength, 24);
            int s4 = packInt(buffer, OrderState.CL_ORD_ID_OFFSET, effectiveLength, 32);

            int slot = mix(s0, s1, s2, s3, s4) & mask;
            while (true) {
                byte entryState = slotState[slot];
                if (entryState == EMPTY) {
                    return;
                }
                if (correlationIds[slot] == correlationId && matches(slot, s0, s1, s2, s3, s4)) {
                    compactFrom(slot);
                    return;
                }
                slot = (slot + 1) & mask;
            }
        }

        private void compactFrom(int deletedSlot) {
            int hole = deletedSlot;
            int slot = (deletedSlot + 1) & mask;

            while (slotState[slot] != EMPTY) {
                int home = mix(segment0[slot], segment1[slot], segment2[slot], segment3[slot], segment4[slot]) & mask;
                int distanceToSlot = (slot - home) & mask;
                int distanceToHole = (hole - home) & mask;

                if (distanceToHole <= distanceToSlot) {
                    move(slot, hole);
                    hole = slot;
                }

                slot = (slot + 1) & mask;
            }

            clear(hole);
        }

        private void write(int slot, long s0, long s1, long s2, long s3, int s4, long correlationId) {
            slotState[slot] = OCCUPIED;
            segment0[slot] = s0;
            segment1[slot] = s1;
            segment2[slot] = s2;
            segment3[slot] = s3;
            segment4[slot] = s4;
            correlationIds[slot] = correlationId;
        }

        private void move(int fromSlot, int toSlot) {
            slotState[toSlot] = OCCUPIED;
            segment0[toSlot] = segment0[fromSlot];
            segment1[toSlot] = segment1[fromSlot];
            segment2[toSlot] = segment2[fromSlot];
            segment3[toSlot] = segment3[fromSlot];
            segment4[toSlot] = segment4[fromSlot];
            correlationIds[toSlot] = correlationIds[fromSlot];
        }

        private void clear(int slot) {
            slotState[slot] = EMPTY;
            correlationIds[slot] = MISSING;
            segment0[slot] = 0L;
            segment1[slot] = 0L;
            segment2[slot] = 0L;
            segment3[slot] = 0L;
            segment4[slot] = 0;
        }

        private boolean matches(int slot, long s0, long s1, long s2, long s3, int s4) {
            return segment0[slot] == s0
                    && segment1[slot] == s1
                    && segment2[slot] == s2
                    && segment3[slot] == s3
                    && segment4[slot] == s4;
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

        private static int effectiveLength(UnsafeBuffer buffer, int offset, int length) {
            int safeLength = length;
            while (safeLength > 0) {
                byte value = buffer.getByte(offset + safeLength - 1);
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

        private static long packLong(UnsafeBuffer buffer, int baseOffset, int effectiveLength, int segmentOffset) {
            long packed = 0L;
            for (int i = 0; i < 8; i++) {
                packed <<= 8;
                int index = segmentOffset + i;
                if (index < effectiveLength) {
                    packed |= buffer.getByte(baseOffset + index) & 0xFFL;
                }
            }
            return packed;
        }

        private static int packInt(UnsafeBuffer buffer, int baseOffset, int effectiveLength, int segmentOffset) {
            int packed = 0;
            for (int i = 0; i < 4; i++) {
                packed <<= 8;
                int index = segmentOffset + i;
                if (index < effectiveLength) {
                    packed |= buffer.getByte(baseOffset + index) & 0xFF;
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
}

