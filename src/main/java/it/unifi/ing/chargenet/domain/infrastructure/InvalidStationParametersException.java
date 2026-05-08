package it.unifi.ing.chargenet.domain.infrastructure;

public class InvalidStationParametersException extends RuntimeException{
    public InvalidStationParametersException(String message) {
        super(message);
    }
}
