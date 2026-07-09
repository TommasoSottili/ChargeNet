package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;

import java.util.List;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;

public class ValidationService {

    private final DaoFactory daoFactory;

    public ValidationService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public boolean testStation(ChargingStation station) {
        // 1. Controllo di coerenza tecnica:
        // La potenza dichiarata non può superare il limite fisico del connettore.
        // NOTA: controlla che la tua classe ConnectorType abbia il metodo getMaxPowerKw()
        if (station.getPowerKw() > station.getConnectorType().getMaxPowerKw()) {
            System.err.println("[ValidationService] Test fallito: Potenza eccessiva per il tipo di connettore.");
            return false;
        }

        // 2. Simulazione test di connettività (ping verso la colonnina fisica fittizia)
        boolean pingSuccess = simulateConnectivityTest(station.getLatitude(), station.getLongitude());
        if (!pingSuccess) {
            System.err.println("[ValidationService] Test fallito: Impossibile stabilire la connessione con la colonnina.");
            return false;
        }

        System.out.println("[ValidationService] Test superato con successo per la colonnina: " + station.getName());
        return true;
    }

    public void approve(ChargingStation station, BigDecimal platformTariff) {
        // Controllo preventivo: la tariffa non può essere negativa o nulla
        if (platformTariff == null || platformTariff.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La tariffa della piattaforma deve essere un valore valido e non negativo.");
        }

        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false); // Inizio transazione

            StationDao stationDao = daoFactory.createStationDao(connection);

            // 1. LOGICA DI DOMINIO: Cambia stato in ACTIVE e imposta la tariffa
            station.activate(platformTariff);

            // 2. PERSISTENZA: Aggiorna la riga nel database
            stationDao.update(station);

            // 3. COMMIT: Conferma definitiva
            connection.commit();
            System.out.println("[ValidationService] Colonnina '" + station.getName() + "' approvata e attivata con successo.");

        } catch (Exception e) {
            rollbackQuietly(connection);
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new RuntimeException("Errore critico durante l'approvazione della colonnina: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    public void reject(ChargingStation station, String motivation) {
        if (motivation == null || motivation.trim().isEmpty()) {
            throw new IllegalArgumentException("È obbligatorio fornire una motivazione per il rifiuto.");
        }

        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false); // Inizio transazione

            StationDao stationDao = daoFactory.createStationDao(connection);

            // 1. LOGICA DI DOMINIO: Cambia stato in REJECTED
            station.reject();

            // 2. PERSISTENZA: Aggiorna la riga nel database
            stationDao.update(station);

            // 3. COMMIT
            connection.commit();
            System.out.println("[ValidationService] Colonnina '" + station.getName() + "' rifiutata. Motivazione: " + motivation);

        } catch (Exception e) {
            rollbackQuietly(connection);
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new RuntimeException("Errore critico durante il rifiuto della colonnina: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    protected boolean simulateConnectivityTest(double lat, double lng) {
        // Simulazione: Diciamo che il ping fallisce casualmente 1 volta su 10 (10% di failure rate)
        // per simulare problemi di rete reali durante i test dell'Energy Manager.
        return Math.random() > 0.10;
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try { connection.rollback(); } catch (SQLException ex) { /* Log silenzioso */ }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                connection.close();
            } catch (SQLException ex) { /* Log silenzioso */ }
        }
    }
}