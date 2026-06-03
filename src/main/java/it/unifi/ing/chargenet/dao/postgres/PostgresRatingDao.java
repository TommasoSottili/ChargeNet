package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.RatingDao;
import it.unifi.ing.chargenet.domain.feedback.Rating;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class PostgresRatingDao implements RatingDao {

    private final Connection connection;

    public PostgresRatingDao(Connection connection) {
        this.connection = connection;
    }

    // --- Metodi CRUD Base ---

    @Override
    public void save(Rating rating) {
        // DA IMPLEMENTARE
    }

    @Override
    public void update(Rating rating) {
        // DA IMPLEMENTARE
    }

    @Override
    public Rating findById(Long id) {
        // DA IMPLEMENTARE
        return null;
    }

    @Override
    public List<Rating> findAll() {
        // DA IMPLEMENTARE
        return new ArrayList<>();
    }

    @Override
    public void delete(Long id) {
        // DA IMPLEMENTARE
    }

    // --- Metodi di Business Specifici (Aggiunti ora) ---

    @Override
    public boolean existsByDriverAndSession(Long driverId, Long sessionId) {
        // DA IMPLEMENTARE
        // Ritorno false di default per far compilare il codice
        return false;
    }

    @Override
    public void recalculateAverage(Long stationId) {
        // DA IMPLEMENTARE
        // In futuro qui il tuo collega scriverà una query del tipo:
        // UPDATE stations SET average_rating = (SELECT AVG(score) FROM ratings WHERE station_id = ?) WHERE id = ?
    }
}