package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import java.util.List;
import java.util.Collections;

public class StationService {

    public void registerStation(ChargingStation station) {
    }

    public void hold(Driver driver, ChargingStation station) {
    }

    public void cancelHold(Driver driver, ChargingStation station) {
    }

    public void expireHolds() {
        // Chiamato dal tuo GridMonitor ogni tot secondi
    }

    public List<ChargingStation> findNearestAvailable(Driver driver) {
        return Collections.emptyList();
    }
}