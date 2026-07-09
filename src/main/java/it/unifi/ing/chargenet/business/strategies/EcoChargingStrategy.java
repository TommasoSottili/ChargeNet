package it.unifi.ing.chargenet.business.strategies;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import java.math.BigDecimal;

public class EcoChargingStrategy implements ChargingStrategy {

    private static final double HEAT_PER_TICK = 4.0;

    @Override
    public double calculateCost(double kwh, ChargingStation station, Driver driver) {
        // Prezzo base dal dominio (tariffa + sconto piano sulla quota piattaforma),
        // poi Eco applica il suo 10% extra di sconto.
        BigDecimal base = station.priceFor(kwh, driver);
        BigDecimal ecoDiscounted = base.multiply(BigDecimal.valueOf(0.90));
        return ecoDiscounted.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public double getHeatIncrement() {
        return HEAT_PER_TICK;
    }

    @Override
    public ChargingType getType() {
        return ChargingType.ECO;
    }
}