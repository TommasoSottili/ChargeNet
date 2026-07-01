package it.unifi.ing.chargenet.business.strategies;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ChargingStrategiesTest {

    private EcoChargingStrategy ecoStrategy;
    private FastChargingStrategy fastStrategy;
    private ChargingStation mockStation;

    @BeforeEach
    void setUp() {
        ecoStrategy = new EcoChargingStrategy();
        fastStrategy = new FastChargingStrategy();

        mockStation = ChargingStation.reconstitute(1L, null, null, "Stazione Test", null, 0.0, 0.0, null, 50.0, false, null, null, 0.0, 0, null, null, null);
    }

    // Helper: reconstitute() perché qui serve anche poter passare un piano
    // NULL (per il test "senza abbonamento"), cosa che il costruttore di
    // registrazione non permette (forza sempre BASIC).
    private Driver testDriver(long id, String name, String email, SubscriptionPlan plan) {
        return Driver.reconstitute(
                id, name, email, "pwd",
                0.0, 0.0, ConnectorType.TYPE_2, plan, 50.0, BigDecimal.ZERO
        );
    }

    // =========================================================================
    // --- TEST STRATEGIA ECO ---
    // =========================================================================

    @Test
    void testEcoStrategyBasics() {
        assertEquals(ChargingType.ECO, ecoStrategy.getType());
        assertEquals(1.0, ecoStrategy.getHeatIncrement(), "L'incremento termico ECO deve essere 1.0");
    }

    @Test
    void testEcoCalculateCostWithoutDriverOrPlan() {
        double kwh = 50.0;
        double expectedCost = kwh * 0.40;

        double costNullDriver = ecoStrategy.calculateCost(kwh, mockStation, null);
        assertEquals(expectedCost, costNullDriver, "Senza driver deve applicare la tariffa base ECO");

        Driver driverNoPlan = testDriver(1L, "Mario", "mario@test.com", null);

        double costNoPlan = ecoStrategy.calculateCost(kwh, mockStation, driverNoPlan);
        assertEquals(expectedCost, costNoPlan, "Senza piano abbonamento deve applicare la tariffa base ECO");
    }

    @Test
    void testEcoCalculateCostWithSubscriptionDiscount() {
        double kwh = 100.0;
        double baseCost = kwh * 0.40;

        Driver basicDriver = testDriver(2L, "Luigi", "luigi@test.com", SubscriptionPlan.BASIC);

        double basicDiscount = SubscriptionPlan.BASIC.getDiscount();
        double expectedBasicCost = Math.round((baseCost * (1 - basicDiscount)) * 100.0) / 100.0;

        double actualBasicCost = ecoStrategy.calculateCost(kwh, mockStation, basicDriver);
        assertEquals(expectedBasicCost, actualBasicCost, "Il piano BASIC deve calcolare il costo corretto rispetto al suo sconto");

        Driver premiumDriver = testDriver(3L, "Anna", "anna@test.com", SubscriptionPlan.PREMIUM);

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
        assertEquals(ChargingType.FAST, fastStrategy.getType());
        assertEquals(2.0, fastStrategy.getHeatIncrement(), "L'incremento termico FAST deve essere 2.0");
    }

    @Test
    void testFastCalculateCostWithoutDriver() {
        double kwh = 20.0;
        double expectedCost = kwh * 0.65;

        double actualCost = fastStrategy.calculateCost(kwh, mockStation, null);
        assertEquals(expectedCost, actualCost, "Senza driver deve applicare la tariffa base FAST");
    }

    @Test
    void testFastCalculateCostWithPremiumDiscount() {
        double kwh = 50.0;
        double baseCost = kwh * 0.65;

        Driver premiumDriver = testDriver(4L, "Anna", "anna@test.com", SubscriptionPlan.PREMIUM);

        double premiumDiscount = SubscriptionPlan.PREMIUM.getDiscount();
        double expectedCost = Math.round((baseCost * (1 - premiumDiscount)) * 100.0) / 100.0;

        double actualCost = fastStrategy.calculateCost(kwh, mockStation, premiumDriver);
        assertEquals(expectedCost, actualCost, "La strategia FAST deve applicare lo sconto dell'abbonamento sulla tariffa da 0.65");
    }
}