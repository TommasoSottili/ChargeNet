package it.unifi.ing.chargenet.domain.infrastructure;

import it.unifi.ing.chargenet.domain.observer.Observer;
import it.unifi.ing.chargenet.domain.observer.Subject;
import it.unifi.ing.chargenet.domain.observer.TransformerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PowerTransformer implements Subject {

    private Long id;
    private String name;
    private double temperature;
    private double loadPercent;
    // Definiamo il "100%" del nostro trasformatore
    // Se riceve 10.0 di calore in un colpo solo, sta lavorando al 100% delle sue capacità.
    private static final double MAX_HEAT_PER_TICK = 10.0;
    private final List<Observer> observers;

    protected PowerTransformer() {
        this.observers = new ArrayList<>();
    }

    public PowerTransformer(String name) {
        this.name = name;
        this.temperature = 25.0;
        this.loadPercent = 0.0;
        this.observers = new ArrayList<>();
    }

    public void simulateTick(double totalHeatIncrement) {
        double previousTemperature = this.temperature;

        if (totalHeatIncrement > 0.0) {

            this.temperature += totalHeatIncrement;

            // Calcolo proporzionale del carico percentuale
            // (Calore Attuale / Calore Massimo) * 100
            double calculatedLoad = (totalHeatIncrement / MAX_HEAT_PER_TICK) * 100.0;

            // Salvaguardia: Il carico visivo non deve superare il 100%
            // (utile se domani la UI deve disegnare una barra di progresso)
            this.loadPercent = Math.min(100.0, calculatedLoad);

        } else {
            // Nessuna sessione attiva: il trasformatore si raffredda di 2 gradi a tick
            this.temperature = Math.max(25.0, this.temperature - 2.0);
            this.loadPercent = 0.0;
        }

        if (this.temperature > 90.0) {
            notifyObservers(TransformerEvent.THERMAL_ALERT);
        }

        if (previousTemperature > 70.0 && this.temperature <= 70.0) {
            notifyObservers(TransformerEvent.COOLING_COMPLETE);
        }
    }

    public void attach(Observer observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void detach(Observer observer) {
        observers.remove(observer);
    }

    public void notifyObservers(TransformerEvent event) {
        for (Observer observer : observers) {
            observer.update(this, event);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getLoadPercent() {
        return loadPercent;
    }
}
