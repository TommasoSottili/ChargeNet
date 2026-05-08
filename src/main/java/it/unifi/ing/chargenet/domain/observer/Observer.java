package it.unifi.ing.chargenet.domain.observer;

public interface Observer {

    void update(Subject source, TransformerEvent event);
}
