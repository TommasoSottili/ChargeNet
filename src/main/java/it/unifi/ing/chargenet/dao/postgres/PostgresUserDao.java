package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.domain.users.Role;

import java.sql.Connection;
import java.util.List;

public class PostgresUserDao implements UserDao {

    private Connection connection;

    // Costruttore cruciale per il pattern architetturale (Transazioni)
    public PostgresUserDao(Connection connection) {
        this.connection = connection;
    }

    // --- Metodi ereditati da GenericDao ---
    @Override
    public void save(User entity) {
        throw new UnsupportedOperationException("Da implementare: save User (Lavoro per il collega)");
    }

    @Override
    public User findById(Long id) {
        throw new UnsupportedOperationException("Da implementare: findById User (Lavoro per il collega)");
    }

    @Override
    public List<User> findAll() {
        throw new UnsupportedOperationException("Da implementare: findAll User (Lavoro per il collega)");
    }

    @Override
    public void update(User entity) {
        // Il tuo SessionService chiamerà questo per aggiornare il portafoglio!
        // Finché non siamo pronti a testarlo per intero, lo lasciamo come stub.
        throw new UnsupportedOperationException("Da implementare: update User (Lavoro per il collega)");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Da implementare: delete User (Lavoro per il collega)");
    }

    // --- Metodi specifici di UserDao ---
    @Override
    public User findByEmail(String email) {
        throw new UnsupportedOperationException("Da implementare: findByEmail (Lavoro per il collega)");
    }

    @Override
    public List<User> findByRole(Role role) {
        throw new UnsupportedOperationException("Da implementare: findByRole (Lavoro per il collega)");
    }
}