package it.unifi.ing.chargenet.dao.postgres;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    // Nome COERENTE con la console. Niente INIT=RUNSCRIPT!
    // DB_CLOSE_DELAY=-1 tiene vivo il mem-DB per tutta la JVM.
    private static final String URL =
            "jdbc:h2:mem:chargenet;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private DatabaseManager() {}

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("Errore critico di connessione al database H2", e);
        }
    }

    /** Crea lo schema UNA SOLA VOLTA all'avvio, poi inserisce i dati di seed. */
    public static void initializeSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             InputStream is = DatabaseManager.class.getResourceAsStream("/scheme.sql")) {

            if (is == null) {
                throw new RuntimeException(
                        "scheme.sql non trovato nel classpath (atteso in src/main/resources/scheme.sql)");
            }
            String script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (String s : script.split(";")) {
                if (!s.trim().isEmpty()) stmt.execute(s);
            }
            System.out.println("[DatabaseManager] Schema inizializzato.");

            seedData(conn);   // <-- popola i dati iniziali

        } catch (Exception e) {
            throw new RuntimeException("Errore inizializzazione schema DB", e);
        }
    }

    /**
     * Inserisce dati iniziali per poter testare l'app senza registrare tutto a mano:
     * 1 operatore, 1 energy manager, 2 trasformatori, alcune colonnine ACTIVE.
     * Le password sono hashate con BCrypt così si può fare login (operator/manager: "password").
     */
    private static void seedData(Connection conn) throws SQLException {
        String opHash      = org.mindrot.jbcrypt.BCrypt.hashpw("password", org.mindrot.jbcrypt.BCrypt.gensalt());
        String managerHash = org.mindrot.jbcrypt.BCrypt.hashpw("password", org.mindrot.jbcrypt.BCrypt.gensalt());

        // --- Utenti tecnici ---
        String userSql = "INSERT INTO users (name, email, password, role, total_earnings) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(userSql)) {
            ps.setString(1, "Operatore Demo");
            ps.setString(2, "operator@chargenet.it");
            ps.setString(3, opHash);
            ps.setString(4, "STATION_OPERATOR");
            ps.setBigDecimal(5, java.math.BigDecimal.ZERO);
            ps.executeUpdate();
        }
        String mgrSql = "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(mgrSql)) {
            ps.setString(1, "Manager Demo");
            ps.setString(2, "manager@chargenet.it");
            ps.setString(3, managerHash);
            ps.setString(4, "ENERGY_MANAGER");
            ps.executeUpdate();
        }

        // --- Trasformatori ---
        String tSql = "INSERT INTO power_transformers (name, temperature, load_percent) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(tSql)) {
            ps.setString(1, "Trafo Centro"); ps.setDouble(2, 25.0); ps.setDouble(3, 0.0); ps.executeUpdate();
            ps.setString(1, "Trafo Nord");   ps.setDouble(2, 25.0); ps.setDouble(3, 0.0); ps.executeUpdate();
        }

        // --- Colonnine ACTIVE (operator_id=1; transformer_id 1/2) ---
        insertStation(conn, 1, 1, "Firenze Duomo Hub",   "Piazza del Duomo 1", 43.7731, 11.2560, "TYPE_2", 150.0, 0.40);
        insertStation(conn, 1, 2, "Santa Maria Novella", "Piazza Stazione 5",  43.7764, 11.2480, "CCS_2",   50.0, 0.35);
        insertStation(conn, 1, 1, "Oltrarno Fast",       "Via de' Bardi 20",   43.7660, 11.2540, "TYPE_2", 120.0, 0.42);

        System.out.println("[DatabaseManager] Seed inserito (operator@chargenet.it / manager@chargenet.it, pwd: 'password').");
    }

    private static void insertStation(Connection conn, long operatorId, long transformerId,
                                      String name, String address, double lat, double lng,
                                      String connector, double powerKw, double tariffOp) throws SQLException {
        String sql = "INSERT INTO charging_stations " +
                "(operator_id, transformer_id, name, address, latitude, longitude, connector_type, " +
                " power_kw, is_solar_powered, tariff_operator, tariff_platform, average_rating, total_ratings, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, operatorId);
            ps.setLong(2, transformerId);
            ps.setString(3, name);
            ps.setString(4, address);
            ps.setDouble(5, lat);
            ps.setDouble(6, lng);
            ps.setString(7, connector);
            ps.setDouble(8, powerKw);
            ps.setBoolean(9, false);
            ps.setBigDecimal(10, java.math.BigDecimal.valueOf(tariffOp));
            ps.setBigDecimal(11, java.math.BigDecimal.valueOf(0.05));
            ps.setDouble(12, 0.0);
            ps.setInt(13, 0);
            ps.setString(14, "ACTIVE");
            ps.executeUpdate();
        }
    }
}