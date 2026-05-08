package it.unifi.ing.chargenet.domain.sessions;

public class StationNotAvailableException extends RuntimeException {
    public StationNotAvailableException(String message) {
            super(message);
    }
}
