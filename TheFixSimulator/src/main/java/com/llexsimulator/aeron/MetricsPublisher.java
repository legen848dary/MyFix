/*
 * Copyright (C) 2026 Debjyoti SARKAR
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.llexsimulator.aeron;

import com.llexsimulator.sbe.MessageHeaderEncoder;
import com.llexsimulator.sbe.MetricsSnapshotEncoder;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Publishes {@code MetricsSnapshot} messages via Aeron IPC to the Vert.x
 * {@link MetricsSubscriber}. Publishing is best-effort: back-pressure and
 * disconnected-subscriber cases are silently discarded.
 *
 * <p>This class is called from the Disruptor handler thread (stage 4).
 * All operations are zero-GC: the SBE encoder and UnsafeBuffer are
 * pre-allocated fields.
 */
public final class MetricsPublisher {

    private static final Logger log  = LoggerFactory.getLogger(MetricsPublisher.class);
    private static final int    STREAM_ID = 1002;

    private final Publication           publication;
    private final UnsafeBuffer          buffer;
    private final MetricsSnapshotEncoder encoder;
    private final MessageHeaderEncoder  headerEncoder;
    public MetricsPublisher(AeronContext ctx, String channel) {
        this.publication   = ctx.getAeron().addPublication(channel, STREAM_ID);
        int bufLen = MessageHeaderEncoder.ENCODED_LENGTH + MetricsSnapshotEncoder.BLOCK_LENGTH;
        this.buffer        = new UnsafeBuffer(ByteBuffer.allocateDirect(bufLen));
        this.encoder       = new MetricsSnapshotEncoder();
        this.headerEncoder = new MessageHeaderEncoder();
        log.info("MetricsPublisher ready on {} stream {}", channel, STREAM_ID);
    }

    public void publish(long snapshotTimeNs, long orders, long execReports,
                        long fills, long rejects,
                        long p50, long p99, long p999, long max, long tps) {
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
               .snapshotTimeNs(snapshotTimeNs)
               .ordersReceivedCount(orders)
               .executionReportsSent(execReports)
               .fillsCount(fills)
               .rejectsCount(rejects)
               .p50LatencyNs(p50)
               .p99LatencyNs(p99)
               .p999LatencyNs(p999)
               .maxLatencyNs(max)
               .throughputPerSec(tps);

        int encodedLen = MessageHeaderEncoder.ENCODED_LENGTH + MetricsSnapshotEncoder.BLOCK_LENGTH;
        long result = publication.offer(buffer, 0, encodedLen);
        if (result < 0 && result != Publication.BACK_PRESSURED && result != Publication.NOT_CONNECTED) {
            log.warn("Aeron metrics publish failed: result={}", result);
        }
    }

    public void close() {
        publication.close();
    }
}
