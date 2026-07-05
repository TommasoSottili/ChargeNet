package it.unifi.ing.chargenet.business.strategies;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;

public class FastChargingStrategy implements ChargingStrategy {

    private static final double HEAT_PER_TICK = 8.0; // Gradi fittizi generati ad ogni tick

    @Override
    public double calculateCost(double kwh, ChargingStation station, Driver driver) {
        // Fast = tariffa piena: prezzo base del dominio, nessuno sconto aggiuntivo.
        return station.priceFor(kwh, driver).doubleValue();
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