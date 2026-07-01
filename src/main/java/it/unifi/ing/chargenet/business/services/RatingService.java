package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.RatingDao;
import it.unifi.ing.chargenet.dao.interfaces.RatingAlertDao;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.feedback.Rating;
import it.unifi.ing.chargenet.domain.feedback.RatingAlert;
import it.unifi.ing.chargenet.domain.users.Driver;

import java.util.List;
import java.sql.Connection;
import java.sql.SQLException;

public class RatingService {

    private final DaoFactory daoFactory;

    // Io chiedo solo l'interfaccia. Chi mi usa, mi passerà l'oggetto vero!
    public RatingService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public Rating leaveRating(Driver driver, ChargingStation station, ChargingSession session, int stars, String comment) {
        // 1. Richiediamo una connessione usando il metodo statico del DatabaseManager
        Connection connection = DatabaseManager.getConnection();

        try {
            // Disabilitiamo l'autocommit per avviare la transazione in modalità manuale
            connection.setAutoCommit(false);

            // Usiamo la daoFactory che ci è stata passata nel costruttore!
            RatingDao ratingDao = this.daoFactory.createRatingDao(connection);

            // 2. Controllo preventivo: Il Driver ha già recensito questa sessione?
            if (ratingDao.existsByDriverAndSession(driver.getId(), session.getId())) {
                throw new IllegalStateException("Errore di business: Il driver ha già inserito una recensione per questa sessione.");
            }

            // 3. Creazione del Rating: Delega la validazione (1-5 stelle) al Factory Method del dominio
            Rating nuovoRating = Rating.leave(driver, station, session, stars, comment);

            // 4. Persistiamo il rating nel database
            ratingDao.save(nuovoRating);

            // 5. Verifica ed eventuale generazione di un Alert di qualità (passiamo solo la connessione e la stazione)
            checkRatingAlert(connection, station);

            // 6. Se siamo arrivati fin qui, confermiamo e salviamo definitivamente le modifiche nel DB
            connection.commit();
            System.out.println("[RatingService] Recensione salvata con successo. Sessione ID: " + session.getId());

            return nuovoRating;

        } catch (Exception e) {
            try {
                if (connection != null) connection.rollback();
                System.err.println("[RatingService] Transazione annullata (Rollback). Motivo: " + e.getMessage());
            } catch (SQLException ex) {
                System.err.println("[RatingService] Errore critico durante il rollback: " + ex.getMessage());
            }

            // AGGIUNGI QUESTA RIGA: Se è un errore di business, rilancialo così com'è!
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            // Altrimenti, se è un errore del DB, lancia l'eccezione generica
            throw new RuntimeException("Impossibile salvare la recensione: " + e.getMessage(), e);
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("[RatingService] Errore durante la chiusura della connessione: " + e.getMessage());
            }
        }
    }

    private void checkRatingAlert(Connection connection, ChargingStation station) {
        RatingDao ratingDao = this.daoFactory.createRatingDao(connection);
        StationDao stationDao = this.daoFactory.createStationDao(connection);
        RatingAlertDao ratingAlertDao = this.daoFactory.createRatingAlertDao(connection);

        // 1. Ricalcolo della media sul database tramite query aggregata SQL
        ratingDao.recalculateAverage(station.getId());

        // 2. Recuperiamo la stazione aggiornata dal DB per leggere la nuova media e il nuovo conteggio totale
        ChargingStation updatedStation = stationDao.findById(station.getId());

        if (updatedStation != null) {
            Double currentAvg = updatedStation.getAverageRating();
            Integer totalRatings = updatedStation.getTotalRatings();

            // 3. Regola di Business: Media sotto 2.0 con almeno 5 rating totali
            if (currentAvg != null && currentAvg < 2.0 && totalRatings != null && totalRatings >= 5) {

                // 4. Verifichiamo che non ci sia già un Alert PENDING per evitare duplicati
                if (!ratingAlertDao.existsOpenAlertForStation(station.getId())) {

                    // 5. Creazione e salvataggio del nuovo Alert
                    RatingAlert alert = RatingAlert.create(updatedStation, currentAvg);
                    ratingAlertDao.save(alert);

                    System.out.println("[RatingService] ATTENZIONE: Generato nuovo Alert per la stazione " + station.getId() + " (Media: " + currentAvg + ")");
                }
            }
        }
    }

    public void checkRatingAlerts() {
        java.sql.Connection connection = null;
        try {
            // 1. Apriamo una connessione dedicata per questa operazione massiva
            connection = it.unifi.ing.chargenet.dao.postgres.DatabaseManager.getConnection();
            connection.setAutoCommit(false); // Inizio transazione

            // 2. Creiamo lo StationDao passando la connessione appena aperta
            it.unifi.ing.chargenet.dao.interfaces.StationDao stationDao = this.daoFactory.createStationDao(connection);

            // 3. Recuperiamo tutte le stazioni (nota: assicurati che il metodo si chiami getAll() o findAll() nel tuo DAO)
            java.util.List<ChargingStation> allStations = stationDao.findAll();

            // 4. Cicliamo su tutte le stazioni e passiamo ENTRAMBI i parametri al metodo privato
            for (ChargingStation station : allStations) {
                this.checkRatingAlert(connection, station);
            }

            // 5. Se tutto è andato bene, facciamo il commit di tutti gli eventuali alert creati in un colpo solo
            connection.commit();
            System.out.println("[RatingService] Controllo massivo degli alert completato con successo su " + allStations.size() + " stazioni.");

        } catch (Exception e) {
            // Se qualcosa fallisce, facciamo rollback per non lasciare dati a metà
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (java.sql.SQLException ex) { /* Log silenzioso */ }
            }
            throw new RuntimeException("Errore critico durante il controllo massivo degli alert: " + e.getMessage(), e);
        } finally {
            // Chiudiamo in sicurezza
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (java.sql.SQLException ex) { /* Log silenzioso */ }
            }
        }
    }

    public List<RatingAlert> getPendingAlerts() {
        try (Connection connection = DatabaseManager.getConnection()) {
            RatingAlertDao alertDao = daoFactory.createRatingAlertDao(connection);

            // Usiamo l'Enum corretto che mi hai mostrato nello screenshot
            return alertDao.findByStatus(it.unifi.ing.chargenet.domain.feedback.RatingAlertStatus.PENDING);

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il recupero degli alert in sospeso", e);
        }
    }

    public void suspendStationFromAlert(RatingAlert alert, String notes) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);

            RatingAlertDao alertDao = daoFactory.createRatingAlertDao(connection);
            StationDao stationDao = daoFactory.createStationDao(connection);

            // Idrata la station COMPLETA dal DB: quella dentro l'alert è uno stub
            // (solo id) e farebbe NPE in stationDao.update (getOperator() == null).
            ChargingStation station = stationDao.findById(alert.getStation().getId());
            if (station == null) {
                throw new IllegalStateException("Stazione dell'alert non trovata: "
                        + alert.getStation().getId());
            }

            alert.resolveSuspend(notes);
            station.setSuspended();

            alertDao.update(alert);
            stationDao.update(station);

            connection.commit();
            System.out.println("[RatingService] Stazione '" + station.getName()
                    + "' sospesa. Alert ID: " + alert.getId());

        } catch (Exception e) {
            rollbackQuietly(connection);
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new RuntimeException("Errore critico durante la sospensione: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    public void dismissAlert(RatingAlert alert, String notes) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false); // Inizio transazione

            RatingAlertDao alertDao = daoFactory.createRatingAlertDao(connection);

            // 1. LOGICA DI DOMINIO: Usiamo il TUO fantastico metodo resolveIgnore
            alert.resolveIgnore(notes);

            // 2. PERSISTENZA: Aggiorniamo solo l'alert, la stazione rimane attiva
            alertDao.update(alert);

            // 3. COMMIT
            connection.commit();
            System.out.println("[RatingService] Alert ID: " + alert.getId() + " ignorato con successo (Falso allarme).");

        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Errore critico durante la chiusura dell'alert: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                /* Log silenzioso */
            }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                connection.close();
            } catch (SQLException ex) {
                /* Log silenzioso */
            }
        }
    }
}