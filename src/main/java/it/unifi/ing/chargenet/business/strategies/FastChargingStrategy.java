package it.unifi.ing.chargenet.business.strategies;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;

public class FastChargingStrategy implements ChargingStrategy {

    private static final double BASE_RATE_PER_KWH = 0.65;
    private static final double HEAT_PER_TICK = 2.0; // Gradi fittizi generati ad ogni tick

    @Override
    public double calculateCost(double kwh, ChargingStation station, Driver driver) {
        double baseCost = kwh * BASE_RATE_PER_KWH;

        // Se non c'è il driver o non ha un piano, paga il prezzo base
        if (driver == null || driver.getSubscriptionPlan() == null) {
            return Math.round(baseCost * 100.0) / 100.0;
        }

        // Prende lo sconto (es. 0.10 per Premium, 0.05 per Plus, 0.0 per Basic)
        double discount = driver.getSubscriptionPlan().getDiscount();
        double finalCost = baseCost * (1 - discount);

        return Math.round(finalCost * 100.0) / 100.0;
    }

    @Override
    public double getHeatIncrement() {
        return HEAT_PER_TICK;
    }

    @Override
    public ChargingType getType() {
        return ChargingType.FAST;
    }
}