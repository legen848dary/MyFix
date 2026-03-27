package com.insoftu.thefix.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheFixOrderRequestTest {

    @Test
    void bulkVariantKeepsPinnedRoutingFieldsWhileVaryingNosContent() {
        TheFixTagEntry strategyTag = new TheFixTagEntry(9001, "Strategy", "VWAP", true);
        TheFixOrderRequest template = new TheFixOrderRequest(
                "NEW_ORDER_SINGLE",
                "",
                "",
                "BP.L",
                "BUY",
                250,
                101.25d,
                0d,
                "DAY",
                "LIMIT",
                "PER_UNIT",
                "EMEA",
                "XLON",
                "GBP",
                List.of(strategyTag)
        );

        TheFixOrderRequest variantOne = template.bulkVariant(1L);
        TheFixOrderRequest variantTwo = template.bulkVariant(2L);

        assertEquals("EMEA", variantOne.region());
        assertEquals("XLON", variantOne.market());
        assertEquals("GBP", variantOne.currency());
        assertEquals(List.of(strategyTag), variantOne.additionalTags());
        assertEquals(TheFixMessageType.NEW_ORDER_SINGLE, variantOne.messageType());

        assertEquals("EMEA", variantTwo.region());
        assertEquals("XLON", variantTwo.market());
        assertEquals("GBP", variantTwo.currency());
        assertEquals(List.of(strategyTag), variantTwo.additionalTags());

        assertTrue(variantOne.quantity() > 0);
        assertTrue(variantTwo.quantity() > 0);
        assertFalse(variantOne.symbol().isBlank());
        assertFalse(variantTwo.symbol().isBlank());

        assertTrue(
                !variantOne.symbol().equals(variantTwo.symbol())
                        || !variantOne.side().equals(variantTwo.side())
                        || variantOne.quantity() != variantTwo.quantity()
                        || Double.compare(variantOne.price(), variantTwo.price()) != 0
                        || !variantOne.orderType().equals(variantTwo.orderType()),
                "Expected bulk variants generated from different seeds to vary at least one order field."
        );

        assertNotEquals("", template.symbol());
        assertEquals("BP.L", template.symbol());
        assertEquals("BUY", template.side());
        assertEquals(250, template.quantity());
        assertEquals(101.25d, template.price());
    }
}

