package it.unifi.ing.chargenet.business.exceptions;

public class NoTransformerAvailableException extends RuntimeException {
    public NoTransformerAvailableException(String message) {
        super(message);
    }
}