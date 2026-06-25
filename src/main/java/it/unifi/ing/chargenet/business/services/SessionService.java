package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.business.strategies.ChargingStrategy;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.financials.TransactionType;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;

// IMPORTIAMO SOLO LE INTERFACCE! (Purezza Architetturale)
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.SessionDao;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.interfaces.TransactionDao;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class SessionService {

    private final DatabaseManager dbManager;
    private final DaoFactory daoFactory;

    public SessionService(DatabaseManager dbManager, DaoFactory daoFactory) {
        this.dbManager = dbManager;
        this.daoFactory = daoFactory;
    }

    /**
     * Apre una nuova sessione verificando i fondi del guidatore.
     */
    public ChargingSession openSession(Driver driver, ChargingStation station, ChargingStrategy strategy, double batteryStart) {
        // 1. Regola di Business: verifica saldo per almeno 5 kWh
        double minRequiredCost = strategy.calculateCost(5.0, station, driver);
        if (driver.getWalletBalance().compareTo(BigDecimal.valueOf(minRequiredCost)) < 0) {
            throw new IllegalStateException("Saldo insufficiente: sono necessari fondi per almeno 5 kWh.");
        }

        // 2. Factory Method per Sessione
        ChargingSession session = ChargingSession.open(driver, station, strategy.getName(), batteryStart);
        station.setBusy();

        // 3. Persistenza Atomica
        Connection connection = null;
        try {
            connection = dbManager.getConnection();
            connection.setAutoCommit(false); // INIZIO TRANSAZIONE

            SessionDao sessionDao = daoFactory.createSessionDao(connection);
            StationDao stationDao = daoFactory.createStationDao(connection);

            sessionDao.save(session);
            stationDao.update(station);

            connection.commit(); // FINE TRANSAZIONE
            return session;

        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Impossibile aprire la sessione a causa di un errore nel database.", e);
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Chiamato dal GridMonitor ogni 5 secondi per far avanzare la ricarica ed erodere il portafoglio.
     */
    public void addTick(ChargingSession session, ChargingStrategy strategy) {
        // 1. Calcolo fisico ed economico
        double oreTick = 5.0 / 3600.0;
        double kwhThisTick = session.getStation().getPowerKw() * oreTick;
        double costDouble = strategy.calculateCost(kwhThisTick, session.getStation(), session.getDriver());
        BigDecimal costThisTick = BigDecimal.valueOf(costDouble);

        session.addTick(kwhThisTick, costThisTick);

        Driver driver = session.getDriver();
        driver.charge(costThisTick);

        // 2. Salvataggio del tick nel Database
        Connection connection = null;
        try {
            connection = dbManager.getConnection();
            connection.setAutoCommit(false);

            UserDao userDao = daoFactory.createUserDao(connection);
            SessionDao sessionDao = daoFactory.createSessionDao(connection);

            userDao.update(driver);
            sessionDao.update(session);

            connection.commit();
        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Errore durante il salvataggio del tick di ricarica", e);
        } finally {
            // È vitale chiudere la connessione qui, PRIMA dei check successivi
            closeQuietly(connection);
        }

        // --- CONTROLLI DI BUSINESS FINALI ---
        // A. Controllo anti-debito
        if (driver.getWalletBalance().compareTo(BigDecimal.ZERO) <= 0) {
            forceClose(session);
        }
        // B. Controllo completamento 100%
        else if (session.getStatus() != SessionStatus.ACTIVE) {
            closeSession(session);
        }
    }

    /**
     * Chiusura volontaria da parte dell'utente (Happy Path).
     */
    public Transaction closeSession(ChargingSession session) {
        session.complete();

        ChargingStation station = session.getStation();
        station.setActive();

        BigDecimal totalCost = session.getCostTotal();
        Double totalKwhDelivered = session.getKwhDelivered();
        String description = "Addebito pari a: " + totalCost + " per ricarica completata";
        Transaction transaction = Transaction.create(session.getDriver(), TransactionType.CHARGE, totalCost, totalKwhDelivered, description);

        Connection connection = null;
        try {
            connection = dbManager.getConnection();
            connection.setAutoCommit(false);

            TransactionDao transactionDao = daoFactory.createTransactionDao(connection);
            StationDao stationDao = daoFactory.createStationDao(connection);
            SessionDao sessionDao = daoFactory.createSessionDao(connection);

            transactionDao.save(transaction);
            stationDao.update(station);
            sessionDao.update(session);

            connection.commit();
            return transaction;
        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Errore durante la chiusura della sessione", e);
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Chiusura forzata dal sistema (es. emergenza termica, spegnimento per portafoglio vuoto).
     */
    public Transaction forceClose(ChargingSession session) {
        session.interrupt();

        ChargingStation station = session.getStation();
        if (station.getStatus() != StationStatus.OVERLOADED) {
            station.setActive();
        }

        // Calcolo dell'ultimo tick per il rimborso
        double oreTick = 5.0 / 3600.0;
        double kwhLastTick = station.getPowerKw() * oreTick;

        // Recuperiamo la strategia dinamicamente dal nome salvato nella sessione
        ChargingStrategy strategy = ChargingStrategy.fromString(session.getStrategyUsed());
        double costLastTickDouble = strategy.calculateCost(kwhLastTick, station, session.getDriver());
        BigDecimal refundAmount = BigDecimal.valueOf(costLastTickDouble);

        // Se il wallet è vuoto, è colpa dell'utente, niente rimborso.
        if (session.getDriver().getWalletBalance().compareTo(BigDecimal.ZERO) <= 0) {
            refundAmount = BigDecimal.ZERO;
        }

        String refundDescription = "Rimborso pari a: " + refundAmount.toPlainString() + " a causa di interruzione tecnica";
        Transaction transaction = Transaction.create(session.getDriver(), TransactionType.REFUND, refundAmount, kwhLastTick, refundDescription);

        Driver driver = session.getDriver();
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            driver.refund(refundAmount);
        }

        Connection connection = null;
        try {
            connection = dbManager.getConnection();
            connection.setAutoCommit(false);

            TransactionDao transactionDao = daoFactory.createTransactionDao(connection);
            UserDao userDao = daoFactory.createUserDao(connection);
            StationDao stationDao = daoFactory.createStationDao(connection);
            SessionDao sessionDao = daoFactory.createSessionDao(connection);

            transactionDao.save(transaction);
            userDao.update(driver);
            stationDao.update(station);
            sessionDao.update(session);

            connection.commit();
            return transaction;
        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Errore durante la chiusura forzata della sessione", e);
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Recupera le sessioni attualmente in corso.
     * Essendo in sola lettura, usiamo il "try-with-resources" per chiudere la connessione in automatico.
     */
    public List<ChargingSession> getActiveSessions() {
        try (Connection connection = dbManager.getConnection()) {
            SessionDao sessionDao = daoFactory.createSessionDao(connection);
            return sessionDao.findActiveSessions();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle sessioni attive", e);
        }
    }

    // --- Metodi di Utility per la gestione sicura delle connessioni ---

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                System.err.println("Errore critico durante il rollback: " + ex.getMessage());
            }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                System.err.println("Errore durante la chiusura della connessione: " + ex.getMessage());
            }
        }
    }
}