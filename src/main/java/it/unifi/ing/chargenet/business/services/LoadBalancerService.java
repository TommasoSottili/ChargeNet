package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.observer.Observer;
import it.unifi.ing.chargenet.domain.observer.Subject;
import it.unifi.ing.chargenet.domain.observer.TransformerEvent;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.business.core.GridCluster;

import java.util.List;

public class LoadBalancerService implements Observer {

    private final SessionService sessionService;
    private final StationDao stationDao; // Aggiunto per soddisfare le nuove specifiche

    public LoadBalancerService(SessionService sessionService, StationDao stationDao) {
        this.sessionService = sessionService;
        this.stationDao = stationDao;
    }

    @Override
    public void update(Subject source, TransformerEvent event) {
        if (!(source instanceof PowerTransformer)) {
            return;
        }

        PowerTransformer transformer = (PowerTransformer) source;

        switch (event) {
            case THERMAL_ALERT:
                handleThermalAlert(transformer);
                break;
            case COOLING_COMPLETE:
                handleCoolingComplete(transformer);
                break;
        }
    }

    /**
     * Recupera le colonnine del trasformatore in fiamme, le blocca in stato OVERLOADED
     * e forza la chiusura di tutte le sessioni attive su di esse delegando al SessionService.
     */
    private void handleThermalAlert(PowerTransformer transformer) {
        System.out.println("🚨 [LOAD BALANCER] THERMAL ALERT: Trasformatore " + transformer.getName());

        // 1. Recupera tutte le colonnine associate a questo trasformatore
        List<ChargingStation> stations = GridCluster.getInstance().getStationsForTransformer(transformer.getId());

        // 2. Imposta le colonnine a OVERLOADED e aggiorna il DB
        for (ChargingStation station : stations) {
            station.setOverloaded();
            stationDao.update(station);
        }

        // 3. Per ogni sessione ACTIVE su quelle colonnine, delega a sessionService.forceClose()
        List<ChargingSession> activeSessions = sessionService.getActiveSessions();
        for (ChargingSession session : activeSessions) {
            ChargingStation sessionStation = session.getStation();

            // Se la colonnina della sessione fa parte di quelle bloccate dal trasformatore
            if (sessionStation != null && stations.contains(sessionStation)) {
                // Il forceClose gestisce in autonomia rimborso, wallet e persistenza Transaction
                sessionService.forceClose(session);
            }
        }
    }

    /**
     * Recupera le colonnine del trasformatore ormai raffreddato e le riapre alla ricarica.
     */
    private void handleCoolingComplete(PowerTransformer transformer) {
        System.out.println("✅ [LOAD BALANCER] COOLING COMPLETE: Trasformatore " + transformer.getName());

        // 1. Recupera le colonnine dello stesso trasformatore
        List<ChargingStation> stations = GridCluster.getInstance().getStationsForTransformer(transformer.getId());

        // 2. Per ciascuna in stato OVERLOADED chiama station.setActive() e aggiorna il DB
        for (ChargingStation station : stations) {
            if (station.getStatus() == StationStatus.OVERLOADED) {
                station.setActive(); // Chiama il metodo specifico dell'entità come da requisiti
                stationDao.update(station);
            }
        }
    }
}