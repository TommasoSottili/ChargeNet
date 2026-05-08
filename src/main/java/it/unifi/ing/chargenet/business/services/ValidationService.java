package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;

public class ValidationService {

    public boolean testStation(ChargingStation station) {
        return false;
    }

    public void approve(ChargingStation station, double platformTariff) {
    }

    public void reject(ChargingStation station, String motivation) {
    }
}