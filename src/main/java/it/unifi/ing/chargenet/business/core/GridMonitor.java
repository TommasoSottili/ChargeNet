package it.unifi.ing.chargenet.business.core;

import it.unifi.ing.chargenet.business.strategies.ChargingStrategy;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.business.services.RatingService;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GridMonitor extends Thread {

    // Dipendenze (I Service verranno passati al momento della creazione del Monitor)
    private final StationService stationService;
    private final SessionService sessionService;
    private final RatingService ratingService;

    public GridMonitor(StationService stationService, SessionService sessionService, RatingService ratingService) {
        this.stationService = stationService;
        this.sessionService = sessionService;
        this.ratingService = ratingService;

        // Buona pratica: imposta il thread come Demone.
        // Così se l'utente chiude l'app (JavaFX), questo thread si spegne da solo.
        this.setDaemon(true);
    }

    @Override
    public void run() {
        System.out.println("⚡ GridMonitor avviato. Inizio ciclo vitale...");

        // Loop infinito sicuro: si ferma solo se l'applicazione gli dice di interrompersi
        while (!isInterrupted()) {
            try {
                Thread.sleep(5000);

                onTick();

            } catch (InterruptedException e) {
                System.out.println("GridMonitor interrotto in modo forzato. Chiusura in corso...");
                // Ripristina lo stato di interruzione per terminare il while
                interrupt();
            }
        }
    }

    private void onTick() {
        // 1. Libera le colonnine bloccate da utenti che non si sono presentati
        expireShortTermHolds();

        // 2. Elabora le sessioni e calcola la mappa del calore per ogni trasformatore
        Map<PowerTransformer, Double> heatMap = processActiveSessions();

        // 3. Applica il calore (o fa raffreddare) i trasformatori
        processTransformers(heatMap);

        // 4. Controlla se le colonnine stanno ricevendo recensioni troppo basse
        checkRatingAlerts();
    }

    private void expireShortTermHolds() {
        stationService.expireHolds();
    }

    private Map<PowerTransformer, Double> processActiveSessions() {
        // Mappa che conterrà [Trasformatore -> Somma dei gradi generati in questo Tick]
        Map<PowerTransformer, Double> transformerHeatMap = new HashMap<>();

        // Inizializziamo tutti i trasformatori a 0.0 gradi
        for (PowerTransformer t : GridCluster.getInstance().getTransformers()) {
            transformerHeatMap.put(t, 0.0);
        }

        // Recuperiamo solo le sessioni ATTIVE
        List<ChargingSession> activeSessions = sessionService.getActiveSessions();

        for (ChargingSession session : activeSessions) {
            // Deleghiamo al collega l'aggiornamento dei kwh consumati nel database
            ChargingType strategyString = session.getStrategyUsed();
            ChargingStrategy currentStrategy = ChargingStrategy.fromEnum(strategyString);
            sessionService.addTick(session, currentStrategy);

            // Calcoliamo il calore generato da questa specifica sessione
            if (currentStrategy != null && session.getStation() != null) {
                double addedHeat = currentStrategy.getHeatIncrement();

                // Troviamo il trasformatore associato a questa colonnina
                long transformerId = session.getStation().getTransformer().getId();
                PowerTransformer targetTransformer = findTransformerById(transformerId);

                if (targetTransformer != null) {
                    // Sommiamo il calore a quello già accumulato da altre auto attaccate allo stesso trasformatore
                    double currentHeat = transformerHeatMap.get(targetTransformer);
                    transformerHeatMap.put(targetTransformer, currentHeat + addedHeat);
                }
            }
        }

        return transformerHeatMap;
    }

    private void processTransformers(Map<PowerTransformer, Double> heatMap) {
        // Passiamo il calore ai trasformatori
        for (PowerTransformer t : GridCluster.getInstance().getTransformers()) {
            double totalHeatIncrement = heatMap.getOrDefault(t, 0.0);

            // Il trasformatore aggiorna la sua temperatura e, se serve, notifica l'Observer
            t.simulateTick(totalHeatIncrement);
        }
    }

    private void checkRatingAlerts() {
        // Deleghiamo al service del collega
        ratingService.checkRatingAlerts();
    }

    // --- Metodi di Utilità ---
    private PowerTransformer findTransformerById(long id) {
        for (PowerTransformer t : GridCluster.getInstance().getTransformers()) {
            if (t.getId() == id) {
                return t;
            }
        }
        return null;
    }
}