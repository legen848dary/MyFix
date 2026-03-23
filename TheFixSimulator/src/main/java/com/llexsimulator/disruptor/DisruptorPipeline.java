package com.llexsimulator.disruptor;

import com.llexsimulator.config.SimulatorConfig;
import com.llexsimulator.disruptor.handler.CompositeOrderEventHandler;
import com.llexsimulator.engine.FixConnection;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wires up the order-processing Disruptor pipeline:
 * <pre>
 *   ValidationHandler → FillStrategyHandler → ExecutionReportHandler → MetricsPublishHandler
 * </pre>
 *
 * <p>A single dedicated platform thread runs all four handlers sequentially on
 * the same CPU core — minimal context switching, maximum cache locality.
 */
public final class DisruptorPipeline {

    private static final Logger log = LoggerFactory.getLogger(DisruptorPipeline.class);

    private final Disruptor<OrderEvent>  disruptor;
    private final RingBuffer<OrderEvent> ringBuffer;
    private final OrderEventTranslator   translator = new OrderEventTranslator();

    public DisruptorPipeline(
            SimulatorConfig config,
            CompositeOrderEventHandler compositeOrderEventHandler
    ) {
        WaitStrategy waitStrategy = "BUSY_SPIN".equalsIgnoreCase(config.waitStrategy())
                ? new BusySpinWaitStrategy()
                : new SleepingWaitStrategy(0, 100_000L);

        AtomicInteger threadCounter = new AtomicInteger(0);
        ThreadFactory tf = runnable -> {
            Thread thread = Thread.ofPlatform()
                    .name("disruptor-handler-" + threadCounter.getAndIncrement())
                    .daemon(true)
                    .unstarted(runnable);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        };

        this.disruptor = new Disruptor<>(
                new OrderEventFactory(),
                config.ringBufferSize(),
                tf,
                ProducerType.SINGLE,
                waitStrategy
        );

        // Single consumer thread — preserve stage ordering without cross-thread handoff.
        disruptor.handleEventsWith(compositeOrderEventHandler);

        this.ringBuffer = disruptor.getRingBuffer();
        log.info("Disruptor pipeline configured: ringBufferSize={} waitStrategy={}",
                config.ringBufferSize(), config.waitStrategy());
    }

    public void start() {
        disruptor.start();
        log.info("Disruptor pipeline started");
    }

    public void shutdown() {
        disruptor.shutdown();
        log.info("Disruptor pipeline stopped");
    }

    public void publish(Object decoder, FixConnection connection, long arrivalNs) {
        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            translator.translateTo(event, sequence, decoder, connection, arrivalNs);
            event.publishCompleteNs = System.nanoTime();
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public long getRemainingCapacity() { return ringBuffer.remainingCapacity(); }
}

