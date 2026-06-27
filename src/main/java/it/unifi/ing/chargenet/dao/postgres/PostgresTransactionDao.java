package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
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

        String sql = "INSERT INTO transactions (driver_id, type, amount, kwh, description, created_at) VALUES (?, ?, ?, ?, ?, ?)";

        // Usiamo RETURN_GENERATED_KEYS per farci restituire l'ID generato dal database (utile se ci serve subito dopo)
        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, entity.getDriver().getId());
            pstmt.setString(2, entity.getType().name());
            pstmt.setBigDecimal(3, entity.getAmount());

            // Il campo kwh è un oggetto Double, potrebbe teoricamente essere null (es. se è solo una ricarica del conto e non dell'auto)
            if (entity.getKwh() != null) {
                pstmt.setDouble(4, entity.getKwh());
            } else {
                pstmt.setDouble(4, 0.0); // Valore di default come da database
            }

            pstmt.setString(5, entity.getDescription());

            // Convertiamo LocalDateTime in java.sql.Timestamp
            pstmt.setTimestamp(6, java.sql.Timestamp.valueOf(entity.getCreatedAt()));

            pstmt.executeUpdate();

            try (java.sql.ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    entity.setId(generatedKeys.getLong(1));
                }
            }

            System.out.println("[PostgresTransactionDao] Transazione di " + entity.getAmount() + "€ salvata con successo per il driver ID: " + entity.getDriver().getId());

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio della transazione", e);
        }
    }

    @Override
    public Transaction findById(Long id) {
        // La query esegue la JOIN esplicita tra le due tabelle usando gli alias per i campi id e type
        String sql = "SELECT t.id AS transaction_id, t.type AS transaction_type, t.amount, t.kwh, t.description, t.created_at, " +
                "u.id AS user_id, u.name, u.email, u.password, u.role, u.latitude, u.longitude, " +
                "u.connector_type, u.subscription_plan, u.battery_capacity, u.wallet_balance " +
                "FROM transactions t " +
                "JOIN users u ON t.driver_id = u.id " +
                "WHERE t.id = ?";

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, id);

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Sfruttiamo il nostro helper privato per fare tutta la magia
                    return extractTransactionFromResultSet(rs);
                }
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante la ricerca della transazione con ID: " + id, e);
        }

        return null; // Ritorna null se la transazione con quell'ID non esiste
    }

    @Override
    public List<Transaction> findAll() {
        String sql = "SELECT t.id AS transaction_id, t.type AS transaction_type, t.amount, t.kwh, t.description, t.created_at, " +
                "u.id AS user_id, u.name, u.email, u.password, u.role, u.latitude, u.longitude, " +
                "u.connector_type, u.subscription_plan, u.battery_capacity, u.wallet_balance " +
                "FROM transactions t " +
                "JOIN users u ON t.driver_id = u.id " +
                "ORDER BY t.created_at DESC";

        java.util.List<Transaction> transactions = new java.util.ArrayList<>();

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql);
             java.sql.ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                transactions.add(extractTransactionFromResultSet(rs));
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il recupero di tutte le transazioni", e);
        }

        return transactions;
    }

    @Override
    public void update(Transaction entity) {
        throw new UnsupportedOperationException("Sicurezza: Le Transazioni sono immutabili e non possono essere modificate");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Sicurezza: Le Transazioni sono immutabili e non possono essere modificate");
    }
    public java.util.List<Transaction> findByDriver(Long driverId) {

        // Usiamo la JOIN per avere i dati utente e filtriamo per driver_id.
        // Infine ordiniamo in modo decrescente (DESC) per avere le transazioni più recenti per prime.
        String sql = "SELECT t.id AS transaction_id, t.type AS transaction_type, t.amount, t.kwh, t.description, t.created_at, " +
                "u.id AS user_id, u.name, u.email, u.password, u.role, u.latitude, u.longitude, " +
                "u.connector_type, u.subscription_plan, u.battery_capacity, u.wallet_balance " +
                "FROM transactions t " +
                "JOIN users u ON t.driver_id = u.id " +
                "WHERE t.driver_id = ? " +
                "ORDER BY t.created_at DESC";

        java.util.List<Transaction> transactions = new java.util.ArrayList<>();

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Impostiamo l'ID dell'utente che vogliamo cercare
            pstmt.setLong(1, driverId);

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // Costruiamo e aggiungiamo la singola transazione alla lista
                    transactions.add(extractTransactionFromResultSet(rs));
                }
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il recupero dello storico transazioni per il driver ID: " + driverId, e);
        }

        return transactions;
    }

    public java.util.List<Transaction> findByType(it.unifi.ing.chargenet.domain.financials.TransactionType type) {

        // Filtriamo per tipo e manteniamo l'ordinamento decrescente (più recenti prima)
        String sql = "SELECT t.id AS transaction_id, t.type AS transaction_type, t.amount, t.kwh, t.description, t.created_at, " +
                "u.id AS user_id, u.name, u.email, u.password, u.role, u.latitude, u.longitude, " +
                "u.connector_type, u.subscription_plan, u.battery_capacity, u.wallet_balance " +
                "FROM transactions t " +
                "JOIN users u ON t.driver_id = u.id " +
                "WHERE t.type = ? " +
                "ORDER BY t.created_at DESC";

        java.util.List<Transaction> transactions = new java.util.ArrayList<>();

        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Convertiamo l'enum in stringa (es. "CHARGE", "REFUND", "TOP_UP")
            pstmt.setString(1, type.name());

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(extractTransactionFromResultSet(rs));
                }
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Errore durante il recupero delle transazioni per il tipo: " + type.name(), e);
        }

        return transactions;
    }

    private Transaction extractTransactionFromResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {

        // 1. Estraiamo i dati della Transazione
        Long tId = rs.getLong("transaction_id");
        String typeStr = rs.getString("transaction_type");
        it.unifi.ing.chargenet.domain.financials.TransactionType tType =
                it.unifi.ing.chargenet.domain.financials.TransactionType.valueOf(typeStr);
        java.math.BigDecimal amount = rs.getBigDecimal("amount");
        Double kwh = rs.getDouble("kwh");
        String description = rs.getString("description");
        java.time.LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();

        // 2. Estraiamo i dati del Driver (dalla JOIN con la tabella users)
        Long uId = rs.getLong("user_id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String password = rs.getString("password");

        Double lat = rs.getDouble("latitude"); if(rs.wasNull()) lat = null;
        Double lon = rs.getDouble("longitude"); if(rs.wasNull()) lon = null;
        Double cap = rs.getDouble("battery_capacity"); if(rs.wasNull()) cap = null;

        String connTypeStr = rs.getString("connector_type");
        var cType = connTypeStr != null ? ConnectorType.valueOf(connTypeStr) : null;

        String subPlanStr = rs.getString("subscription_plan");
        var sPlan = subPlanStr != null ? SubscriptionPlan.valueOf(subPlanStr) : null;

        java.math.BigDecimal balance = rs.getBigDecimal("wallet_balance");
        if (balance == null) balance = java.math.BigDecimal.ZERO;

        // 3. Ricostruiamo prima l'oggetto Driver
        Driver driver = Driver.reconstitute(
                uId, name, email, password, lat, lon, cType, sPlan, cap, balance
        );

        // 4. Infine, ricostruiamo e restituiamo la Transaction
        return Transaction.reconstitute(
                tId, driver, tType, amount, kwh, description, createdAt
        );
    }
}