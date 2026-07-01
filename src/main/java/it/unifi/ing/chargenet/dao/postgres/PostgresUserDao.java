package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.domain.users.*;

import java.sql.Connection;
import java.util.List;
import java.sql.Types;

public class PostgresUserDao implements UserDao {

    private Connection connection;

    public PostgresUserDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void save(User entity) {

        String sql = "INSERT INTO users (name, latitude, longitude, email, password, role, wallet_balance, connector_type, subscription_plan, battery_capacity, total_earnings) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, entity.getName());
            pstmt.setString(4, entity.getEmail());
            pstmt.setString(5, entity.getPassword());
            pstmt.setString(6, entity.getRole().name());

            if (entity instanceof Driver) {

                Driver driver = (Driver) entity;

                if (driver.getLatitude() != null) pstmt.setDouble(2, driver.getLatitude()); else pstmt.setNull(2, java.sql.Types.DOUBLE);
                if (driver.getLongitude() != null) pstmt.setDouble(3, driver.getLongitude()); else pstmt.setNull(3, java.sql.Types.DOUBLE);
                pstmt.setBigDecimal(7, driver.getWalletBalance());

                if (driver.getConnectorType() != null) pstmt.setString(8, driver.getConnectorType().name()); else pstmt.setNull(8, java.sql.Types.VARCHAR);
                if (driver.getSubscriptionPlan() != null) pstmt.setString(9, driver.getSubscriptionPlan().name()); else pstmt.setNull(9, java.sql.Types.VARCHAR);

                if (driver.getBatteryCapacity() != null) pstmt.setDouble(10, driver.getBatteryCapacity()); else pstmt.setNull(10, java.sql.Types.DOUBLE);

                pstmt.setNull(11, java.sql.Types.DECIMAL);

            } else if (entity instanceof StationOperator) {

                StationOperator operator = (StationOperator) entity;

                pstmt.setNull(2, java.sql.Types.DOUBLE);
                pstmt.setNull(3, java.sql.Types.DOUBLE);
                pstmt.setNull(7, java.sql.Types.DECIMAL); // wallet_balance
                pstmt.setNull(8, java.sql.Types.VARCHAR);
                pstmt.setNull(9, java.sql.Types.VARCHAR);
                pstmt.setNull(10, java.sql.Types.DOUBLE);
                pstmt.setNull(11, Types.DECIMAL);

                if (operator.getTotalEarnings() != null) {
                    pstmt.setBigDecimal(11, operator.getTotalEarnings());
                } else {
                    pstmt.setBigDecimal(11, java.math.BigDecimal.ZERO);
                }

            } else {

                pstmt.setNull(2, java.sql.Types.DOUBLE);
                pstmt.setNull(3, java.sql.Types.DOUBLE);
                pstmt.setNull(7, java.sql.Types.DECIMAL);
                pstmt.setNull(8, java.sql.Types.VARCHAR);
                pstmt.setNull(9, java.sql.Types.VARCHAR);
                pstmt.setNull(10, java.sql.Types.DOUBLE);
                pstmt.setNull(11, java.sql.Types.DECIMAL);
            }

            pstmt.executeUpdate();

            try (java.sql.ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    entity.setId(generatedKeys.getLong(1));
                }
            }

