package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.domain.users.Driver;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostgresStationDao implements StationDao {

    private final Connection connection;

    public PostgresStationDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void save(ChargingStation station) {
        String sql = "INSERT INTO charging_stations " +
                "(operator_id, transformer_id, name, address, latitude, longitude, connector_type, " +
                "power_kw, is_solar_powered, tariff_operator, tariff_platform, average_rating, " +
                "total_ratings, status, reserved_by_id, expiration_timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setStationParameters(stmt, station);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    station.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio della ChargingStation", e);
        }
    }

    @Override
    public void update(ChargingStation station) {
        String sql = "UPDATE charging_stations SET " +
                "operator_id = ?, transformer_id = ?, name = ?, address = ?, latitude = ?, " +
                "longitude = ?, connector_type = ?, power_kw = ?, is_solar_powered = ?, " +
                "tariff_operator = ?, tariff_platform = ?, average_rating = ?, total_ratings = ?, " +
                "status = ?, reserved_by_id = ?, expiration_timestamp = ? " +
                "WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            setStationParameters(stmt, station);
            stmt.setLong(17, station.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'aggiornamento della ChargingStation", e);
        }
    }

    @Override
    public ChargingStation findById(Long id) {
        String sql = "SELECT * FROM charging_stations WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToStation(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore nel recupero della ChargingStation per ID", e);
        }
        return null;
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM charging_stations WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'eliminazione", e);
        }
    }

    @Override
    public List<ChargingStation> findByStatus(StationStatus status) {
        String sql = "SELECT * FROM charging_stations WHERE status = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            return executePreparedStatementToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca per status", e);
        }
    }

    @Override
    public List<ChargingStation> findByTransformer(Long transformerId) {
        String sql = "SELECT * FROM charging_stations WHERE transformer_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transformerId);
            return executePreparedStatementToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca per trasformatore", e);
        }
    }

    @Override
    public List<ChargingStation> findNearestAvailable(double lat, double lng, ConnectorType type, Long excludeId) {
        String sql = "SELECT * FROM charging_stations " +
                "WHERE status = 'ACTIVE' AND connector_type = ? AND id != ? " +
                "ORDER BY ((latitude - ?) * (latitude - ?) + (longitude - ?) * (longitude - ?)) ASC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, type.name());
            stmt.setLong(2, excludeId != null ? excludeId : -1L);
            stmt.setDouble(3, lat);
            stmt.setDouble(4, lat);
            stmt.setDouble(5, lng);
            stmt.setDouble(6, lng);
            return executePreparedStatementToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca stazioni vicine", e);
        }
    }

    @Override
    public void expireHolds() {
        String sql = "UPDATE charging_stations " +
                "SET status = 'ACTIVE', reserved_by_id = NULL, expiration_timestamp = NULL " +
                "WHERE status = 'RESERVED' AND expiration_timestamp < CURRENT_TIMESTAMP";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore scadenza prenotazioni", e);
        }
    }

    @Override
    public List<ChargingStation> findByOperator(Long operatorId) {
        String sql = "SELECT * FROM charging_stations WHERE operator_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, operatorId);
            // Riutilizziamo il metodo di supporto che fa già tutta la magia dei Proxy!
            return executePreparedStatementToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca stazioni per operatore", e);
        }
    }

    @Override
    public List<ChargingStation> findByConnectorType(ConnectorType type) {
        String sql = "SELECT * FROM charging_stations WHERE connector_type = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, type.name()); // Salviamo l'enum come Stringa
            return executePreparedStatementToList(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Errore ricerca stazioni per tipo di connettore", e);
        }
    }

    @Override
    public List<ChargingStation> findActive() {
        // Riutilizzo super-pulito: chiamiamo direttamente il metodo findByStatus
        // che abbiamo già implementato
        return findByStatus(StationStatus.ACTIVE);
    }

    @Override
    public List<ChargingStation> findAll() {
        List<ChargingStation> stations = new ArrayList<>();
        String sql = "SELECT * FROM charging_stations";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                stations.add(mapResultSetToStation(rs));
            }

        } catch (SQLException e) {
            // Usa il sistema di eccezioni custom del tuo progetto, o una RuntimeException
            throw new RuntimeException("Errore nel recupero di tutte le stazioni dal database", e);
        }

        return stations;
    }

    // --- METODI DI SUPPORTO ---

    private void setStationParameters(PreparedStatement stmt, ChargingStation station) throws SQLException {
        stmt.setLong(1, station.getOperator().getId());
        stmt.setLong(2, station.getTransformer().getId());
        stmt.setString(3, station.getName());
        stmt.setString(4, station.getAddress());
        stmt.setDouble(5, station.getLatitude());
        stmt.setDouble(6, station.getLongitude());
        stmt.setString(7, station.getConnectorType().name());
        stmt.setDouble(8, station.getPowerKw());
        stmt.setBoolean(9, station.getSolarPowered());
        stmt.setBigDecimal(10, station.getTariffOperator());
        stmt.setBigDecimal(11, station.getTariffPlatform());
        stmt.setDouble(12, station.getAverageRating());
        stmt.setInt(13, station.getTotalRatings());
        stmt.setString(14, station.getStatus().name());

        if (station.getReservedBy() != null) {
            stmt.setLong(15, station.getReservedBy().getId());
            stmt.setTimestamp(16, Timestamp.valueOf(station.getExpirationTimestamp()));
        } else {
            stmt.setNull(15, Types.BIGINT);
            stmt.setNull(16, Types.TIMESTAMP);
        }
    }

    private ChargingStation mapResultSetToStation(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");

        // --- LA MAGIA DEI PROXY ---
        // Istanziamento degli oggetti "leggeri" tramite l'ID recuperato dal DB
        StationOperator operator = StationOperator.reconstitute(rs.getLong("operator_id"));
        PowerTransformer transformer = PowerTransformer.reconstitute(rs.getLong("transformer_id"));

        Driver reservedBy = null;
        Long reservedById = rs.getLong("reserved_by_id");
        if (!rs.wasNull()) {
            reservedBy = Driver.reconstitute(reservedById);
        }
        // --------------------------

        String name = rs.getString("name");
        String address = rs.getString("address");
        double lat = rs.getDouble("latitude");
        double lng = rs.getDouble("longitude");
        ConnectorType connectorType = ConnectorType.valueOf(rs.getString("connector_type"));
        double powerKw = rs.getDouble("power_kw");
        boolean isSolar = rs.getBoolean("is_solar_powered");
        java.math.BigDecimal tariffOp = rs.getBigDecimal("tariff_operator");
        java.math.BigDecimal tariffPlat = rs.getBigDecimal("tariff_platform");
        double avgRating = rs.getDouble("average_rating");
        int totalRatings = rs.getInt("total_ratings");
        StationStatus status = StationStatus.valueOf(rs.getString("status"));

        Timestamp expirationTs = rs.getTimestamp("expiration_timestamp");
        java.time.LocalDateTime expiration = (expirationTs != null) ? expirationTs.toLocalDateTime() : null;

        // Ora passiamo gli Oggetti, non più i numeri!
        return ChargingStation.reconstitute(
                id, operator, transformer, name, address, lat, lng, connectorType,
                powerKw, isSolar, tariffOp, tariffPlat, avgRating, totalRatings,
                status, reservedBy, expiration
        );
    }

    private List<ChargingStation> executeQueryToList(String sql) {
        List<ChargingStation> list = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToStation(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore esecuzione query", e);
        }
        return list;
    }

    private List<ChargingStation> executePreparedStatementToList(PreparedStatement stmt) throws SQLException {
        List<ChargingStation> list = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToStation(rs));
            }
        }
        return list;
    }

    public boolean acquireAtomicHold(Long stationId, Long driverId) {
        String sql = "UPDATE charging_stations " +
                "SET status = 'RESERVED', reserved_by_id = ?, " +
                "expiration_timestamp = ? " + // Messo il punto interrogativo!
                "WHERE id = ? AND status = 'ACTIVE'";

        try (java.sql.PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, driverId);

            // Calcoliamo la scadenza in Java (+15 minuti da ora)
            java.time.LocalDateTime expiration = java.time.LocalDateTime.now().plusMinutes(15);
            stmt.setTimestamp(2, java.sql.Timestamp.valueOf(expiration));

            stmt.setLong(3, stationId);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected == 1;

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante l'acquireAtomicHold sulla stazione ID: " + stationId, e);
        }
    }
}