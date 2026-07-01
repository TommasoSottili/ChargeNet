package it.unifi.ing.chargenet.business.services;
import it.unifi.ing.chargenet.business.exceptions.AuthenticationException;
import it.unifi.ing.chargenet.business.exceptions.DuplicateEmailException;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.domain.users.*;
import it.unifi.ing.chargenet.business.utils.GpsSimulator;
import org.mindrot.jbcrypt.BCrypt;
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import java.sql.Connection;
import java.sql.SQLException;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;

public class AuthService {

    private final DaoFactory daoFactory;
    private User currentUser;

    public AuthService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
        this.currentUser = null;
    }

    public User register(String name, String email, String password, Role role) {

        if (role == Role.DRIVER) {
            throw new IllegalArgumentException(
                    "Usa registerDriver(...) per registrare un Driver: " +
                            "richiede tipo di connettore e posizione geografica.");
        }

        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);

            UserDao userDao = daoFactory.createUserDao(connection);

            if (userDao.findByEmail(email) != null) {
                throw new DuplicateEmailException(
                        "Impossibile registrarsi: l'email " + email + " è già in uso.");
            }

            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            User newUser = switch (role) {
                case STATION_OPERATOR -> new StationOperator(name, hashedPassword, email);
                case ENERGY_MANAGER   -> new EnergyManager(name, hashedPassword, email);
                default -> throw new IllegalArgumentException("Ruolo non supportato: " + role);
            };

            userDao.save(newUser);
            connection.commit();
            return newUser;

        } catch (Exception e) {
            rollbackQuietly(connection);
            if (e instanceof DuplicateEmailException) throw (DuplicateEmailException) e;
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new RuntimeException("Errore critico durante la registrazione", e);
        } finally {
            closeQuietly(connection);
        }
    }

    public Driver registerDriver(String name, String email, String password,
                                 ConnectorType connectorType) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);
            UserDao userDao = daoFactory.createUserDao(connection);

            if (userDao.findByEmail(email) != null) {
                throw new DuplicateEmailException(
                        "Impossibile registrarsi: l'email " + email + " è già in uso.");
            }

            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            double[] coords = GpsSimulator.randomPosition();

            Driver newDriver = new Driver(
                    name, email, hashedPassword, connectorType, coords[0], coords[1]
            );

            userDao.save(newDriver);
            connection.commit();
            return newDriver;

        } catch (Exception e) {
            rollbackQuietly(connection);
            if (e instanceof DuplicateEmailException) throw (DuplicateEmailException) e;
            throw new RuntimeException("Errore critico durante la registrazione del Driver", e);
        } finally {
            closeQuietly(connection);
        }
    }

    public User login(String email, String rawPassword) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            UserDao userDao = daoFactory.createUserDao(connection);

            // Cerchiamo l'utente
            User user = userDao.findByEmail(email);

            // Verifichiamo se esiste e se la password corrisponde
            if (user == null || !BCrypt.checkpw(rawPassword, user.getPassword())) {
                throw new AuthenticationException("Credenziali non valide.");
            }

            // Impostiamo la sessione
            this.currentUser = user;
            System.out.println("[AuthService] Login effettuato con successo: " + email);

            return user;

        } catch (Exception e) {
            if (e instanceof AuthenticationException) throw (AuthenticationException) e;
            throw new RuntimeException("Errore critico durante il login", e);
        } finally {
            closeQuietly(connection);
        }
    }

    public void logout() {
        this.currentUser = null;
        System.out.println("[AuthService] Logout completato.");
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
                connection.close(); }
            catch (SQLException ex) { /* Log silenzioso */ }
        }
    }
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Ricarica un utente fresco dal DB a partire dall'id.
     * Serve a evitare di mostrare dati di sessione stantii (es. saldo wallet
     * dopo una ricarica). Restituisce l'aggregato completamente idratato.
     */
    public User refreshUser(Long userId) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            UserDao userDao = daoFactory.createUserDao(connection);
            return userDao.findById(userId);
        } catch (Exception e) {
            throw new RuntimeException("Errore durante il refresh dell'utente", e);
        } finally {
            closeQuietly(connection);
        }
    }
}