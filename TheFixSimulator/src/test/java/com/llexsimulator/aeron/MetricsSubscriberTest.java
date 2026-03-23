package com.llexsimulator.aeron;

import com.llexsimulator.metrics.MetricsRegistry;
import com.llexsimulator.sbe.MessageHeaderEncoder;
import com.llexsimulator.sbe.MetricsSnapshotEncoder;
import com.llexsimulator.web.WebSocketBroadcaster;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsSubscriberTest {

    @Test
    void onFragmentBroadcastsMetricsJsonWithNewPercentiles() throws Exception {
        Fixture fixture = new Fixture();
        MetricsRegistry registry = new MetricsRegistry();
        registry.incrementCancels();
        registry.recordLatency(50_000L);
        registry.recordLatency(75_000L);
        registry.recordLatency(90_000L);
        registry.snapshot();

        MetricsSubscriber subscriber = fixture.newSubscriber(registry);
        DirectBuffer buffer = encodedSnapshotBuffer();

        invokeOnFragment(subscriber, buffer);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.broadcaster).broadcast(jsonCaptor.capture());
        String json = jsonCaptor.getValue();

        assertTrue(json.contains("\"type\":\"metrics\""));
        assertTrue(json.contains("\"snapshotTimeNs\":123456789"));
        assertTrue(json.contains("\"ordersReceived\":11"));
        assertTrue(json.contains("\"execReportsSent\":9"));
        assertTrue(json.contains("\"fills\":7"));
        assertTrue(json.contains("\"rejects\":2"));
        assertTrue(json.contains("\"cancels\":1"));
        assertTrue(json.contains("\"p50Us\":" + (registry.getP50Ns() / 1_000L)));
        assertTrue(json.contains("\"p75Us\":" + (registry.getP75Ns() / 1_000L)));
        assertTrue(json.contains("\"p90Us\":" + (registry.getP90Ns() / 1_000L)));
        assertTrue(json.contains("\"throughputPerSec\":1234"));
        assertFalse(json.contains("\"p80Us\""));
        assertFalse(json.contains("\"p99Us\""));
    }

    @Test
    void runPollsUntilStoppedAfterReceivingFragments() {
        Fixture fixture = new Fixture();
        MetricsSubscriber[] holder = new MetricsSubscriber[1];
        when(fixture.subscription.poll(any(), eq(10))).thenAnswer(invocation -> {
            holder[0].stop();
            return 1;
        });

        holder[0] = fixture.newSubscriber(new MetricsRegistry());
        holder[0].run();

        verify(fixture.subscription).poll(any(), eq(10));
    }

    @Test
    void runSpinsWhenPollReturnsZeroAndThenStops() {
        Fixture fixture = new Fixture();
        MetricsSubscriber[] holder = new MetricsSubscriber[1];
        when(fixture.subscription.poll(any(), eq(10))).thenAnswer(invocation -> {
            holder[0].stop();
            return 0;
        });

        holder[0] = fixture.newSubscriber(new MetricsRegistry());
        holder[0].run();

        verify(fixture.subscription).poll(any(), eq(10));
    }

    private static DirectBuffer encodedSnapshotBuffer() {
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(
                MessageHeaderEncoder.ENCODED_LENGTH + MetricsSnapshotEncoder.BLOCK_LENGTH));
        new MetricsSnapshotEncoder()
                .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .snapshotTimeNs(123_456_789L)
                .ordersReceivedCount(11L)
                .executionReportsSent(9L)
                .fillsCount(7L)
                .rejectsCount(2L)
                .p50LatencyNs(50_000L)
                .p99LatencyNs(99_000L)
                .p999LatencyNs(999_000L)
                .maxLatencyNs(1_000_000L)
                .throughputPerSec(1_234L);
        return buffer;
    }

    private static void invokeOnFragment(MetricsSubscriber subscriber, DirectBuffer buffer) throws Exception {
        Method onFragment = MetricsSubscriber.class.getDeclaredMethod(
                "onFragment", DirectBuffer.class, int.class, int.class, Header.class);
        onFragment.setAccessible(true);
        onFragment.invoke(subscriber, buffer, 0, buffer.capacity(), mock(Header.class));
    }

    private static final class Fixture {
        private final AeronContext ctx = mock(AeronContext.class);
        private final Aeron aeron = mock(Aeron.class);
        private final Subscription subscription = mock(Subscription.class);
        private final WebSocketBroadcaster broadcaster = mock(WebSocketBroadcaster.class);
        private final Vertx vertx = mock(Vertx.class);

        private Fixture() {
            when(ctx.getAeron()).thenReturn(aeron);
            when(aeron.addSubscription("aeron:ipc", 1002)).thenReturn(subscription);
            doAnswer(invocation -> {
                Handler<Void> handler = invocation.getArgument(0);
                handler.handle(null);
                return null;
            }).when(vertx).runOnContext(any());
        }

        private MetricsSubscriber newSubscriber(MetricsRegistry registry) {
            return new MetricsSubscriber(ctx, broadcaster, vertx, registry, "aeron:ipc");
        }
    }
}
