package it.unifi.ing.chargenet.domain.observer;

public interface Subject {
    void attach(Observer observer);
    void detach(Observer observer);
    void notifyObservers(TransformerEvent event);
}
