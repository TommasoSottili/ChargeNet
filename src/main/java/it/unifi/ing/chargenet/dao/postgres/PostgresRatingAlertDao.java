package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.RatingAlertDao;
import it.unifi.ing.chargenet.domain.feedback.RatingAlert;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
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
        // 1. Prepariamo la query. Sono 5 colonne e 5 punti interrogativi.
        String sql = "INSERT INTO rating_alerts (station_id, avg_at_creation, status, manager_note, created_at) " +
                "VALUES (?, ?, ?, ?, ?)";

        // 2. Chiediamo esplicitamente a Postgres di restituirci l'ID che genererà (RETURN_GENERATED_KEYS)
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, alert.getStation().getId());
            pstmt.setDouble(2, alert.getAvgAtCreation());

            // 3. Convertiamo l'Enum RatingAlertStatus nella sua rappresentazione a Stringa (es. "PENDING")
            pstmt.setString(3, alert.getStatus().name());

            // 4. Gestione sicura del NULL: appena creato, il managerNote è null.
            // Se in futuro salveremo un alert già risolto, questo if ci copre le spalle.
            if (alert.getManagerNote() != null) {
                pstmt.setString(4, alert.getManagerNote());
            } else {
                pstmt.setNull(4, java.sql.Types.VARCHAR);
            }

            pstmt.setTimestamp(5, java.sql.Timestamp.valueOf(alert.getCreatedAt()));

            // 5. Eseguiamo l'inserimento
            pstmt.executeUpdate();

            // 6. Recuperiamo l'ID generato dal database e lo assegniamo all'oggetto Java (come hai appena fatto per le Transazioni)
            try (java.sql.ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    alert.setId(generatedKeys.getLong(1));
                }
            }

            System.out.println("[PostgresRatingAlertDao] Nuovo Alert salvato con successo per la stazione ID: " + alert.getStation().getId() + " (ID Alert: " + alert.getId() + ")");

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio dell'alert per la stazione ID: " + alert.getStation().getId(), e);
        }
    }

    @Override
    public void update(RatingAlert alert) {
        // L'unica cosa che può cambiare nel tempo sono lo stato e la nota del manager
        String sql = "UPDATE rating_alerts SET status = ?, manager_note = ? WHERE id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, alert.getStatus().name());

            if (alert.getManagerNote() != null) {
                pstmt.setString(2, alert.getManagerNote());
            } else {
                pstmt.setNull(2, java.sql.Types.VARCHAR);
            }

            pstmt.setLong(3, alert.getId());
            pstmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante l'aggiornamento dell'alert ID: " + alert.getId(), e);
        }
    }

    @Override
    public RatingAlert findById(Long id) {
        String sql = "SELECT a.id AS alert_id, a.avg_at_creation, a.status, a.manager_note, a.created_at AS alert_created_at, " +
                "st.id AS station_id, st.name AS station_name " +
                "FROM rating_alerts a " +
                "JOIN charging_stations st ON a.station_id = st.id " +
                "WHERE a.id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return extractRatingAlertFromResultSet(rs);
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore ricerca alert per ID: " + id, e);
        }
        return null;
    }

    @Override
    public List<RatingAlert> findAll() {
        throw new UnsupportedOperationException("Ricerca globale disabilitata per performance.");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Sicurezza: Gli alert non possono essere cancellati (Audit Log).");
    }

    @Override
    public java.util.List<RatingAlert> findByStatus(it.unifi.ing.chargenet.domain.feedback.RatingAlertStatus status) {
        String sql = "SELECT a.id AS alert_id, a.avg_at_creation, a.status, a.manager_note, a.created_at AS alert_created_at, " +
                "st.id AS station_id, st.name AS station_name " +
                "FROM rating_alerts a " +
                "JOIN charging_stations st ON a.station_id = st.id " +
                "WHERE a.status = ? ORDER BY a.created_at ASC"; // I più vecchi per primi (FIFO)

        java.util.List<RatingAlert> alerts = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) alerts.add(extractRatingAlertFromResultSet(rs));
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore ricerca alert per status: " + status, e);
        }
        return alerts;
    }

    @Override
    public java.util.List<RatingAlert> findByStation(Long stationId) {
        String sql = "SELECT a.id AS alert_id, a.avg_at_creation, a.status, a.manager_note, a.created_at AS alert_created_at, " +
                "st.id AS station_id, st.name AS station_name " +
                "FROM rating_alerts a " +
                "JOIN charging_stations st ON a.station_id = st.id " +
                "WHERE a.station_id = ? ORDER BY a.created_at DESC"; // I più recenti per primi

        java.util.List<RatingAlert> alerts = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, stationId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) alerts.add(extractRatingAlertFromResultSet(rs));
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore ricerca alert per stazione: " + stationId, e);
        }
        return alerts;
    }

    @Override
    public boolean existsOpenAlertForStation(Long stationId) {
        // Esegue una COUNT per verificare la presenza di alert non ancora risolti
        String sql = "SELECT COUNT(*) FROM rating_alerts WHERE station_id = ? AND status = 'PENDING'";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, stationId);

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count > 0; // Se c'è almeno 1 record, restituisce true
                }
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante la verifica di alert PENDING per la stazione ID: " + stationId, e);
        }

        return false;
    }

    private RatingAlert extractRatingAlertFromResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {
        Long alertId = rs.getLong("alert_id");
        Double avgAtCreation = rs.getDouble("avg_at_creation");

        String statusStr = rs.getString("status");
        it.unifi.ing.chargenet.domain.feedback.RatingAlertStatus status =
                statusStr != null ? it.unifi.ing.chargenet.domain.feedback.RatingAlertStatus.valueOf(statusStr) : null;

        String managerNote = rs.getString("manager_note");
        java.time.LocalDateTime createdAt = rs.getTimestamp("alert_created_at").toLocalDateTime();

        // Ricostruzione Parziale della ChargingStation (ci basta l'ID e il nome per mostrare l'alert)
        Long stId = rs.getLong("station_id");
        String stName = rs.getString("station_name");

        ChargingStation station = ChargingStation.reconstitute(
                stId, null, null, stName, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );

        return RatingAlert.reconstitute(alertId, station, avgAtCreation, status, managerNote, createdAt);
    }
}