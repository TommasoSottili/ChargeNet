package it.unifi.ing.chargenet.domain.feedback;

public class InvalidRatingException extends RuntimeException {
    public InvalidRatingException(String message) {
        super(message);
    }
}
