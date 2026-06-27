package it.unifi.ing.chargenet.business.services;

public class StationNotAvailableException extends RuntimeException {

    public StationNotAvailableException(String message) {
        super(message);
    }
}
