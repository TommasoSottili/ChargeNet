package it.unifi.ing.chargenet.business.services;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.domain.users.*;
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

        Connection connection = null;
        try {
            // Apriamo una connessione "fresca" per questa registrazione
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false); // Inizio transazione

            // Creiamo il DAO "al volo" usando l'Abstract Factory del collega
            UserDao userDao = daoFactory.createUserDao(connection);

            // Validazione business
            if (userDao.findByEmail(email) != null) {
                throw new DuplicateEmailException("Impossibile registrarsi: l'email " + email + " è già in uso.");
            }

            // Hashing della password
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            // Creazione nuovo utente (i campi extra partono null/default)
            User newUser;
            switch (role) {
                case DRIVER:
                    newUser = new Driver(null, null, null, null, null, name, email, hashedPassword);
                    break;
                case STATION_OPERATOR:
                    newUser = new StationOperator(name, hashedPassword, email);
                    break;
                case ENERGY_MANAGER:
                    newUser = new EnergyManager(name, hashedPassword, email);
                    break;
                default:
                    throw new IllegalArgumentException("Ruolo non supportato.");
            }

            // Salvataggio tramite DAO
            userDao.save(newUser);

            // Conferma definitiva su Database
            connection.commit();
            System.out.println("[AuthService] Registrazione completata per: " + email);

            return newUser;

        } catch (Exception e) {
            // Se c'è un errore, annulliamo tutto
            rollbackQuietly(connection);
            if (e instanceof DuplicateEmailException) throw (DuplicateEmailException) e;
            throw new RuntimeException("Errore critico durante la registrazione", e);
        } finally {
            // Fondamentale: chiudiamo il "rubinetto" del database
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
}