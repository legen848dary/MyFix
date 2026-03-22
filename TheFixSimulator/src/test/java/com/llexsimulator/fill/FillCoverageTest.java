package com.llexsimulator.fill;

import com.llexsimulator.disruptor.OrderEvent;
import com.llexsimulator.fill.strategy.FillAtArrivalPriceStrategy;
import com.llexsimulator.fill.strategy.PriceImprovementFillStrategy;
import com.llexsimulator.sbe.FillBehaviorType;
import com.llexsimulator.sbe.OrderSide;
import com.llexsimulator.sbe.RejectReason;
import com.llexsimulator.web.dto.FillProfileDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static com.llexsimulator.testutil.OrderEventFixtures.decodeFillInstruction;
import static com.llexsimulator.testutil.OrderEventFixtures.newLimitOrderEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillCoverageTest {

    @Test
    void fillBehaviorConfigFromDtoMapsFieldsAndDefaultsRejectReason() {
        FillProfileDto dto = new FillProfileDto(
                "custom",
                "desc",
                "RANDOM_FILL",
                6_000,
                3,
                7,
                null,
                25,
                75,
                2,
                9,
                4);

        FillBehaviorConfig config = FillBehaviorConfig.fromDto(dto);

        assertEquals(FillBehaviorType.RANDOM_FILL, config.behaviorType);
        assertEquals(6_000, config.fillPctBps);
        assertEquals(3, config.numPartialFills);
        assertEquals(7_000_000L, config.delayNs);
        assertEquals(RejectReason.SIMULATOR_REJECT, config.rejectReason);
        assertEquals(2_500, config.randomMinQtyPctBps);
        assertEquals(7_500, config.randomMaxQtyPctBps);
        assertEquals(2_000_000L, config.randomMinDelayNs);
        assertEquals(9_000_000L, config.randomMaxDelayNs);
        assertEquals(4, config.priceImprovementBps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fillStrategyFactoryResolvesEveryRegisteredStrategyAndThrowsWhenMissing() throws Exception {
        for (FillBehaviorType type : new FillBehaviorType[]{
                FillBehaviorType.IMMEDIATE_FULL_FILL,
                FillBehaviorType.PARTIAL_FILL,
                FillBehaviorType.DELAYED_FILL,
                FillBehaviorType.REJECT,
                FillBehaviorType.PARTIAL_THEN_CANCEL,
                FillBehaviorType.PRICE_IMPROVEMENT,
                FillBehaviorType.FILL_AT_ARRIVAL_PRICE,
                FillBehaviorType.RANDOM_FILL,
                FillBehaviorType.NO_FILL_IOC_CANCEL,
                FillBehaviorType.valueOf("RANDOM_REJECT_CANCEL")
        }) {
            assertTrue(FillStrategyFactory.getStrategy(type) instanceof FillStrategy);
        }

        Constructor<FillStrategyFactory> ctor = FillStrategyFactory.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        ctor.newInstance();

        Field field = FillStrategyFactory.class.getDeclaredField("STRATEGIES");
        field.setAccessible(true);
        Map<FillBehaviorType, FillStrategy> strategies = (Map<FillBehaviorType, FillStrategy>) field.get(null);
        FillStrategy removed = strategies.remove(FillBehaviorType.REJECT);
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FillStrategyFactory.getStrategy(FillBehaviorType.REJECT));
            assertTrue(ex.getMessage().contains("No strategy registered"));
        } finally {
            strategies.put(FillBehaviorType.REJECT, removed);
        }
    }

    @Test
    void immediatePartialDelayedRejectAndIocStrategiesEncodeExpectedFields() {
        FillBehaviorConfig config = new FillBehaviorConfig();
        config.fillPctBps = 4_500;
        config.numPartialFills = 0;
        config.delayNs = 321L;
        config.rejectReason = RejectReason.INVALID_PRICE;

        assertStrategy(FillBehaviorType.IMMEDIATE_FULL_FILL, config, 10_000, 1, 0L, 10_500_000_000L, RejectReason.SIMULATOR_REJECT);
        assertStrategy(FillBehaviorType.PARTIAL_FILL, config, 4_500, 1, 0L, 10_500_000_000L, RejectReason.SIMULATOR_REJECT);
        assertStrategy(FillBehaviorType.DELAYED_FILL, config, 10_000, 1, 321L, 10_500_000_000L, RejectReason.SIMULATOR_REJECT);
        assertStrategy(FillBehaviorType.REJECT, config, 0, 0, 0L, 0L, RejectReason.INVALID_PRICE);
        assertStrategy(FillBehaviorType.PARTIAL_THEN_CANCEL, config, 4_500, 1, 0L, 10_500_000_000L, RejectReason.SIMULATOR_REJECT);
        assertStrategy(FillBehaviorType.NO_FILL_IOC_CANCEL, config, 0, 0, 0L, 0L, RejectReason.SIMULATOR_REJECT);
    }

    @Test
    void fillAtArrivalPriceStrategyUsesOverrideWhenPresentAndFallsBackOtherwise() {
        FillBehaviorConfig config = new FillBehaviorConfig();
        OrderEvent event = newLimitOrderEvent(1L, 10L, OrderSide.BUY, 10_000L, 9_900_000_000L);
        FillAtArrivalPriceStrategy strategy = new FillAtArrivalPriceStrategy();

        strategy.apply(event, config);
        assertEquals(9_900_000_000L, decodeFillInstruction(event).fillPrice());

        config.fillPriceOverride = 9_800_000_000L;
        strategy.apply(event, config);
        assertEquals(9_800_000_000L, decodeFillInstruction(event).fillPrice());
    }

    @Test
    void priceImprovementStrategyAdjustsBuyAndSellSides() {
        FillBehaviorConfig config = new FillBehaviorConfig();
        config.priceImprovementBps = 25;
        PriceImprovementFillStrategy strategy = new PriceImprovementFillStrategy();

        OrderEvent buyEvent = newLimitOrderEvent(1L, 1L, OrderSide.BUY, 10_000L, 10_000_000_000L);
        strategy.apply(buyEvent, config);
        assertEquals(9_975_000_000L, decodeFillInstruction(buyEvent).fillPrice());

        OrderEvent sellEvent = newLimitOrderEvent(2L, 1L, OrderSide.SELL, 10_000L, 10_000_000_000L);
        strategy.apply(sellEvent, config);
        assertEquals(10_025_000_000L, decodeFillInstruction(sellEvent).fillPrice());
    }

    @Test
    void randomFillStrategySupportsCollapsedAndRangedConfiguration() {
        FillBehaviorConfig collapsed = new FillBehaviorConfig();
        collapsed.behaviorType = FillBehaviorType.RANDOM_FILL;
        collapsed.randomMinQtyPctBps = 7_700;
        collapsed.randomMaxQtyPctBps = 7_700;
        collapsed.randomMinDelayNs = 11L;
        collapsed.randomMaxDelayNs = 11L;

        OrderEvent event = newLimitOrderEvent(5L, 1L, OrderSide.BUY, 10_000L, 10_000_000_000L);
        FillStrategyFactory.getStrategy(FillBehaviorType.RANDOM_FILL).apply(event, collapsed);
        assertEquals(7_700, decodeFillInstruction(event).fillPctBps());
        assertEquals(11L, decodeFillInstruction(event).delayNs());

        FillBehaviorConfig ranged = new FillBehaviorConfig();
        ranged.behaviorType = FillBehaviorType.RANDOM_FILL;
        ranged.randomMinQtyPctBps = 1_000;
        ranged.randomMaxQtyPctBps = 2_000;
        ranged.randomMinDelayNs = 5L;
        ranged.randomMaxDelayNs = 9L;
        FillStrategyFactory.getStrategy(FillBehaviorType.RANDOM_FILL).apply(event, ranged);
        assertTrue(decodeFillInstruction(event).fillPctBps() >= 1_000);
        assertTrue(decodeFillInstruction(event).fillPctBps() <= 2_000);
        assertTrue(decodeFillInstruction(event).delayNs() >= 5L);
        assertTrue(decodeFillInstruction(event).delayNs() <= 9L);
    }

    @Test
    void fillStrategyFactoryReturnsExpectedConcreteStrategies() {
        assertInstanceOf(FillAtArrivalPriceStrategy.class,
                FillStrategyFactory.getStrategy(FillBehaviorType.FILL_AT_ARRIVAL_PRICE));
        assertInstanceOf(PriceImprovementFillStrategy.class,
                FillStrategyFactory.getStrategy(FillBehaviorType.PRICE_IMPROVEMENT));
    }

    private static void assertStrategy(FillBehaviorType type,
                                       FillBehaviorConfig config,
                                       int fillPctBps,
                                       int partialFills,
                                       long delayNs,
                                       long fillPrice,
                                       RejectReason rejectReason) {
        OrderEvent event = newLimitOrderEvent(type.ordinal() + 1L, 77L, OrderSide.BUY, 10_000L, 10_500_000_000L);
        FillStrategyFactory.getStrategy(type).apply(event, config);

        assertEquals(type, decodeFillInstruction(event).fillBehavior());
        assertEquals(fillPctBps, decodeFillInstruction(event).fillPctBps());
        assertEquals(partialFills, decodeFillInstruction(event).numPartialFills());
        assertEquals(delayNs, decodeFillInstruction(event).delayNs());
        assertEquals(fillPrice, decodeFillInstruction(event).fillPrice());
        assertEquals(rejectReason, decodeFillInstruction(event).rejectReasonCode());
    }
}

