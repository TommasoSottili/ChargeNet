package it.unifi.ing.chargenet.business.strategies;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChargingStrategiesTest {

    private EcoChargingStrategy ecoStrategy;
    private FastChargingStrategy fastStrategy;
    private ChargingStation mockStation;

    @BeforeEach
    void setUp() {
        ecoStrategy = new EcoChargingStrategy();
        fastStrategy = new FastChargingStrategy();

        // Creiamo una stazione proxy leggera (i campi null non influiscono sul test del costo)
        mockStation = ChargingStation.reconstitute(1L, null, null, "Stazione Test", null, 0.0, 0.0, null, 50.0, false, null, null, 0.0, 0, null, null, null);
    }

    // =========================================================================
    // --- TEST STRATEGIA ECO ---
    // =========================================================================

    @Test
    void testEcoStrategyBasics() {
        assertEquals("ECO", ecoStrategy.getName());
        assertEquals(1.0, ecoStrategy.getHeatIncrement(), "L'incremento termico ECO deve essere 1.0");
    }

    @Test
    void testEcoCalculateCostWithoutDriverOrPlan() {
        double kwh = 50.0;
        double expectedCost = kwh * 0.40; // 50 * 0.40 = 20.0

        // Test 1: Con driver nullo
        double costNullDriver = ecoStrategy.calculateCost(kwh, mockStation, null);
        assertEquals(expectedCost, costNullDriver, "Senza driver deve applicare la tariffa base ECO");

        // Test 2: Con driver esistente ma senza piano tariffario (passiamo null come SubscriptionPlan)
        Driver driverNoPlan = new Driver(0.0, 0.0, ConnectorType.TYPE_2, null, 50.0, "Mario", "mario@test.com", "pwd");

        double costNoPlan = ecoStrategy.calculateCost(kwh, mockStation, driverNoPlan);
        assertEquals(expectedCost, costNoPlan, "Senza piano abbonamento deve applicare la tariffa base ECO");
    }

    @Test
    void testEcoCalculateCostWithSubscriptionDiscount() {
        double kwh = 100.0;
        double baseCost = kwh * 0.40; // 40.0 euro

        // 1. Caso Piano BASIC
        Driver basicDriver = new Driver(0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.BASIC, 50.0, "Luigi", "luigi@test.com", "pwd");

        double basicDiscount = SubscriptionPlan.BASIC.getDiscount();
        double expectedBasicCost = Math.round((baseCost * (1 - basicDiscount)) * 100.0) / 100.0;

        double actualBasicCost = ecoStrategy.calculateCost(kwh, mockStation, basicDriver);
        assertEquals(expectedBasicCost, actualBasicCost, "Il piano BASIC deve calcolare il costo corretto rispetto al suo sconto");

        // 2. Caso Piano PREMIUM
        Driver premiumDriver = new Driver(0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.PREMIUM, 50.0, "Anna", "anna@test.com", "pwd");

        double premiumDiscount = SubscriptionPlan.PREMIUM.getDiscount();
        double expectedPremiumCost = Math.round((baseCost * (1 - premiumDiscount)) * 100.0) / 100.0;

        double actualPremiumCost = ecoStrategy.calculateCost(kwh, mockStation, premiumDriver);
        assertEquals(expectedPremiumCost, actualPremiumCost, "Il piano PREMIUM deve applicare correttamente lo sconto nel calcolo finale");
    }

    // =========================================================================
    // --- TEST STRATEGIA FAST ---
    // =========================================================================

    @Test
    void testFastStrategyBasics() {
        assertEquals("FAST", fastStrategy.getName());
        assertEquals(2.0, fastStrategy.getHeatIncrement(), "L'incremento termico FAST deve essere 2.0");
    }

    @Test
    void testFastCalculateCostWithoutDriver() {
        double kwh = 20.0;
        double expectedCost = kwh * 0.65; // 20 * 0.65 = 13.0

        double actualCost = fastStrategy.calculateCost(kwh, mockStation, null);
        assertEquals(expectedCost, actualCost, "Senza driver deve applicare la tariffa base FAST");
    }

    @Test
    void testFastCalculateCostWithPremiumDiscount() {
        double kwh = 50.0;
        double baseCost = kwh * 0.65; // 32.50 euro

        Driver premiumDriver = new Driver(0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.PREMIUM, 50.0, "Anna", "anna@test.com", "pwd");

        double premiumDiscount = SubscriptionPlan.PREMIUM.getDiscount();
        double expectedCost = Math.round((baseCost * (1 - premiumDiscount)) * 100.0) / 100.0;

        double actualCost = fastStrategy.calculateCost(kwh, mockStation, premiumDriver);
        assertEquals(expectedCost, actualCost, "La strategia FAST deve applicare lo sconto dell'abbonamento sulla tariffa da 0.65");
    }
}