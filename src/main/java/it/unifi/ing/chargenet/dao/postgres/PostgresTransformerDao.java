package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.TransformerDao;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class PostgresTransformerDao implements TransformerDao {

    private final Connection connection;

    public PostgresTransformerDao(Connection connection) {
        this.connection = connection;
    }

    // --- Metodi CRUD Base ---

    @Override
    public void save(PowerTransformer transformer) {
        // DA IMPLEMENTARE
    }

    @Override
    public void update(PowerTransformer transformer) {
        // DA IMPLEMENTARE
    }

    @Override
    public PowerTransformer findById(Long id) {
        // DA IMPLEMENTARE
        return null;
    }

    @Override
    public List<PowerTransformer> findAll() {
        // DA IMPLEMENTARE
        return new ArrayList<>();
    }

    @Override
    public void delete(Long id) {
        // DA IMPLEMENTARE
    }

    // --- Metodi di Business Specifici ---

    @Override
    public List<PowerTransformer> findOverheated() {
        // DA IMPLEMENTARE
        // Ritorno una lista vuota per far compilare il codice in attesa della query SQL
        return new ArrayList<>();
    }
}