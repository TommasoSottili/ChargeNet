package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Driver;

import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Collections;
import java.sql.PreparedStatement;

public class StationService {

    private final DaoFactory daoFactory;

    public StationService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public ChargingStation registerStation(StationOperator operator, PowerTransformer transformer, String name,
                                String address, double lat, double lng, ConnectorType connectorType,
                                double powerKw, boolean isSolarPowered, BigDecimal tariff) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false); // Inizio transazione

            StationDao stationDao = daoFactory.createStationDao(connection);

            // 1. LOGICA DI DOMINIO: Deleghiamo la validazione e la creazione al Factory Method
            ChargingStation newStation = ChargingStation.register(
                    operator, transformer, name, address, lat, lng,
                    connectorType, powerKw, isSolarPowered, tariff
            );

            // 2. PERSISTENZA: Salviamo la stazione nel database
            stationDao.save(newStation);

            // 3. COMMIT: Confermiamo la transazione
            connection.commit();
            System.out.println("[StationService] Nuova colonnina registrata con successo: " + name + " (Stato: PENDING_VERIFICATION)");

            return newStation;

        } catch (Exception e) {
            rollbackQuietly(connection);
            // Se avete creato un'eccezione custom InvalidStationParametersException, de-commenta la riga sotto:
            if (e instanceof InvalidStationParametersException) {
                throw (InvalidStationParametersException) e;
            }
            throw new RuntimeException("Errore critico durante la registrazione della colonnina: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    public void hold(Driver driver, ChargingStation station) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false); // Inizio transazione

            StationDao stationDao = daoFactory.createStationDao(connection);

            // 1. Chiediamo al DAO di eseguire l'UPDATE atomico.
            // Restituirà true se la colonnina era ACTIVE e l'ha bloccata, false altrimenti.
            boolean success = stationDao.acquireAtomicHold(station.getId(), driver.getId());

            // 2. Se success è false, significa che la colonnina è occupata o in manutenzione
            if (!success) {
                // Lanciamo l'eccezione custom che abbiamo creato prima
                throw new StationNotAvailableException("La colonnina " + station.getName() + " non è più disponibile per la prenotazione.");
            }

            // 3. LOGICA DI DOMINIO: Aggiorniamo l'oggetto Java in memoria.
            // NOTA: Assicurati che il metodo si chiami hold() nella classe ChargingStation.
            station.setReserved(driver);

            // 4. COMMIT: Confermiamo la transazione
            connection.commit();
            System.out.println("[StationService] Hold effettuato con successo sulla colonnina ID: " + station.getId() + " dal driver: " + driver.getEmail());

        } catch (Exception e) {
            rollbackQuietly(connection);
            // Facciamo passare indenne l'eccezione di business per mostrarla nella UI
            if (e instanceof StationNotAvailableException) {
                throw (StationNotAvailableException) e;
            }
            throw new RuntimeException("Errore critico durante l'operazione di hold: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    public void cancelHold(Driver driver, ChargingStation station) {
        // Controllo preventivo (Fail-Fast)
        if (!station.isReservedBy(driver)) {
            throw new IllegalStateException("Errore di sicurezza: Non puoi annullare una prenotazione che non ti appartiene.");
        }

        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);

            StationDao stationDao = daoFactory.createStationDao(connection);

            // LOGICA DI DOMINIO: Sblocca la colonnina in memoria
            station.cancelHold();

            // PERSISTENZA: Salva il nuovo stato ACTIVE nel DB
            stationDao.update(station);

            connection.commit();
            System.out.println("[StationService] Prenotazione annullata con successo sulla colonnina ID: " + station.getId());

        } catch (Exception e) {
            rollbackQuietly(connection);
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new RuntimeException("Errore critico durante l'annullamento della prenotazione: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    public void expireHolds() {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);

            StationDao stationDao = daoFactory.createStationDao(connection);

            // Deleghiamo al metodo massivo che il tuo collega ha già scritto nel DAO
            stationDao.expireHolds();

            connection.commit();
            System.out.println("[StationService] Pulizia prenotazioni scadute eseguita con successo.");

        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Errore durante la scadenza massiva delle prenotazioni: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    public List<ChargingStation> findNearestAvailable(Driver driver) {
        Connection connection = null;
        try {
            // In questo caso non serve disabilitare l'autocommit perché è solo una SELECT (sola lettura)
            connection = DatabaseManager.getConnection();
            StationDao stationDao = daoFactory.createStationDao(connection);

            // Assicurati che i nomi dei getter (getLatitude, getPreferredConnector) corrispondano
            // a quelli reali presenti nella tua classe Driver.
            return stationDao.findNearestAvailable(
                    driver.getLatitude(),
                    driver.getLongitude(),
                    driver.getConnectorType(), // o come si chiama il getter del ConnectorType
                    null // Nessun ID specifico da escludere dalla ricerca
            );

        } catch (Exception e) {
            throw new RuntimeException("Errore durante la ricerca delle colonnine vicine: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ex) { /* Log silenzioso */ }
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