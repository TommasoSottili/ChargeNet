package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.observer.Observer;
import it.unifi.ing.chargenet.domain.observer.Subject;
import it.unifi.ing.chargenet.domain.observer.TransformerEvent;
import it.unifi.ing.chargenet.business.core.GridCluster;

import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class LoadBalancerService implements Observer {

    private final SessionService sessionService;
    private final DatabaseManager dbManager;
    private final DaoFactory daoFactory;

    // Sostituiamo StationDao con l'infrastruttura transazionale
    public LoadBalancerService(SessionService sessionService, DatabaseManager dbManager, DaoFactory daoFactory) {
        this.sessionService = sessionService;
        this.dbManager = dbManager;
        this.daoFactory = daoFactory;
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

    private void handleThermalAlert(PowerTransformer transformer) {
        System.out.println("🚨 [LOAD BALANCER] THERMAL ALERT: Trasformatore " + transformer.getName());
        List<ChargingStation> stations = GridCluster.getInstance().getStationsForTransformer(transformer.getId());

        // FASE 1: Aggiornamento atomico di tutte le colonnine
        Connection connection = null;
        try {
            connection = dbManager.getConnection();
            connection.setAutoCommit(false);
            StationDao stationDao = daoFactory.createStationDao(connection);

            for (ChargingStation station : stations) {
                station.setOverloaded();
                stationDao.update(station);
            }
            connection.commit();
        } catch (Exception e) {
            rollbackQuietly(connection);
            System.err.println("Errore nell'aggiornamento delle colonnine durante l'alert termico!");
        } finally {
            closeQuietly(connection); // Chiudiamo la connessione PRIMA di chiamare il SessionService
        }

        // FASE 2: Chiusura delle sessioni
        // Il sessionService gestirà le sue connessioni in totale autonomia per ogni forceClose!
        List<ChargingSession> activeSessions = sessionService.getActiveSessions();
        for (ChargingSession session : activeSessions) {
            ChargingStation sessionStation = session.getStation();

            // Usiamo l'ID per confrontarle, in modo da evitare problemi di instanze diverse in memoria
            boolean belongsToTransformer = stations.stream()
                    .anyMatch(s -> s.getId().equals(sessionStation.getId()));

            if (belongsToTransformer) {
                sessionService.forceClose(session);
            }
        }
    }

    private void handleCoolingComplete(PowerTransformer transformer) {
        System.out.println("✅ [LOAD BALANCER] COOLING COMPLETE: Trasformatore " + transformer.getName());
        List<ChargingStation> stations = GridCluster.getInstance().getStationsForTransformer(transformer.getId());

        Connection connection = null;
        try {
            connection = dbManager.getConnection();
            connection.setAutoCommit(false);
            StationDao stationDao = daoFactory.createStationDao(connection);

            for (ChargingStation station : stations) {
                if (station.getStatus() == StationStatus.OVERLOADED) {
                    station.setActive();
                    stationDao.update(station);
                }
            }
            connection.commit();
        } catch (Exception e) {
            rollbackQuietly(connection);
            System.err.println("Errore nel ripristino delle colonnine dopo il raffreddamento!");
        } finally {
            closeQuietly(connection);
        }
    }

    // --- Metodi Utility ---
    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try { connection.rollback(); } catch (SQLException ex) { /* log */ }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ex) { /* log */ }
        }
    }
}