package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.TransactionDao;
import it.unifi.ing.chargenet.domain.financials.Transaction;

import java.sql.Connection;
import java.util.List;

public class PostgresTransactionDao implements TransactionDao {

    private Connection connection;

    public PostgresTransactionDao(Connection connection) {
        this.connection = connection;
    }

    // --- Metodi ereditati da GenericDao ---
    @Override
    public void save(Transaction entity) {
        // Il tuo SessionService chiamerà questo alla chiusura della sessione!
        throw new UnsupportedOperationException("Da implementare: save Transaction (Lavoro per il collega)");
    }

    @Override
    public Transaction findById(Long id) {
        throw new UnsupportedOperationException("Da implementare: findById Transaction (Lavoro per il collega)");
    }

    @Override
    public List<Transaction> findAll() {
        throw new UnsupportedOperationException("Da implementare: findAll Transaction (Lavoro per il collega)");
    }

    @Override
    public void update(Transaction entity) {
        throw new UnsupportedOperationException("Da implementare: update Transaction (Lavoro per il collega)");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Da implementare: delete Transaction (Lavoro per il collega)");
    }
}