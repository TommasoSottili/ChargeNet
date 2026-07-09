package it.unifi.ing.chargenet.business.strategies;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class ChargingStrategiesTest {

    private EcoChargingStrategy ecoStrategy;
    private FastChargingStrategy fastStrategy;
    private ChargingStation mockStation;

    // Tariffe di test note: operatore 0.30 + piattaforma 0.10 = 0.40 base/kWh
    private static final BigDecimal TARIFF_OP  = new BigDecimal("0.30");
    private static final BigDecimal TARIFF_PLAT = new BigDecimal("0.10");

    @BeforeEach
    void setUp() {
        ecoStrategy = new EcoChargingStrategy();
        fastStrategy = new FastChargingStrategy();

        // Tariffe REALI (non null): priceFor le dereferenzia sempre.
        mockStation = ChargingStation.reconstitute(
                1L, null, null, "Stazione Test", null, 0.0, 0.0, ConnectorType.TYPE_2,
                50.0, false, TARIFF_OP, TARIFF_PLAT, 0.0, 0, StationStatus.ACTIVE, null, null);
    }

    private Driver testDriver(long id, String name, String email, SubscriptionPlan plan) {
        return Driver.reconstitute(
                id, name, email, "pwd",
                0.0, 0.0, ConnectorType.TYPE_2, plan, 50.0, BigDecimal.ZERO
        );
    }

    /** Replica esatta della regola di dominio priceFor: sconto solo sulla quota piattaforma. */
    private double basePrice(double kwh, SubscriptionPlan plan) {
        double discount = (plan != null) ? plan.getDiscount() : 0.0;
        BigDecimal platAfter = TARIFF_PLAT.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(discount)));
        BigDecimal rate = TARIFF_OP.add(platAfter);
        return rate.multiply(BigDecimal.valueOf(kwh)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // =========================================================================
    // --- ECO ---
    // =========================================================================

    @Test
    void testEcoStrategyBasics() {
        assertEquals(ChargingType.ECO, ecoStrategy.getType());
        assertEquals(1.0, ecoStrategy.getHeatIncrement(), "L'incremento termico ECO deve essere 1.0");
    }

    @Test
    void testEcoCalculateCostWithoutDriverOrPlan() {
        double kwh = 50.0;
        // Eco = base (senza sconto piano) × 0.90
        double expected = round2(basePrice(kwh, null) * 0.90);

        double costNullDriver = ecoStrategy.calculateCost(kwh, mockStation, null);
        assertEquals(expected, costNullDriver, 0.001, "Senza driver: tariffa base × sconto Eco 10%");

        Driver driverNoPlan = testDriver(1L, "Mario", "mario@test.com", null);
        double costNoPlan = ecoStrategy.calculateCost(kwh, mockStation, driverNoPlan);
        assertEquals(expected, costNoPlan, 0.001, "Senza piano: come sopra");
    }

    @Test
    void testEcoCalculateCostWithSubscriptionDiscount() {
        double kwh = 100.0;

        Driver basicDriver = testDriver(2L, "Luigi", "luigi@test.com", SubscriptionPlan.BASIC);
        double expectedBasic = round2(basePrice(kwh, SubscriptionPlan.BASIC) * 0.90);
        assertEquals(expectedBasic, ecoStrategy.calculateCost(kwh, mockStation, basicDriver), 0.001,
                "BASIC: base (sconto solo su piattaforma) × Eco 10%");

        Driver premiumDriver = testDriver(3L, "Anna", "anna@test.com", SubscriptionPlan.PREMIUM);
        double expectedPremium = round2(basePrice(kwh, SubscriptionPlan.PREMIUM) * 0.90);
        assertEquals(expectedPremium, ecoStrategy.calculateCost(kwh, mockStation, premiumDriver), 0.001,
                "PREMIUM: sconto piano sulla sola quota piattaforma, poi Eco 10%");
    }

    // =========================================================================
    // --- FAST ---
    // =========================================================================

    @Test
    void testFastStrategyBasics() {
        assertEquals(ChargingType.FAST, fastStrategy.getType());
        assertEquals(2.0, fastStrategy.getHeatIncrement(), "L'incremento termico FAST deve essere 2.0");
    }

    @Test
    void testFastCalculateCostWithoutDriver() {
        double kwh = 20.0;
        // Fast = tariffa piena, nessuno sconto Eco
        double expected = basePrice(kwh, null);
        assertEquals(expected, fastStrategy.calculateCost(kwh, mockStation, null), 0.001,
                "Senza driver: tariffa piena base");
    }

    @Test
    void testFastCalculateCostWithPremiumDiscount() {
        double kwh = 50.0;
        Driver premiumDriver = testDriver(4L, "Anna", "anna@test.com", SubscriptionPlan.PREMIUM);
        double expected = basePrice(kwh, SubscriptionPlan.PREMIUM);
        assertEquals(expected, fastStrategy.calculateCost(kwh, mockStation, premiumDriver), 0.001,
                "PREMIUM: sconto piano sulla quota piattaforma, nessuno sconto Eco");
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}