            System.out.println("[PostgresUserDao] Inserito utente (" + entity.getClass().getSimpleName() + "): " + entity.getEmail() + " (ID: " + entity.getId() + ")");

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio dell'utente nel database", e);
        }
    }

    @Override
    public User findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractUserFromResultSet(rs);
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante la ricerca dell'utente per ID", e);
        }
        return null;
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        java.util.List<User> users = new java.util.ArrayList<>();

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql);
             java.sql.ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                User user = extractUserFromResultSet(rs);
                if (user != null) {
                    users.add(user);
                }
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il recupero di tutti gli utenti", e);
        }

        return users;
    }

    @Override
    public void update(User entity) {
        // Il tuo SessionService chiamerà questo per aggiornare il portafoglio!
        String sql = "UPDATE users SET name = ?, latitude = ?, longitude = ?, email = ?, password = ?, " +
                "role = ?, wallet_balance = ?, connector_type = ?, subscription_plan = ?, " +
                "battery_capacity = ?, total_earnings = ? WHERE id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // 1. Impostiamo i campi COMUNI ereditati da User
            pstmt.setString(1, entity.getName());

            // Di default impostiamo a NULL i campi specifici, verranno sovrascritti se l'istanza corrisponde
            pstmt.setNull(2, java.sql.Types.DOUBLE);  // latitude
            pstmt.setNull(3, java.sql.Types.DOUBLE);  // longitude
            pstmt.setString(4, entity.getEmail());
            pstmt.setString(5, entity.getPassword());
            pstmt.setString(6, entity.getRole().name());
            pstmt.setNull(7, java.sql.Types.NUMERIC); // wallet_balance
            pstmt.setNull(8, java.sql.Types.VARCHAR); // connector_type
            pstmt.setNull(9, java.sql.Types.VARCHAR); // subscription_plan
            pstmt.setNull(10, java.sql.Types.DOUBLE); // battery_capacity
            pstmt.setNull(11, java.sql.Types.NUMERIC); // total_earnings

            // 2. Controlliamo il tipo specifico dell'utente per mappare i campi extra
            if (entity instanceof Driver) {
                Driver driver = (Driver) entity;

                if (driver.getLatitude() != null) pstmt.setDouble(2, driver.getLatitude());
                if (driver.getLongitude() != null) pstmt.setDouble(3, driver.getLongitude());
                if (driver.getWalletBalance() != null) pstmt.setBigDecimal(7, driver.getWalletBalance());
                if (driver.getConnectorType() != null) pstmt.setString(8, driver.getConnectorType().name());
                if (driver.getSubscriptionPlan() != null) pstmt.setString(9, driver.getSubscriptionPlan().name());
                if (driver.getBatteryCapacity() != null) pstmt.setDouble(10, driver.getBatteryCapacity());

            } else if (entity instanceof StationOperator) {
                StationOperator operator = (StationOperator) entity;

                if (operator.getTotalEarnings() != null) pstmt.setBigDecimal(11, operator.getTotalEarnings());
            }
            // 3. Clausola WHERE: impostiamo l'ID per aggiornare SOLO questa riga
            pstmt.setLong(12, entity.getId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                System.out.println("[PostgresUserDao] Attenzione: Nessun utente aggiornato. L'ID " + entity.getId() + " potrebbe non esistere.");
            } else {
                System.out.println("[PostgresUserDao] Utente con ID " + entity.getId() + " aggiornato con successo nel DB.");
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante l'aggiornamento dell'utente con ID: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, id);

            // Eseguiamo la query e salviamo quante righe sono state eliminate
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                // Se restituisce 0, significa che l'ID non esisteva nel database
                System.out.println("[PostgresUserDao] Nessun utente trovato con ID " + id + ". Nessuna eliminazione effettuata.");
            } else {
                System.out.println("[PostgresUserDao] Utente con ID " + id + " eliminato con successo.");
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante l'eliminazione dell'utente con ID: " + id, e);
        }
    }

    // --- Metodi specifici di UserDao ---
    @Override
    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractUserFromResultSet(rs);
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante la ricerca dell'utente per email", e);
        }
        return null;
    }

    @Override
    public List<User> findByRole(Role role) {
        String sql = "SELECT * FROM users WHERE role = ?";
        java.util.List<User> users = new java.util.ArrayList<>();

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Passiamo il nome dell'enum come stringa (es. "DRIVER")
            pstmt.setString(1, role.name());

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    User user = extractUserFromResultSet(rs);
                    if (user != null) {
                        users.add(user);
                    }
                }
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante la ricerca degli utenti per ruolo: " + role.name(), e);
        }

        return users;
    }

    private User extractUserFromResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {
        Long id = rs.getLong("id");
        String name = rs.getString("name");
        String dbEmail = rs.getString("email");
        String password = rs.getString("password");
        Role role = Role.valueOf(rs.getString("role"));

        if (role == Role.DRIVER) {
            Double lat = rs.getDouble("latitude"); if(rs.wasNull()) lat = null;
            Double lon = rs.getDouble("longitude"); if(rs.wasNull()) lon = null;
            Double cap = rs.getDouble("battery_capacity"); if(rs.wasNull()) cap = null;

            String connTypeStr = rs.getString("connector_type");
            var cType = connTypeStr != null ? ConnectorType.valueOf(connTypeStr) : null;

            String subPlanStr = rs.getString("subscription_plan");
            var sPlan = subPlanStr != null ? SubscriptionPlan.valueOf(subPlanStr) : null;

            java.math.BigDecimal balance = rs.getBigDecimal("wallet_balance");
            if (balance == null) balance = java.math.BigDecimal.ZERO;

            return Driver.reconstitute(
                    id, name, dbEmail, password, lat, lon, cType, sPlan, cap, balance
            );

        } else if (role == Role.STATION_OPERATOR) {
            java.math.BigDecimal earnings = rs.getBigDecimal("total_earnings");
            if (earnings == null) earnings = java.math.BigDecimal.ZERO;

            return StationOperator.reconstitute(
                    id, name, dbEmail, password, earnings
            );

        } else if (role == Role.ENERGY_MANAGER) {
            return EnergyManager.reconstitute(
                    id, name, dbEmail, password
            );
        }
        return null;
    }
}
