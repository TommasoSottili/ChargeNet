package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.SessionDao;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

public class PostgresSessionDao implements SessionDao {

    private final Connection connection;

    public PostgresSessionDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void save(ChargingSession session) {
        String sql = "INSERT INTO charging_sessions (driver_id, station_id, strategy_used, battery_start, battery_current, kwh_delivered, cost_total, status, opened_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // Usiamo RETURN_GENERATED_KEYS per recuperare l'ID (BIGINT) creato automaticamente dal database
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, session.getDriver().getId()); // Adatta i getter se nel tuo modello si chiamano diversamente
            stmt.setLong(2, session.getStation().getId());
            stmt.setString(3, session.getStrategyUsed().name()); // Presumo sia un Enum
            stmt.setDouble(4, session.getBatteryStart());
            stmt.setDouble(5, session.getBatteryCurrent());
            stmt.setDouble(6, session.getKwhDelivered());
            stmt.setBigDecimal(7, session.getCostTotal());
            stmt.setString(8, session.getStatus().name());
            stmt.setTimestamp(9, Timestamp.valueOf(session.getOpenedAt()));

            stmt.executeUpdate();

            // Recupera l'ID autogenerato e assegnalo all'oggetto Java
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    session.setId(generatedKeys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio della sessione", e);
        }
    }

    @Override
    public void update(ChargingSession session) {
        // Aggiorniamo solo i campi che variano durante la ricarica
        String sql = "UPDATE charging_sessions SET battery_current = ?, kwh_delivered = ?, cost_total = ?, status = ?, closed_at = ? WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, session.getBatteryCurrent());
            stmt.setDouble(2, session.getKwhDelivered());
            stmt.setBigDecimal(3, session.getCostTotal());
            stmt.setString(4, session.getStatus().name());

            // Gestione sicura del closedAt (se la ricarica è ancora attiva, closedAt è null!)
            if (session.getClosedAt() != null) {
                stmt.setTimestamp(5, Timestamp.valueOf(session.getClosedAt()));
            } else {
                stmt.setNull(5, Types.TIMESTAMP);
            }

            stmt.setLong(6, session.getId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'aggiornamento della sessione", e);
        }
    }

    @Override
    public ChargingSession findById(Long id) {
        String sql = "SELECT * FROM charging_sessions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSession(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore nella lettura della sessione per ID", e);
        }
        return null;
    }

    @Override
    public List<ChargingSession> findAll() {
        return executeQueryToList("SELECT * FROM charging_sessions");
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM charging_sessions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'eliminazione della sessione", e);
        }
    }

    // --- Metodi Specifici ---

    @Override
    public List<ChargingSession> findByDriver(Long driverId) {
        String sql = "SELECT * FROM charging_sessions WHERE driver_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, driverId);
            return executeResultSetToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca sessioni per guidatore", e);
        }
    }

    @Override
    public List<ChargingSession> findActiveSessions() {
        // Cerca tutte le sessioni con stato ACTIVE
        return executeQueryToList("SELECT * FROM charging_sessions WHERE status = 'ACTIVE'");
    }

    @Override
    public List<ChargingSession> findActiveByStation(Long stationId) {
        String sql = "SELECT * FROM charging_sessions WHERE station_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, stationId);
            return executeResultSetToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca sessioni attive per stazione", e);
        }
    }

    // --- Utility Methods (Per non ripetere il codice!) ---

    private List<ChargingSession> executeQueryToList(String sql) {
        List<ChargingSession> list = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToSession(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore esecuzione query: " + sql, e);
        }
        return list;
    }

    private List<ChargingSession> executeResultSetToList(PreparedStatement stmt) throws SQLException {
        List<ChargingSession> list = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToSession(rs));
            }
        }
        return list;
    }

    private ChargingSession mapResultSetToSession(ResultSet rs) throws SQLException {
        // Idratazione COMPLETA degli oggetti annidati: una sessione ricostruita dal DB
        // deve essere un aggregato valido, non gusci con solo l'id.
        // Stesso package dao.postgres → nessun import nuovo da aggiungere.
        PostgresUserDao userDao = new PostgresUserDao(connection);
        PostgresStationDao stationDao = new PostgresStationDao(connection);

        Driver driver = (Driver) userDao.findById(rs.getLong("driver_id"));
        ChargingStation station = stationDao.findById(rs.getLong("station_id"));

        SessionStatus status = SessionStatus.valueOf(rs.getString("status"));

        Timestamp closedAtTs = rs.getTimestamp("closed_at");
        LocalDateTime closedAt = (closedAtTs != null) ? closedAtTs.toLocalDateTime() : null;

        return ChargingSession.reconstitute(
                rs.getLong("id"),
                driver,
                station,
                ChargingType.valueOf(rs.getString("strategy_used")),
                rs.getDouble("battery_start"),
                rs.getDouble("battery_current"),
                rs.getDouble("kwh_delivered"),
                rs.getBigDecimal("cost_total"),
                status,
                rs.getTimestamp("opened_at").toLocalDateTime(),
                closedAt
        );
    }

    // =========================================================================
    // METODI AGGIUNTIVI PER SUPPORTO SERVICE LAYER E USE CASES
    // =========================================================================

    @Override
    public ChargingSession findActiveByDriverId(Long driverId) {
        String sql = "SELECT * FROM charging_sessions WHERE driver_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, driverId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSession(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca sessione attiva per il driver", e);
        }
        return null;
    }

    @Override
    public List<ChargingSession> findCompletedByDriverId(Long driverId) {
        String sql = "SELECT * FROM charging_sessions WHERE driver_id = ? AND status = 'COMPLETED'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, driverId);
            return executeResultSetToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca sessioni completate per il driver", e);
        }
    }

    @Override
    public int countByOperatorId(Long operatorId) {
        // Usiamo una JOIN perché la sessione conosce la stazione, e la stazione conosce l'operatore
        String sql = "SELECT COUNT(cs.id) FROM charging_sessions cs " +
                "JOIN charging_stations st ON cs.station_id = st.id " +
                "WHERE st.operator_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, operatorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il conteggio delle sessioni per l'operatore", e);
        }
        return 0;
    }

    @Override
    public double sumEnergyByOperatorId(Long operatorId) {
        // Usiamo COALESCE per garantire che torni 0.0 e non NULL se l'operatore non ha mai venduto energia
        String sql = "SELECT COALESCE(SUM(cs.kwh_delivered), 0.0) FROM charging_sessions cs " +
                "JOIN charging_stations st ON cs.station_id = st.id " +
                "WHERE st.operator_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, operatorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il calcolo dell'energia venduta per l'operatore", e);
        }
        return 0.0;
    }
}