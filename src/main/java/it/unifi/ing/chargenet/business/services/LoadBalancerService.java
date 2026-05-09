package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.domain.observer.Observer;
import it.unifi.ing.chargenet.domain.observer.Subject;
import it.unifi.ing.chargenet.domain.observer.TransformerEvent;

public class LoadBalancerService implements Observer {

    @Override
    public void update(Subject src, TransformerEvent event) {
        // TODO: Gestire l'evento (handleThermalAlert o handleCoolingComplete)
    }
}