package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.RatingDao;
import it.unifi.ing.chargenet.domain.feedback.Rating;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;

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
        String sql = "INSERT INTO ratings (driver_id, station_id, session_id, stars, comment, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        // 1. Aggiungiamo Statement.RETURN_GENERATED_KEYS
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, rating.getDriver().getId());
            pstmt.setLong(2, rating.getStation().getId());
            pstmt.setLong(3, rating.getSession().getId());
            pstmt.setInt(4, rating.getStars());

            if (rating.getComment() != null && !rating.getComment().trim().isEmpty()) {
                pstmt.setString(5, rating.getComment());
            } else {
                pstmt.setNull(5, java.sql.Types.VARCHAR);
            }

            pstmt.setTimestamp(6, java.sql.Timestamp.valueOf(rating.getCreatedAt()));

            // 2. Eseguiamo l'inserimento
            pstmt.executeUpdate();

            // 3. Recuperiamo l'ID generato dal database e lo assegniamo all'oggetto Java
            try (java.sql.ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    rating.setId(generatedKeys.getLong(1));
                }
            }

            System.out.println("[PostgresRatingDao] Rating da " + rating.getStars() + " stelle salvato con successo per la sessione ID: " + rating.getSession().getId());

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio del rating per la sessione ID: " + rating.getSession().getId(), e);
        }
    }

    @Override
    public void update(Rating rating) {
        throw new UnsupportedOperationException("Sicurezza: Le recensioni sono immutabili e non possono essere modificate.");
    }

    @Override
    public Rating findById(Long id) {
        String sql = "SELECT " +
                "r.id AS rating_id, r.stars, r.comment, r.created_at AS rating_created_at, " +
                "u.id AS user_id, u.name AS user_name, u.email AS user_email, " +
                "st.id AS station_id, st.name AS station_name, st.address AS station_address, " +
                "se.id AS session_id " +
                "FROM ratings r " +
                "JOIN users u ON r.driver_id = u.id " +
                "JOIN charging_stations st ON r.station_id = st.id " +
                "JOIN charging_sessions se ON r.session_id = se.id " +
                "WHERE r.id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractRatingFromResultSet(rs);
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante la ricerca della recensione con ID: " + id, e);
        }
        return null;
    }

    @Override
    public List<Rating> findAll() {
        throw new UnsupportedOperationException("Metodo disabilitato per motivi di performance. Usare i metodi di ricerca specifici (findByStation, findByDriver).");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Sicurezza: Le recensioni sono immutabili e non possono essere modificate.");
    }

    // --- Metodi di Business Specifici (Aggiunti ora) ---

    @Override
    public boolean existsByDriverAndSession(Long driverId, Long sessionId) {
        // Query super ottimizzata: chiediamo solo il conteggio delle righe che matchano
        String sql = "SELECT COUNT(*) FROM ratings WHERE driver_id = ? AND session_id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, driverId);
            pstmt.setLong(2, sessionId);

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Se il conteggio è maggiore di 0, significa che la recensione esiste già!
                    int count = rs.getInt(1);
                    return count > 0;
                }
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante la verifica del rating per il driver " + driverId + " e la sessione " + sessionId, e);
        }

        return false;
    }

    @Override
    public void recalculateAverage(Long stationId) {
        // Questa query calcola la media matematica esatta e il conteggio totale delle recensioni,
        // aggiornando direttamente la riga della stazione di ricarica corrispondente.
        String sql = "UPDATE charging_stations " +
                "SET average_rating = (SELECT COALESCE(AVG(stars), 0) FROM ratings WHERE station_id = ?), " +
                "total_ratings = (SELECT COUNT(*) FROM ratings WHERE station_id = ?) " +
                "WHERE id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, stationId);
            pstmt.setLong(2, stationId);
            pstmt.setLong(3, stationId);

            pstmt.executeUpdate();
            System.out.println("[PostgresRatingDao] Media recensioni ricalcolata per la stazione ID: " + stationId);

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il ricalcolo della media recensioni per la stazione ID: " + stationId, e);
        }
    }

    @Override
    public java.util.List<Rating> findByStation(Long stationId) {
        String sql = "SELECT " +
                "r.id AS rating_id, r.stars, r.comment, r.created_at AS rating_created_at, " +
                "u.id AS user_id, u.name AS user_name, u.email AS user_email, " +
                "st.id AS station_id, st.name AS station_name, st.address AS station_address, " +
                "se.id AS session_id " +
                "FROM ratings r " +
                "JOIN users u ON r.driver_id = u.id " +
                "JOIN charging_stations st ON r.station_id = st.id " +
                "JOIN charging_sessions se ON r.session_id = se.id " +
                "WHERE r.station_id = ? " +
                "ORDER BY r.created_at DESC";

        List<Rating> ratings = new ArrayList<>();

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, stationId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ratings.add(extractRatingFromResultSet(rs));
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle recensioni per la stazione ID: " + stationId, e);
        }

        return ratings;
    }

    @Override
    public List<Rating> findByDriver(Long driverId) {
        String sql = "SELECT " +
                "r.id AS rating_id, r.stars, r.comment, r.created_at AS rating_created_at, " +
                "u.id AS user_id, u.name AS user_name, u.email AS user_email, " +
                "st.id AS station_id, st.name AS station_name, st.address AS station_address, " +
                "se.id AS session_id " +
                "FROM ratings r " +
                "JOIN users u ON r.driver_id = u.id " +
                "JOIN charging_stations st ON r.station_id = st.id " +
                "JOIN charging_sessions se ON r.session_id = se.id " +
                "WHERE r.driver_id = ? " +
                "ORDER BY r.created_at DESC";

        List<Rating> ratings = new ArrayList<>();

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, driverId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ratings.add(extractRatingFromResultSet(rs));
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle recensioni per il driver ID: " + driverId, e);
        }
        return ratings;
    }

    private Rating extractRatingFromResultSet(java.sql.ResultSet rs) throws java.sql.SQLException { // metodo helper
        // 1. Dati base del Rating
        Long ratingId = rs.getLong("rating_id");
        Integer stars = rs.getInt("stars");
        String comment = rs.getString("comment");
        java.time.LocalDateTime createdAt = rs.getTimestamp("rating_created_at").toLocalDateTime();

        // 2. Ricostruzione Parziale del Driver (Autore della recensione)
        Long uId = rs.getLong("user_id");
        String uName = rs.getString("user_name");
        String uEmail = rs.getString("user_email");

        // Passiamo null ai dettagli irrilevanti per leggere una recensione
        it.unifi.ing.chargenet.domain.users.Driver driver = it.unifi.ing.chargenet.domain.users.Driver.reconstitute(
                uId, uName, uEmail, null, null, null, null, null, null, java.math.BigDecimal.ZERO
        );

        // 3. Ricostruzione Parziale della ChargingStation
        Long stId = rs.getLong("station_id");
        String stName = rs.getString("station_name");
        String stAddress = rs.getString("station_address");

        // Passiamo null a Operator, Transformer, Tariffe, ecc. per risparmiare memoria
        ChargingStation station = ChargingStation.reconstitute(
                stId, null, null, stName, stAddress, null, null, null, null, null,
                null, null, null, null, null, null, null
        );

        // 4. Ricostruzione Parziale della ChargingSession
        Long seId = rs.getLong("session_id");

        ChargingSession session = ChargingSession.reconstitute(
                seId, driver, station, null, null, null, null, null, null, createdAt, createdAt // Usiamo createdAt come fallback
        );

        // 5. Creazione finale del Rating
        return Rating.reconstitute(ratingId, driver, station, session, stars, comment, createdAt);
    }
}