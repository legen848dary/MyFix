package com.llexsimulator.order;

import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, Long> clOrdIdIndex;

    public OrderRepository(int poolSize) {
        this.pool   = new ArrayDeque<>(poolSize);
        this.active = new Long2ObjectHashMap<>(poolSize * 2, 0.5f);
        this.clOrdIdIndex = new HashMap<>(poolSize * 2);
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
        String key = clOrdIdKey(clOrdIdBytes, length);
        if (!key.isBlank()) {
            clOrdIdIndex.put(key, correlationId);
        }
    }

    public OrderState findByClOrdId(byte[] clOrdIdBytes, int length) {
        Long correlationId = clOrdIdIndex.get(clOrdIdKey(clOrdIdBytes, length));
        return correlationId == null ? null : active.get(correlationId);
    }

    /** Returns the {@link OrderState} to the pool and zeroes its buffer. */
    public void release(long correlationId) {
        OrderState state = active.remove(correlationId);
        if (state != null) {
            byte[] clOrdId = new byte[36];
            state.getClOrdId(clOrdId, 0);
            String key = clOrdIdKey(clOrdId, clOrdId.length);
            Long mappedCorrelationId = clOrdIdIndex.get(key);
            if (mappedCorrelationId != null && mappedCorrelationId == correlationId) {
                clOrdIdIndex.remove(key);
            }
            state.reset();
            pool.offer(state);
        }
    }

    public int activeCount() { return active.size(); }
    public int pooledCount()  { return pool.size(); }

    private static String clOrdIdKey(byte[] clOrdIdBytes, int length) {
        int safeLength = Math.max(0, Math.min(length, clOrdIdBytes.length));
        while (safeLength > 0) {
            byte value = clOrdIdBytes[safeLength - 1];
            if (value != 0 && value != ' ') {
                break;
            }
            safeLength--;
        }
        return new String(clOrdIdBytes, 0, safeLength, StandardCharsets.US_ASCII);
    }
}

