package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.ClOrdID;
import quickfix.field.ExecType;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.Text;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheFixClientFixServiceMessageClassificationTest {

    @TempDir
    Path tempDir;

    @Test
    void countsRejectedExecutionReportsAsRejectsInsteadOfExecReports() throws Exception {
        try (TheFixClientFixService service = new TheFixClientFixService(testConfig(), new TheFixSessionProfileStore(testConfig()))) {
            service.fromApp(executionReport("WEB-REJECT-1", ExecType.REJECTED, OrdStatus.REJECTED, "Rejected by simulator"),
                    new SessionID("FIX.4.4", "CLIENT", "SIM"));

            assertEquals(0L, service.kpiSnapshot().getLong("executionReports"));
            assertEquals(0L, service.kpiSnapshot().getLong("cancels"));
            assertEquals(1L, service.kpiSnapshot().getLong("rejects"));
        }
    }

    @Test
    void countsNonRejectedExecutionReportsAsExecutionReports() throws Exception {
        try (TheFixClientFixService service = new TheFixClientFixService(testConfig(), new TheFixSessionProfileStore(testConfig()))) {
            service.fromApp(executionReport("WEB-FILL-1", ExecType.FILL, OrdStatus.FILLED, "Filled"),
                    new SessionID("FIX.4.4", "CLIENT", "SIM"));

            assertEquals(1L, service.kpiSnapshot().getLong("executionReports"));
            assertEquals(0L, service.kpiSnapshot().getLong("cancels"));
            assertEquals(0L, service.kpiSnapshot().getLong("rejects"));
        }
    }

    private TheFixClientConfig testConfig() {
        return new TheFixClientConfig(
                "127.0.0.1",
                8081,
                "127.0.0.1",
                9880,
                "FIX.4.4",
                "CLIENT",
                "SIM",
                "FIX.4.4",
                30,
                5,
                25,
                tempDir.resolve("quickfix").toString(),
                false
        );
    }

    private static Message executionReport(String clOrdId, char execType, char ordStatus, String text) {
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, MsgType.EXECUTION_REPORT);
        message.setString(ClOrdID.FIELD, clOrdId);
        message.setString(Symbol.FIELD, "AAPL");
        message.setString(Side.FIELD, Character.toString(Side.BUY));
        message.setDouble(OrderQty.FIELD, 100d);
        message.setDouble(Price.FIELD, 100.25d);
        message.setString(ExecType.FIELD, Character.toString(execType));
        message.setString(OrdStatus.FIELD, Character.toString(ordStatus));
        message.setString(Text.FIELD, text);
        return message;
    }
}
