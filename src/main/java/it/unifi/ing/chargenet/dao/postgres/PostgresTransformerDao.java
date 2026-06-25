package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.dao.interfaces.TransformerDao;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostgresTransformerDao implements TransformerDao {

    private final Connection connection;

    public PostgresTransformerDao(Connection connection) {
        this.connection = connection;
    }

    // --- METODI CRUD BASE ---

    @Override
    public void save(PowerTransformer transformer) {
        String sql = "INSERT INTO power_transformers (name, temperature, load_percent) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, transformer.getName());
            stmt.setDouble(2, transformer.getTemperature());
            stmt.setDouble(3, transformer.getLoadPercent());

            stmt.executeUpdate();

            // Recuperiamo l'ID generato automaticamente dal database (Postgres o H2)
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    // Assicurati che PowerTransformer abbia un metodo setId()
                    // o che tu possa impostarlo tramite reflection/costruttore
                    transformer.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio del PowerTransformer", e);
        }
    }

    @Override
    public void update(PowerTransformer transformer) {
        String sql = "UPDATE power_transformers SET name = ?, temperature = ?, load_percent = ? WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, transformer.getName());
            stmt.setDouble(2, transformer.getTemperature());
            stmt.setDouble(3, transformer.getLoadPercent());
            stmt.setLong(4, transformer.getId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'aggiornamento del PowerTransformer", e);
        }
    }

    @Override
    public PowerTransformer findById(Long id) {
        String sql = "SELECT * FROM power_transformers WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTransformer(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore nel recupero del PowerTransformer per ID", e);
        }
        return null; // Ritorna null se non lo trova
    }

    @Override
    public List<PowerTransformer> findAll() {
        String sql = "SELECT * FROM power_transformers";
        return executeQueryToList(sql);
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM power_transformers WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'eliminazione del PowerTransformer", e);
        }
    }

    // --- METODI DI RICERCA SPECIFICI ---

    @Override
    public List<PowerTransformer> findOverheated() {
        // Regola di Business inserita direttamente nella query:
        // Recupera solo i trasformatori con temperatura superiore a 90 gradi
        String sql = "SELECT * FROM power_transformers WHERE temperature > 90.0";
        return executeQueryToList(sql);
    }

    // --- METODI DI SUPPORTO (DRY - Don't Repeat Yourself) ---

    private PowerTransformer mapResultSetToTransformer(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String name = rs.getString("name");
        double temperature = rs.getDouble("temperature");
        double loadPercent = rs.getDouble("load_percent");

        // Richiama il metodo statico appena creato
        return PowerTransformer.reconstitute(id, name, temperature, loadPercent);
    }

    private List<PowerTransformer> executeQueryToList(String sql) {
        List<PowerTransformer> list = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToTransformer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore esecuzione query: " + sql, e);
        }
        return list;
    }
}