package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus; // <-- Adatta il package se necessario
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresSessionDaoTest {

    private static Connection connection;
    private PostgresSessionDao sessionDao;

    // 1. SETUP H2
    @BeforeAll
    static void startDatabase() throws SQLException {
        String url = "jdbc:h2:mem:chargenet_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=RUNSCRIPT FROM './src/main/resources/scheme.sql'";
        connection = DriverManager.getConnection(url, "sa", "");
    }

    // 2. CHIUSURA
    @AfterAll
    static void stopDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    // 3. LA "CATENA DI MONTAGGIO" DEI DATI FINTI
    @BeforeEach
    void setUp() throws SQLException {
        sessionDao = new PostgresSessionDao(connection);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");

            stmt.execute("TRUNCATE TABLE charging_sessions RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE charging_stations RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE power_transformers RESTART IDENTITY");

            // Inseriamo l'Operatore (ID 1)
            stmt.execute("INSERT INTO users (id, name, email, password, role) " +
                    "VALUES (1, 'Op', 'op@test.com', 'pwd', 'STATION_OPERATOR')");

            // Inseriamo il Guidatore (ID 2)
            stmt.execute("INSERT INTO users (id, name, email, password, role) " +
                    "VALUES (2, 'Driver', 'driver@test.com', 'pwd', 'DRIVER')");

            // Inseriamo il Trasformatore (ID 1)
            stmt.execute("INSERT INTO power_transformers (id, name, temperature, load_percent) " +
                    "VALUES (1, 'Trans', 25.0, 0.0)");

            // Inseriamo la Stazione (ID 1)
            // CORREZIONE: Aggiunto 'address', 'tariff_operator' e 'tariff_platform' per evitare vincoli NOT NULL
            stmt.execute("INSERT INTO charging_stations " +
                    "(id, operator_id, transformer_id, name, address, status, connector_type, latitude, longitude, power_kw, is_solar_powered, tariff_operator, tariff_platform, average_rating, total_ratings) " +
                    "VALUES (1, 1, 1, 'Stazione Test', 'Via Roma 1', 'ACTIVE', 'TYPE_2', 0.0, 0.0, 50.0, false, 0.40, 0.05, 0.0, 0)");

            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    // --- METODO HELPER PER LE SESSIONI (Aggiornato a 11 parametri) ---
    private ChargingSession createMockSession(LocalDateTime openedAt) {
        Driver proxyDriver = Driver.reconstitute(2L);
        ChargingStation proxyStation = ChargingStation.reconstitute(1L);

        return ChargingSession.reconstitute(
                null,                  // 1. id (Generato dal DB)
                proxyDriver,           // 2. driver
                proxyStation,          // 3. station
                "FAST",                // 4. strategyUsed
                20.0,                  // 5. batteryStart (es. 20%)
                20.0,                  // 6. batteryCurrent
                0.0,                   // 7. kwhDelivered
                BigDecimal.ZERO,       // 8. costTotal
                SessionStatus.ACTIVE,  // 9. status
                openedAt,              // 10. openedAt
                null                   // 11. closedAt (null per sessione in corso)
        );
    }

    // --- I TEST ---

    @Test
    void testSaveAndFindById() {
        // ARRANGEMENT
        LocalDateTime now = LocalDateTime.now();
        ChargingSession session = createMockSession(now);

        // ACT
        sessionDao.save(session);

        // ASSERT
        assertNotNull(session.getId(), "L'ID della sessione deve essere autogenerato");

        ChargingSession retrieved = sessionDao.findById(session.getId());
        assertNotNull(retrieved);
        assertEquals(0.0, retrieved.getKwhDelivered());
        assertEquals("FAST", retrieved.getStrategyUsed());
        assertNull(retrieved.getClosedAt(), "La sessione non dovrebbe avere orario di fine");

        assertNotNull(retrieved.getDriver());
        assertEquals(2L, retrieved.getDriver().getId());
    }

    @Test
    void testUpdateSession() {
        // ARRANGEMENT
        ChargingSession session = createMockSession(LocalDateTime.now().minusHours(1));
        sessionDao.save(session);
        Long sessionId = session.getId();

        // ACT: Simuliamo la fine della ricarica usando i tuoi 11 parametri
        ChargingSession completedSession = ChargingSession.reconstitute(
                sessionId,                       // 1. id (lo stesso!)
                session.getDriver(),             // 2. driver
                session.getStation(),            // 3. station
                session.getStrategyUsed(),       // 4. strategyUsed
                session.getBatteryStart(),       // 5. batteryStart
                80.0,                            // 6. batteryCurrent (Aggiornato all'80%)
                35.5,                            // 7. kwhDelivered (Aggiornato)
                new BigDecimal("15.50"),         // 8. costTotal (Aggiornato)
                SessionStatus.COMPLETED,         // 9. status (Aggiornato a completato)
                session.getOpenedAt(),           // 10. openedAt
                LocalDateTime.now()              // 11. closedAt (Impostato a ora)
        );

        sessionDao.update(completedSession);

        // ASSERT
        ChargingSession retrieved = sessionDao.findById(sessionId);
        assertNotNull(retrieved.getClosedAt(), "L'orario di fine deve essere aggiornato");
        assertEquals(35.5, retrieved.getKwhDelivered());
        assertEquals(80.0, retrieved.getBatteryCurrent());
        assertEquals(SessionStatus.COMPLETED, retrieved.getStatus());
        assertEquals(new BigDecimal("15.50"), retrieved.getCostTotal());
    }

    @Test
    void testFindByDriver() {
        // ARRANGEMENT
        ChargingSession s1 = createMockSession(LocalDateTime.now().minusDays(2));
        ChargingSession s2 = createMockSession(LocalDateTime.now().minusDays(1));
        sessionDao.save(s1);
        sessionDao.save(s2);

        // ACT
        List<ChargingSession> driverSessions = sessionDao.findByDriver(2L);
        List<ChargingSession> otherSessions = sessionDao.findByDriver(99L);

        // ASSERT
        assertEquals(2, driverSessions.size(), "Deve trovare 2 sessioni");
        assertTrue(otherSessions.isEmpty(), "Non deve trovare sessioni per driver inesistente");
    }

    @Test
    void testFindActiveSessionByStation() {
        // ARRANGEMENT
        // 1 sessione conclusa
        ChargingSession completed = ChargingSession.reconstitute(
                null, Driver.reconstitute(2L), ChargingStation.reconstitute(1L),
                "ECO", 10.0, 100.0, 20.0, new BigDecimal("10.00"),
                SessionStatus.COMPLETED,
                LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(4)
        );
        // 1 sessione in corso
        ChargingSession active = createMockSession(LocalDateTime.now());

        sessionDao.save(completed);
        sessionDao.save(active);

        // ACT
        // Il DAO restituisce una lista di sessioni attive su quella stazione
        List<ChargingSession> activeSessions = sessionDao.findActiveByStation(1L);

        // ASSERT
        assertFalse(activeSessions.isEmpty(), "Deve trovare almeno una sessione attiva");
        assertEquals(1, activeSessions.size(), "Ci deve essere ESATTAMENTE una sessione attiva sulla colonnina");

        // Estraiamo l'unica sessione trovata e facciamo i controlli
        ChargingSession foundActive = activeSessions.get(0);
        assertNull(foundActive.getClosedAt(), "Deve recuperare la sessione non ancora chiusa");
        assertEquals(active.getId(), foundActive.getId(), "L'ID della sessione deve combaciare con quella in corso");
    }

    @Test
    void testFindAllAndDelete() {
        // ARRANGEMENT: Creiamo e salviamo due sessioni tramite il nostro helper
        ChargingSession s1 = createMockSession(LocalDateTime.now().minusHours(2));
        ChargingSession s2 = createMockSession(LocalDateTime.now().minusHours(1));
        sessionDao.save(s1);
        sessionDao.save(s2);

        // ACT 1: Testiamo findAll
        List<ChargingSession> allSessions = sessionDao.findAll();

        // ASSERT 1
        assertEquals(2, allSessions.size(), "Dovrebbero esserci esattamente 2 sessioni nel DB");

        // ACT 2: Testiamo delete eliminando la prima sessione
        sessionDao.delete(s1.getId());

        // ASSERT 2: Verifichiamo che l'eliminazione sia andata a buon fine
        List<ChargingSession> remainingSessions = sessionDao.findAll();
        assertEquals(1, remainingSessions.size(), "Dopo la delete dovrebbe rimanere 1 sola sessione");
        assertEquals(s2.getId(), remainingSessions.get(0).getId(), "La sessione rimasta deve essere la s2");

        // Verifichiamo che cercando l'ID eliminato il DAO gestisca correttamente la situazione (ritornando null)
        assertNull(sessionDao.findById(s1.getId()), "Cercando l'ID eliminato deve tornare null");
    }

    @Test
    void testFindActiveSessions() {
        // ARRANGEMENT: Inseriamo 2 sessioni in corso (ACTIVE) e 1 sessione terminata (COMPLETED)
        ChargingSession active1 = createMockSession(LocalDateTime.now().minusHours(1));
        ChargingSession active2 = createMockSession(LocalDateTime.now().minusMinutes(30));

        // Creiamo la sessione completata passando tutti gli 11 parametri
        ChargingSession completed = ChargingSession.reconstitute(
                null,
                Driver.reconstitute(2L),
                ChargingStation.reconstitute(1L),
                "ECO",
                20.0,
                80.0,
                30.0,
                new BigDecimal("12.00"),
                SessionStatus.COMPLETED, // <-- Stato COMPLETED
                LocalDateTime.now().minusHours(3),
                LocalDateTime.now().minusHours(2) // Data di chiusura presente
        );

        sessionDao.save(active1);
        sessionDao.save(active2);
        sessionDao.save(completed);

        // ACT: Richiamiamo il metodo che filtra solo le sessioni attive
        List<ChargingSession> activeSessions = sessionDao.findActiveSessions();

        // ASSERT
        assertEquals(2, activeSessions.size(), "Deve trovare solo le 2 sessioni con stato ACTIVE");

        // Verifica avanzata: ci assicuriamo che ogni elemento della lista sia effettivamente ACTIVE
        boolean allActive = activeSessions.stream()
                .allMatch(session -> session.getStatus() == SessionStatus.ACTIVE);
        assertTrue(allActive, "Tutte le sessioni restituite devono avere lo stato ACTIVE");
    }
}