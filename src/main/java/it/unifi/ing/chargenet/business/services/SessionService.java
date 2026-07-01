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
import it.unifi.ing.chargenet.domain.users.StationOperator;

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

    private final DaoFactory daoFactory;

    public SessionService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public ChargingSession openSession(Driver driver, ChargingStation station, ChargingStrategy strategy, double batteryStart) {
        double minRequiredCost = strategy.calculateCost(5.0, station, driver);
        if (driver.getWalletBalance().compareTo(BigDecimal.valueOf(minRequiredCost)) < 0) {
            throw new IllegalStateException("Saldo insufficiente: sono necessari fondi per almeno 5 kWh.");
        }

        ChargingSession session = ChargingSession.open(driver, station, strategy.getType(), batteryStart);
        station.setBusy();

        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();   // ← era DatabaseManager.getConnection()
            connection.setAutoCommit(false);

            SessionDao sessionDao = daoFactory.createSessionDao(connection);
            StationDao stationDao = daoFactory.createStationDao(connection);

            sessionDao.save(session);
            stationDao.update(station);

            connection.commit();
            return session;
        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Impossibile aprire la sessione a causa di un errore nel database.", e);
        } finally {
            closeQuietly(connection);
        }
    }

    public void addTick(ChargingSession session, ChargingStrategy strategy) {
        double oreTick = 5.0 / 3600.0;
        double kwhThisTick = session.getStation().getPowerKw() * oreTick;
        double costDouble = strategy.calculateCost(kwhThisTick, session.getStation(), session.getDriver());
        BigDecimal costThisTick = BigDecimal.valueOf(costDouble);

        session.addTick(kwhThisTick, costThisTick);

        Driver driver = session.getDriver();
        driver.charge(costThisTick);

        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();   // ←
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
            closeQuietly(connection);
        }

        if (driver.getWalletBalance().compareTo(BigDecimal.ZERO) <= 0) {
            forceClose(session);
        } else if (session.getStatus() != SessionStatus.ACTIVE) {
            closeSession(session);
        }
    }

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
            connection = DatabaseManager.getConnection();   // ←
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

    public Transaction forceClose(ChargingSession session) {
        session.interrupt();
        ChargingStation station = session.getStation();
        if (station.getStatus() != StationStatus.OVERLOADED) {
            station.setActive();
        }

        double oreTick = 5.0 / 3600.0;
        double kwhLastTick = station.getPowerKw() * oreTick;

        ChargingStrategy strategy = ChargingStrategy.fromEnum(session.getStrategyUsed());
        double costLastTickDouble = strategy.calculateCost(kwhLastTick, station, session.getDriver());
        BigDecimal refundAmount = BigDecimal.valueOf(costLastTickDouble);

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
            connection = DatabaseManager.getConnection();   // ←
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
        try (Connection connection = DatabaseManager.getConnection()) {
            SessionDao sessionDao = daoFactory.createSessionDao(connection);
            return sessionDao.findActiveSessions();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle sessioni attive", e);
        }
    }

    /**
     * SODDISFA UC4 (View Revenue Stats)
     * Conta il numero totale di sessioni storiche associate alle colonnine di un operatore.
     */
    public int countSessionsByOperator(StationOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("L'operatore non può essere nullo.");
        }

        try (Connection connection = DatabaseManager.getConnection()) {
            SessionDao sessionDao = daoFactory.createSessionDao(connection);
            return sessionDao.countByOperatorId(operator.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il conteggio delle sessioni per operatore", e);
        }
    }

    /**
     * SODDISFA UC4 (View Revenue Stats)
     * Calcola il totale dei kWh erogati da tutte le colonnine di un operatore.
     */
    public double calculateTotalEnergySoldByOperator(StationOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("L'operatore non può essere nullo.");
        }

        try (Connection connection = DatabaseManager.getConnection()) {
            SessionDao sessionDao = daoFactory.createSessionDao(connection);

            // Nota per il DAO: assicurati che il metodo sumEnergyByOperatorId
            // restituisca 0.0 e non un valore nullo se l'operatore non ha ancora sessioni.
            return sessionDao.sumEnergyByOperatorId(operator.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il calcolo dell'energia totale venduta", e);
        }
    }

    /**
     * SODDISFA UC12 (View My Sessions)
     * Recupera lo storico di tutte le sessioni completate da un Driver.
     */
    public List<ChargingSession> getSessionHistoryByDriver(Driver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("Il driver non può essere nullo.");
        }

        try (Connection connection = DatabaseManager.getConnection()) {
            SessionDao sessionDao = daoFactory.createSessionDao(connection);
            return sessionDao.findCompletedByDriverId(driver.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero dello storico sessioni del driver", e);
        }
    }

    /**
     * SODDISFA UC10 (Close Charging Session)
     * Recupera l'eventuale sessione attualmente in corso per un Driver.
     */
    public ChargingSession getActiveSessionForDriver(Driver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("Il driver non può essere nullo.");
        }

        try (Connection connection = DatabaseManager.getConnection()) {
            SessionDao sessionDao = daoFactory.createSessionDao(connection);
            return sessionDao.findActiveByDriverId(driver.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero della sessione attiva del driver", e);
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