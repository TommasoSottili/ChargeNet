package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.RatingAlertDao;
import it.unifi.ing.chargenet.domain.feedback.RatingAlert;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class PostgresRatingAlertDao implements RatingAlertDao {

    private final Connection connection;

    public PostgresRatingAlertDao(Connection connection) {
        this.connection = connection;
    }

    // --- Metodi CRUD Base ---

    @Override
    public void save(RatingAlert alert) {
        // DA IMPLEMENTARE
    }

    @Override
    public void update(RatingAlert alert) {
        // DA IMPLEMENTARE
    }

    @Override
    public RatingAlert findById(Long id) {
        // DA IMPLEMENTARE
        return null;
    }

    @Override
    public List<RatingAlert> findAll() {
        // DA IMPLEMENTARE
        return new ArrayList<>();
    }

    @Override
    public void delete(Long id) {
        // DA IMPLEMENTARE
    }

    // --- Metodi di Business Specifici ---

    @Override
    public boolean existsOpenAlertForStation(Long stationId) {
        // DA IMPLEMENTARE
        // Ritorno false di default per far compilare il codice in attesa della query
        return false;
    }
}