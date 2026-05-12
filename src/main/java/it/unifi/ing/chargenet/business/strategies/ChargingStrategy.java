package it.unifi.ing.chargenet.business.strategies;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;

public interface ChargingStrategy {
    // Calcola il costo in base ai kWh consumati
    double calculateCost(double kwh, ChargingStation station, Driver driver);

    // Per il PowerTransformer (Opzione 2 con i kW che abbiamo scelto)
    double getHeatIncrement(); //Nome del metodo modificato

    // Ritorna il nome della strategia (es. "FAST", "ECO")
    String getName();

    static ChargingStrategy fromString(String strategyName) {
        if ("FAST".equalsIgnoreCase(strategyName)) {
            return new FastChargingStrategy();
        }
        return new EcoChargingStrategy();
    }
}