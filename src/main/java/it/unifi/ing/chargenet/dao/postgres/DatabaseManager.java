package it.unifi.ing.chargenet.dao.postgres;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DatabaseManager {

    private static final String URL =
            "jdbc:h2:mem:chargenet;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    /** Quota piattaforma applicata alle colonnine seed (coerente con insertStation). */
    private static final double TARIFF_PLATFORM = 0.05;

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

            seedData(conn);

        } catch (Exception e) {
            throw new RuntimeException("Errore inizializzazione schema DB", e);
        }
    }

    /**
     * Seed "a metà storia": utenti, trasformatori, un parco colonnine ampio,
     * colonnine in attesa di approvazione, alert di rating aperti, e uno storico
     * di ricariche/transazioni. Login: driver@ / operator@ / manager@chargenet.it (pwd "password").
     */
    private static void seedData(Connection conn) throws SQLException {
        String driverHash  = org.mindrot.jbcrypt.BCrypt.hashpw("password", org.mindrot.jbcrypt.BCrypt.gensalt());
        String opHash      = org.mindrot.jbcrypt.BCrypt.hashpw("password", org.mindrot.jbcrypt.BCrypt.gensalt());
        String managerHash = org.mindrot.jbcrypt.BCrypt.hashpw("password", org.mindrot.jbcrypt.BCrypt.gensalt());

        // --- Utenti (ordine preservato: driver=1, operatore principale=2, manager=3) ---
        long driverId  = insertDriver(conn, "Driver Demo", "driver@chargenet.it", driverHash,
                43.7696, 11.2558, new BigDecimal("200.00"), "CCS_2", "BASIC", 50.0);   // ⚠️ era TYPE_2
        long opDemo    = insertOperator(conn, "Operatore Demo", "operator@chargenet.it", opHash);
        long managerId = insertManager(conn, "Manager Demo", "manager@chargenet.it", managerHash);
        long opEnel    = insertOperator(conn, "EnelX Italia", "enelx@chargenet.it", opHash);
        long opA2a     = insertOperator(conn, "A2A Energy",   "a2a@chargenet.it",   opHash);

        // --- Trasformatori (4 per una Grid Dashboard ricca) ---
        long tCentro   = insertTransformer(conn, "Trafo Centro",   25.0, 0.0);
        long tNord     = insertTransformer(conn, "Trafo Nord",     25.0, 0.0);
        long tOltrarno = insertTransformer(conn, "Trafo Oltrarno", 25.0, 0.0);
        long tNovoli   = insertTransformer(conn, "Trafo Novoli",   25.0, 0.0);

        // --- Colonnine ACTIVE (maggioranza CCS_2 per il driver CCS_2; TYPE_2 sempre ≤ 22 kW) ---
        long stDuomo   = insertStation(conn, opDemo, tCentro,   "Firenze Duomo Hub",     "Piazza del Duomo 1",    43.7731, 11.2560, "CCS_2", 150.0, 0.40, 4.7, 42, "ACTIVE");
        long stSMN     = insertStation(conn, opDemo, tNord,     "Santa Maria Novella",   "Piazza Stazione 5",     43.7764, 11.2480, "CCS_2",  50.0, 0.35, 4.4, 30, "ACTIVE");
        long stOltra   = insertStation(conn, opDemo, tOltrarno, "Oltrarno Fast",         "Via de' Bardi 20",      43.7660, 11.2540, "TYPE_2", 22.0, 0.42, 4.1, 18, "ACTIVE");
        long stNovoli  = insertStation(conn, opDemo, tNovoli,   "Novoli Park & Charge",  "Via di Novoli 40",      43.7970, 11.2250, "CCS_2", 120.0, 0.38, 4.6, 25, "ACTIVE");
        long stMichel  = insertStation(conn, opEnel, tCentro,   "Piazzale Michelangelo", "Viale Michelangelo 1",  43.7629, 11.2650, "CCS_2", 150.0, 0.45, 4.8, 51, "ACTIVE");
        long stCampo   = insertStation(conn, opEnel, tNord,     "Campo di Marte",        "Viale Fanti 10",        43.7810, 11.2820, "CCS_2", 100.0, 0.41, 3.9, 22, "ACTIVE");
        long stCure    = insertStation(conn, opA2a,  tNovoli,   "Le Cure Charge",        "Via Faentina 80",       43.7930, 11.2720, "TYPE_2", 22.0, 0.39, 4.0, 12, "ACTIVE");
        long stIsol    = insertStation(conn, opDemo, tOltrarno, "Isolotto Hub",          "Via dell'Argingrosso 5",43.7690, 11.2110, "CCS_2",  60.0, 0.37, 4.3, 16, "ACTIVE");

        // --- Colonnine "problematiche" (media bassa + molti rating): generano gli alert ---
        long stBad1 = insertStation(conn, opDemo, tCentro, "San Frediano Charge", "Borgo San Frediano 12", 43.7710, 11.2430, "CCS_2", 80.0, 0.40, 1.4, 8, "ACTIVE");
        long stBad2 = insertStation(conn, opA2a,  tNord,   "Rovezzano Fast",      "Via di Rovezzano 3",    43.7720, 11.3130, "TYPE_2", 22.0, 0.43, 1.8, 6, "ACTIVE");

        // --- Colonnine in ATTESA di approvazione (Approval Queue del Manager) ---
        insertStation(conn, opEnel, tCentro,   "Rifredi Supercharge", "Via Vittorio Emanuele 200", 43.7980, 11.2450, "CCS_2", 180.0, 0.46, 0.0, 0, "PENDING_VERIFICATION");
        insertStation(conn, opA2a,  tNord,     "Coverciano Point",    "Via di Coverciano 15",      43.7790, 11.3000, "TYPE_2", 22.0, 0.40, 0.0, 0, "PENDING_VERIFICATION");
        insertStation(conn, opDemo, tOltrarno, "Gavinana Fast Hub",   "Piazza Gavinana 8",         43.7590, 11.2760, "CCS_2", 120.0, 0.39, 0.0, 0, "PENDING_VERIFICATION");

        // --- Alert di rating APERTI (Rating Incident Manager): 1 CRITICAL (<1.5), 1 WARNING ---
        seedRatingAlert(conn, stBad1, 1.4, 3);    // 3 ore fa
        seedRatingAlert(conn, stBad2, 1.8, 26);   // ~1 giorno fa

        // --- Storico ricariche del driver (Charging History + dashboard operatore) ---
        seedHistoricalSession(conn, driverId, stDuomo,  "FAST", 32.0, 0.40,  1);
        seedHistoricalSession(conn, driverId, stNovoli, "ECO",  28.0, 0.38,  3);
        seedHistoricalSession(conn, driverId, stMichel, "FAST", 40.0, 0.45,  5);
        seedHistoricalSession(conn, driverId, stSMN,    "FAST", 18.0, 0.35,  8);
        seedHistoricalSession(conn, driverId, stOltra,  "ECO",  12.0, 0.42, 12);
        seedHistoricalSession(conn, driverId, stCampo,  "FAST", 35.0, 0.41, 18);
        seedHistoricalSession(conn, driverId, stIsol,   "ECO",  22.0, 0.37, 25);
        seedHistoricalSession(conn, driverId, stDuomo,  "FAST", 26.0, 0.40, 30);

        // --- Qualche transazione non-ricarica per varietà (portafoglio / abbonamento) ---
        seedTransaction(conn, driverId, "FUND_ADDED",   new BigDecimal("100.00"), 0.0, "Ricarica portafoglio", 15);
        seedTransaction(conn, driverId, "FUND_ADDED",   new BigDecimal("50.00"),  0.0, "Ricarica portafoglio", 30);
        seedTransaction(conn, driverId, "SUBSCRIPTION", new BigDecimal("-9.00"),  0.0, "Attivazione piano PLUS", 20);

        // --- total_earnings di OGNI operatore, derivato dalle sessioni (nessun calcolo a mano) ---
        try (Statement st = conn.createStatement()) {
            st.execute(
                    "UPDATE users SET total_earnings = COALESCE((" +
                            "  SELECT SUM(cs.kwh_delivered * s.tariff_operator) " +
                            "  FROM charging_sessions cs " +
                            "  JOIN charging_stations s ON cs.station_id = s.id " +
                            "  WHERE s.operator_id = users.id AND cs.status = 'COMPLETED'" +
                            "), 0) WHERE role = 'STATION_OPERATOR'");
        }

        System.out.println("[DatabaseManager] Seed ricco inserito. Login: driver@/operator@/manager@chargenet.it, pwd 'password'.");
    }

    // ==========================================================================
    //  HELPER (tutti restituiscono l'id generato dove serve una referenza)
    // ==========================================================================

    private static long insertDriver(Connection conn, String name, String email, String hash,
                                     double lat, double lng, BigDecimal wallet, String connector,
                                     String plan, double battery) throws SQLException {
        String sql = "INSERT INTO users (name, email, password, role, latitude, longitude, " +
                "wallet_balance, connector_type, subscription_plan, battery_capacity) " +
                "VALUES (?, ?, ?, 'DRIVER', ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, hash);
            ps.setDouble(4, lat);
            ps.setDouble(5, lng);
            ps.setBigDecimal(6, wallet);
            ps.setString(7, connector);
            ps.setString(8, plan);
            ps.setDouble(9, battery);
            return executeReturningId(ps);
        }
    }

    private static long insertOperator(Connection conn, String name, String email, String hash) throws SQLException {
        String sql = "INSERT INTO users (name, email, password, role, total_earnings) " +
                "VALUES (?, ?, ?, 'STATION_OPERATOR', 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, hash);
            return executeReturningId(ps);
        }
    }

    private static long insertManager(Connection conn, String name, String email, String hash) throws SQLException {
        String sql = "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, 'ENERGY_MANAGER')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, hash);
            return executeReturningId(ps);
        }
    }

    private static long insertTransformer(Connection conn, String name, double temp, double load) throws SQLException {
        String sql = "INSERT INTO power_transformers (name, temperature, load_percent) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setDouble(2, temp);
            ps.setDouble(3, load);
            return executeReturningId(ps);
        }
    }

    private static long insertStation(Connection conn, long operatorId, long transformerId,
                                      String name, String address, double lat, double lng,
                                      String connector, double powerKw, double tariffOp,
                                      double avgRating, int totalRatings, String status) throws SQLException {
        String sql = "INSERT INTO charging_stations " +
                "(operator_id, transformer_id, name, address, latitude, longitude, connector_type, " +
                " power_kw, is_solar_powered, tariff_operator, tariff_platform, average_rating, total_ratings, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, operatorId);
            ps.setLong(2, transformerId);
            ps.setString(3, name);
            ps.setString(4, address);
            ps.setDouble(5, lat);
            ps.setDouble(6, lng);
            ps.setString(7, connector);
            ps.setDouble(8, powerKw);
            ps.setBoolean(9, false);
            ps.setBigDecimal(10, BigDecimal.valueOf(tariffOp));
            ps.setBigDecimal(11, BigDecimal.valueOf(TARIFF_PLATFORM));
            ps.setDouble(12, avgRating);
            ps.setInt(13, totalRatings);
            ps.setString(14, status);
            return executeReturningId(ps);
        }
    }

    /** Sessione COMPLETED storica + relativa Transaction CHARGE. Costo = kwh × (tariffOp + platform). */
    private static void seedHistoricalSession(Connection conn, long driverId, long stationId,
                                              String strategy, double kwh, double tariffOp,
                                              int daysAgo) throws SQLException {
        BigDecimal cost = BigDecimal.valueOf(kwh)
                .multiply(BigDecimal.valueOf(tariffOp).add(BigDecimal.valueOf(TARIFF_PLATFORM)))
                .setScale(2, RoundingMode.HALF_UP);

        double batteryStart = 15.0;
        double batteryEnd = Math.min(100.0, batteryStart + (kwh / 50.0) * 100.0);

        java.time.LocalDateTime opened = java.time.LocalDateTime.now().minusDays(daysAgo);
        java.time.LocalDateTime closed = opened.plusMinutes(45);

        String sessionSql = "INSERT INTO charging_sessions (driver_id, station_id, strategy_used, " +
                "battery_start, battery_current, kwh_delivered, cost_total, status, opened_at, closed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sessionSql)) {
            ps.setLong(1, driverId);
            ps.setLong(2, stationId);
            ps.setString(3, strategy);
            ps.setDouble(4, batteryStart);
            ps.setDouble(5, batteryEnd);
            ps.setDouble(6, kwh);
            ps.setBigDecimal(7, cost);
            ps.setTimestamp(8, java.sql.Timestamp.valueOf(opened));
            ps.setTimestamp(9, java.sql.Timestamp.valueOf(closed));
            ps.executeUpdate();
        }

        seedTransaction(conn, driverId, "CHARGE", cost, kwh, "Ricarica presso colonnina", daysAgo);
    }

    private static void seedTransaction(Connection conn, long driverId, String type, BigDecimal amount,
                                        double kwh, String description, int daysAgo) throws SQLException {
        String sql = "INSERT INTO transactions (driver_id, type, amount, kwh, description, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, driverId);
            ps.setString(2, type);
            ps.setBigDecimal(3, amount);
            ps.setDouble(4, kwh);
            ps.setString(5, description);
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(daysAgo)));
            ps.executeUpdate();
        }
    }

    /** Alert di rating APERTO su una colonnina (Rating Incident Manager). */
    private static void seedRatingAlert(Connection conn, long stationId, double avgAtCreation,
                                        int hoursAgo) throws SQLException {
        String sql = "INSERT INTO rating_alerts (station_id, avg_at_creation, status, manager_note, created_at) " +
                "VALUES (?, ?, 'PENDING', NULL, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, stationId);
            ps.setDouble(2, avgAtCreation);
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(hoursAgo)));
            ps.executeUpdate();
        }
    }

    private static long executeReturningId(PreparedStatement ps) throws SQLException {
        ps.executeUpdate();
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) return keys.getLong(1);
        }
        throw new SQLException("Nessun id generato dall'insert");
    }
}