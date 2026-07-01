package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus;
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

            // Inseriamo la Stazione (ID 1) associata all'Operatore 1
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
                ChargingType.FAST,                // 4. strategyUsed
                20.0,                  // 5. batteryStart (es. 20%)
                20.0,                  // 6. batteryCurrent
                0.0,                   // 7. kwhDelivered
                BigDecimal.ZERO,       // 8. costTotal
                SessionStatus.ACTIVE,  // 9. status
                openedAt,              // 10. openedAt
                null                   // 11. closedAt (null per sessione in corso)
        );
    }

    // --- I TEST ESISTENTENTI ---

    @Test
    void testSaveAndFindById() {
        LocalDateTime now = LocalDateTime.now();
        ChargingSession session = createMockSession(now);

        sessionDao.save(session);

        assertNotNull(session.getId(), "L'ID della sessione deve essere autogenerato");
        ChargingSession retrieved = sessionDao.findById(session.getId());
        assertNotNull(retrieved);
        assertEquals(0.0, retrieved.getKwhDelivered());
        assertEquals(ChargingType.FAST, retrieved.getStrategyUsed());
        assertNull(retrieved.getClosedAt(), "La sessione non dovrebbe avere orario di fine");
        assertNotNull(retrieved.getDriver());
        assertEquals(2L, retrieved.getDriver().getId());
    }

    @Test
    void testUpdateSession() {
        ChargingSession session = createMockSession(LocalDateTime.now().minusHours(1));
        sessionDao.save(session);
        Long sessionId = session.getId();

        ChargingSession completedSession = ChargingSession.reconstitute(
                sessionId,
                session.getDriver(),
                session.getStation(),
                session.getStrategyUsed(),
                session.getBatteryStart(),
                80.0,
                35.5,
                new BigDecimal("15.50"),
                SessionStatus.COMPLETED,
                session.getOpenedAt(),
                LocalDateTime.now()
        );

        sessionDao.update(completedSession);

        ChargingSession retrieved = sessionDao.findById(sessionId);
        assertNotNull(retrieved.getClosedAt(), "L'orario di fine deve essere aggiornato");
        assertEquals(35.5, retrieved.getKwhDelivered());
        assertEquals(80.0, retrieved.getBatteryCurrent());
        assertEquals(SessionStatus.COMPLETED, retrieved.getStatus());
        assertEquals(new BigDecimal("15.50"), retrieved.getCostTotal());
    }

    @Test
    void testFindByDriver() {
        ChargingSession s1 = createMockSession(LocalDateTime.now().minusDays(2));
        ChargingSession s2 = createMockSession(LocalDateTime.now().minusDays(1));
        sessionDao.save(s1);
        sessionDao.save(s2);

        List<ChargingSession> driverSessions = sessionDao.findByDriver(2L);
        List<ChargingSession> otherSessions = sessionDao.findByDriver(99L);

        assertEquals(2, driverSessions.size(), "Deve trovare 2 sessioni");
        assertTrue(otherSessions.isEmpty(), "Non deve trovare sessioni per driver inesistente");
    }

    @Test
    void testFindActiveSessionByStation() {
        ChargingSession completed = ChargingSession.reconstitute(
                null, Driver.reconstitute(2L), ChargingStation.reconstitute(1L),
                ChargingType.ECO, 10.0, 100.0, 20.0, new BigDecimal("10.00"),
                SessionStatus.COMPLETED,
                LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(4)
        );
        ChargingSession active = createMockSession(LocalDateTime.now());

        sessionDao.save(completed);
        sessionDao.save(active);

        List<ChargingSession> activeSessions = sessionDao.findActiveByStation(1L);

        assertFalse(activeSessions.isEmpty(), "Deve trovare almeno una sessione attiva");
        assertEquals(1, activeSessions.size(), "Ci deve essere ESATTAMENTE una sessione attiva sulla colonnina");

        ChargingSession foundActive = activeSessions.get(0);
        assertNull(foundActive.getClosedAt(), "Deve recuperare la sessione non ancora chiusa");
        assertEquals(active.getId(), foundActive.getId(), "L'ID della sessione deve combaciare con quella in corso");
    }

    @Test
    void testFindAllAndDelete() {
        ChargingSession s1 = createMockSession(LocalDateTime.now().minusHours(2));
        ChargingSession s2 = createMockSession(LocalDateTime.now().minusHours(1));
        sessionDao.save(s1);
        sessionDao.save(s2);

        List<ChargingSession> allSessions = sessionDao.findAll();
        assertEquals(2, allSessions.size(), "Dovrebbero esserci esattamente 2 sessioni nel DB");

        sessionDao.delete(s1.getId());

        List<ChargingSession> remainingSessions = sessionDao.findAll();
        assertEquals(1, remainingSessions.size(), "Dopo la delete dovrebbe rimanere 1 sola sessione");
        assertEquals(s2.getId(), remainingSessions.get(0).getId(), "La sessione rimasta deve essere la s2");
        assertNull(sessionDao.findById(s1.getId()), "Cercando l'ID eliminato deve tornare null");
    }

    @Test
    void testFindActiveSessions() {
        ChargingSession active1 = createMockSession(LocalDateTime.now().minusHours(1));
        ChargingSession active2 = createMockSession(LocalDateTime.now().minusMinutes(30));

        ChargingSession completed = ChargingSession.reconstitute(
                null,
                Driver.reconstitute(2L),
                ChargingStation.reconstitute(1L),
                ChargingType.ECO,
                20.0,
                80.0,
                30.0,
                new BigDecimal("12.00"),
                SessionStatus.COMPLETED,
                LocalDateTime.now().minusHours(3),
                LocalDateTime.now().minusHours(2)
        );

        sessionDao.save(active1);
        sessionDao.save(active2);
        sessionDao.save(completed);

        List<ChargingSession> activeSessions = sessionDao.findActiveSessions();

        assertEquals(2, activeSessions.size(), "Deve trovare solo le 2 sessioni con stato ACTIVE");
        boolean allActive = activeSessions.stream()
                .allMatch(session -> session.getStatus() == SessionStatus.ACTIVE);
        assertTrue(allActive, "Tutte le sessioni restituite devono avere lo stato ACTIVE");
    }

    // =========================================================================
    // --- NUOVI TEST PER I METODI AGGIUNTI (UC Lettura UI) ---
    // =========================================================================

    @Test
    void testFindActiveByDriverId() {
        ChargingSession active = createMockSession(LocalDateTime.now());
        sessionDao.save(active);

        ChargingSession found = sessionDao.findActiveByDriverId(2L);
        ChargingSession notFound = sessionDao.findActiveByDriverId(99L);

        assertNotNull(found, "Deve trovare la sessione in corso per il guidatore specificato");
        assertEquals(SessionStatus.ACTIVE, found.getStatus(), "La sessione recuperata deve essere attiva");
        assertEquals(2L, found.getDriver().getId(), "L'ID del guidatore deve corrispondere");

        assertNull(notFound, "Deve tornare null se il guidatore non ha sessioni in corso o non esiste");
    }

    @Test
    void testFindCompletedByDriverId() {
        ChargingSession completed1 = ChargingSession.reconstitute(
                null, Driver.reconstitute(2L), ChargingStation.reconstitute(1L),
                ChargingType.ECO, 20.0, 80.0, 30.0, new BigDecimal("12.00"),
                SessionStatus.COMPLETED,
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(2)
        );
        ChargingSession completed2 = ChargingSession.reconstitute(
                null, Driver.reconstitute(2L), ChargingStation.reconstitute(1L),
                ChargingType.FAST, 20.0, 80.0, 40.0, new BigDecimal("18.00"),
                SessionStatus.COMPLETED,
                LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(4)
        );
        ChargingSession active = createMockSession(LocalDateTime.now());

        sessionDao.save(completed1);
        sessionDao.save(completed2);
        sessionDao.save(active); // Non deve essere restituita

        List<ChargingSession> completedSessions = sessionDao.findCompletedByDriverId(2L);

        assertEquals(2, completedSessions.size(), "Deve trovare solo le 2 sessioni completate per il driver");
        assertTrue(completedSessions.stream().allMatch(s -> s.getStatus() == SessionStatus.COMPLETED), "Tutti gli elementi della lista devono essere in stato COMPLETED");
    }

    @Test
    void testCountByOperatorId() {
        // La Stazione 1 è associata all'Operatore 1 nel setUp()
        ChargingSession s1 = createMockSession(LocalDateTime.now().minusDays(1));
        ChargingSession s2 = createMockSession(LocalDateTime.now().minusHours(2));
        sessionDao.save(s1);
        sessionDao.save(s2);

        int countOp1 = sessionDao.countByOperatorId(1L);
        int countEmptyOp = sessionDao.countByOperatorId(99L);

        assertEquals(2, countOp1, "Deve contare correttamente 2 sessioni effettuate sulle stazioni dell'operatore 1");
        assertEquals(0, countEmptyOp, "Deve restituire 0 se l'operatore non esiste o non ha sessioni storiche");
    }

    @Test
    void testSumEnergyByOperatorId() {
        // Creiamo sessioni con dati di energia precisi per la Stazione 1 (Operatore 1)
        ChargingSession completed1 = ChargingSession.reconstitute(
                null, Driver.reconstitute(2L), ChargingStation.reconstitute(1L),
                ChargingType.FAST, 10.0, 80.0, 50.5, new BigDecimal("20.00"), // Erogati 50.5 kWh
                SessionStatus.COMPLETED,
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(2)
        );
        ChargingSession completed2 = ChargingSession.reconstitute(
                null, Driver.reconstitute(2L), ChargingStation.reconstitute(1L),
                ChargingType.ECO, 50.0, 100.0, 20.0, new BigDecimal("8.00"), // Erogati 20.0 kWh
                SessionStatus.COMPLETED,
                LocalDateTime.now().minusHours(6), LocalDateTime.now().minusHours(4)
        );

        sessionDao.save(completed1);
        sessionDao.save(completed2);

        double totalEnergy = sessionDao.sumEnergyByOperatorId(1L);
        double emptyEnergy = sessionDao.sumEnergyByOperatorId(99L);

        // Assert con una piccola tolleranza per i calcoli in virgola mobile (0.001)
        assertEquals(70.5, totalEnergy, 0.001, "La somma dell'energia erogata deve essere 50.5 + 20.0 = 70.5 kWh");
        assertEquals(0.0, emptyEnergy, 0.001, "Se l'operatore non ha erogato energia, il COALESCE della query deve garantire 0.0");
    }
}