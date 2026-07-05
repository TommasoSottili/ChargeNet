package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresStationDaoTest {

    private static Connection connection;
    private PostgresStationDao stationDao;

    // 1. SETUP DEL DATABASE H2
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

    // 3. PULIZIA E PREPARAZIONE DATI FITTIZI (Mocking su DB)
    @BeforeEach
    void setUp() throws SQLException {
        stationDao = new PostgresStationDao(connection);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");

            // Svuotiamo tutte le tabelle coinvolte
            stmt.execute("TRUNCATE TABLE charging_stations RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE power_transformers RESTART IDENTITY");

            // INIEZIONE DI DATI FINTI: Bypassiamo i DAO dei colleghi!
            // Inseriamo un Operatore con ID fisso = 1
            stmt.execute("INSERT INTO users (id, name, email, password, role) " +
                    "VALUES (1, 'Operatore Fittizio', 'op@mock.com', 'pwd', 'STATION_OPERATOR')");

            // Inseriamo un Trasformatore con ID fisso = 1
            stmt.execute("INSERT INTO power_transformers (id, name, temperature, load_percent) " +
                    "VALUES (1, 'Trasformatore Fittizio', 25.0, 0.0)");

            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    // --- METODO HELPER PER CREARE STAZIONI DI TEST ---
    private ChargingStation createMockStation(String name, double lat, double lng, StationStatus status) {
        // Usiamo i Proxy con l'ID 1 che abbiamo appena forzato nel database
        StationOperator proxyOperator = StationOperator.reconstitute(1L);
        PowerTransformer proxyTransformer = PowerTransformer.reconstitute(1L);

        // Usiamo il factory method per creare rapidamente un oggetto di test
        return ChargingStation.reconstitute(
                null, // L'ID è nullo perché deve generarlo il DB
                proxyOperator, proxyTransformer, name, "Via Roma 1", lat, lng,
                ConnectorType.TYPE_2, 50.0, true, new BigDecimal("0.40"),
                new BigDecimal("0.05"), 0.0, 0, status, null, null
        );
    }

    // --- METODO HELPER SOVRACCARICATO (Per scegliere il tipo di connettore) ---
    private ChargingStation createMockStation(String name, double lat, double lng, StationStatus status, ConnectorType type) {
        StationOperator proxyOperator = StationOperator.reconstitute(1L);
        PowerTransformer proxyTransformer = PowerTransformer.reconstitute(1L);

        return ChargingStation.reconstitute(
                null, proxyOperator, proxyTransformer, name, "Via Roma 1", lat, lng,
                type, // <-- Passiamo il tipo dinamicamente!
                50.0, true, new BigDecimal("0.40"),
                new BigDecimal("0.05"), 0.0, 0, status, null, null
        );
    }

    // --- I NOSTRI TEST ---

    @Test
    void testSaveAndFindById() {
        // ARRANGEMENT
        ChargingStation station = createMockStation("Stazione Alpha", 43.7695, 11.2558, StationStatus.ACTIVE);

        // ACT
        stationDao.save(station);

        // ASSERT
        assertNotNull(station.getId(), "L'ID della stazione deve essere generato dal DB");

        ChargingStation retrieved = stationDao.findById(station.getId());
        assertNotNull(retrieved, "La stazione recuperata non deve essere nulla");
        assertEquals("Stazione Alpha", retrieved.getName());
        assertEquals(ConnectorType.TYPE_2, retrieved.getConnectorType());

        // Verifica che i Proxy siano stati mappati correttamente!
        assertNotNull(retrieved.getOperator());
        assertEquals(1L, retrieved.getOperator().getId());
        assertNotNull(retrieved.getTransformer());
        assertEquals(1L, retrieved.getTransformer().getId());
    }

    @Test
    void testFindNearestAvailable() {
        // ARRANGEMENT: Creiamo 3 stazioni con coordinate diverse
        ChargingStation s1 = createMockStation("Vicino", 10.0, 10.0, StationStatus.ACTIVE);
        ChargingStation s2 = createMockStation("Lontano", 50.0, 50.0, StationStatus.ACTIVE);
        ChargingStation s3 = createMockStation("Vicino ma Rotta", 10.1, 10.1, StationStatus.OVERLOADED);

        stationDao.save(s1);
        stationDao.save(s2);
        stationDao.save(s3);

        // ACT: Cerchiamo stazioni TYPE_2 vicine al punto (11.0, 11.0)
        List<ChargingStation> nearest = stationDao.findNearestAvailable(11.0, 11.0, ConnectorType.TYPE_2, null, null);

        // ASSERT
        // "Vicino ma Rotta" non deve apparire perché non è ACTIVE
        assertEquals(2, nearest.size(), "Deve trovare solo le 2 stazioni ACTIVE");

        // "Vicino" (s1) deve essere al primo posto grazie all'ORDER BY euclideo
        assertEquals(s1.getId(), nearest.get(0).getId(), "La prima stazione deve essere quella più vicina");
        assertEquals(s2.getId(), nearest.get(1).getId(), "La seconda stazione deve essere quella più lontana");
    }

    @Test
    void testExpireHolds() {
        // ARRANGEMENT
        // 1. Creiamo un guidatore fittizio usando il Proxy
        // (Possiamo usare l'ID 1 perché nel @BeforeEach abbiamo inserito un utente con ID 1)
        Driver mockDriver = Driver.reconstitute(1L);

        // 2. Creiamo la stazione di base
        ChargingStation station = createMockStation("Prenotata", 0.0, 0.0, StationStatus.RESERVED, ConnectorType.TYPE_2);

        // 3. Ricreiamo la stazione INCLUDENDO il guidatore e la scadenza!
        station = ChargingStation.reconstitute(
                null,
                station.getOperator(),
                station.getTransformer(),
                station.getName(),
                station.getAddress(),
                0.0, 0.0,
                station.getConnectorType(),
                150.0, false,
                new BigDecimal("0.50"), new BigDecimal("0.05"), 0.0, 0,
                StationStatus.RESERVED,
                mockDriver, // <--- ECCO IL SEGRETO: Passiamo il guidatore!
                java.time.LocalDateTime.now().minusMinutes(30) // Scaduta 30 min fa
        );

        // Ora il DAO entrerà nell'if e salverà correttamente il timestamp
        stationDao.save(station);

        // ACT: Lanciamo lo sblocco delle prenotazioni
        stationDao.expireHolds();

        // ASSERT: Verifichiamo che sia tornata attiva
        ChargingStation retrieved = stationDao.findById(station.getId());
        assertEquals(StationStatus.ACTIVE, retrieved.getStatus(), "Lo status deve essere tornato ACTIVE");
        assertNull(retrieved.getReservedBy(), "La prenotazione deve essere stata rimossa");
    }

    @Test
    void testUpdate() {
        // ARRANGEMENT: Creiamo e salviamo una stazione
        ChargingStation station = createMockStation("Stazione da Aggiornare", 40.0, 10.0, StationStatus.ACTIVE);
        stationDao.save(station);

        // Ci salviamo l'ID che il database ha assegnato a questa stazione
        Long id = station.getId();

        // ACT: Simuliamo la modifica ricreando l'oggetto con lo STESSO ID,
        // ma cambiando nome, potenza e stato (MAINTENANCE)
        ChargingStation updatedStation = ChargingStation.reconstitute(
                id,
                station.getOperator(),
                station.getTransformer(),
                "Stazione Aggiornata", // <-- Nome cambiato!
                station.getAddress(),
                station.getLatitude(),
                station.getLongitude(),
                station.getConnectorType(),
                150.0,                 // <-- Potenza cambiata!
                station.getSolarPowered(),
                station.getTariffOperator(),
                station.getTariffPlatform(),
                station.getAverageRating(),
                station.getTotalRatings(),
                StationStatus.OVERLOADED, // <-- Stato cambiato!
                station.getReservedBy(),
                station.getExpirationTimestamp()
        );

        // Passiamo al DAO l'oggetto modificato
        stationDao.update(updatedStation);

        // ASSERT: Rileggiamo dal database e verifichiamo che la query UPDATE abbia funzionato
        ChargingStation retrieved = stationDao.findById(id);

        assertNotNull(retrieved);
        assertEquals("Stazione Aggiornata", retrieved.getName(), "Il nome deve essere aggiornato nel DB");
        assertEquals(StationStatus.OVERLOADED, retrieved.getStatus(), "Lo status deve essere aggiornato nel DB");
        assertEquals(150.0, retrieved.getPowerKw(), "La potenza deve essere aggiornata nel DB");
    }

    @Test
    void testFindAllAndDelete() {
        // ARRANGEMENT: Inseriamo due stazioni
        ChargingStation s1 = createMockStation("Stazione 1", 0.0, 0.0, StationStatus.ACTIVE);
        ChargingStation s2 = createMockStation("Stazione 2", 1.0, 1.0, StationStatus.OVERLOADED);
        stationDao.save(s1);
        stationDao.save(s2);

        // ACT 1: Testiamo findAll
        List<ChargingStation> allStations = stationDao.findAll();
        assertEquals(2, allStations.size(), "Dovrebbero esserci esattamente 2 stazioni nel DB");

        // ACT 2: Testiamo delete
        stationDao.delete(s1.getId());

        // ASSERT: Verifichiamo che ne sia rimasta solo una
        List<ChargingStation> remainingStations = stationDao.findAll();
        assertEquals(1, remainingStations.size(), "Dopo la delete dovrebbe rimanere 1 sola stazione");
        assertEquals(s2.getId(), remainingStations.get(0).getId(), "La stazione rimasta deve essere la numero 2");
        assertNull(stationDao.findById(s1.getId()), "Cercando l'ID eliminato deve tornare null");
    }

    @Test
    void testFindByStatusAndActive() {
        // ARRANGEMENT
        ChargingStation active1 = createMockStation("A1", 0.0, 0.0, StationStatus.ACTIVE);
        ChargingStation active2 = createMockStation("A2", 0.0, 0.0, StationStatus.ACTIVE);
        ChargingStation maintenance = createMockStation("M1", 0.0, 0.0, StationStatus.OVERLOADED);

        stationDao.save(active1);
        stationDao.save(active2);
        stationDao.save(maintenance);

        // ACT
        List<ChargingStation> activeStations = stationDao.findByStatus(StationStatus.ACTIVE);
        List<ChargingStation> maintenanceStations = stationDao.findByStatus(StationStatus.OVERLOADED);
        List<ChargingStation> explicitlyActive = stationDao.findActive(); // Testiamo il metodo scorciatoia

        // ASSERT
        assertEquals(2, activeStations.size(), "Deve trovare 2 stazioni ACTIVE");
        assertEquals(1, maintenanceStations.size(), "Deve trovare 1 stazione MAINTENANCE");
        assertEquals(2, explicitlyActive.size(), "findActive() deve restituire 2 stazioni");
    }

    @Test
    void testFindByConnectorType() {
        // ARRANGEMENT: Usiamo il nuovo helper per creare direttamente i tipi corretti
        ChargingStation type2 = createMockStation("Stazione Tipo 2", 0.0, 0.0, StationStatus.ACTIVE, ConnectorType.TYPE_2);
        ChargingStation ccs2 = createMockStation("Stazione CCS", 0.0, 0.0, StationStatus.ACTIVE, ConnectorType.CCS_2);

        stationDao.save(type2);
        stationDao.save(ccs2);

        // ACT
        List<ChargingStation> type2Results = stationDao.findByConnectorType(ConnectorType.TYPE_2);
        List<ChargingStation> ccs2Results = stationDao.findByConnectorType(ConnectorType.CCS_2);

        // ASSERT
        assertEquals(1, type2Results.size(), "Deve trovare una stazione TYPE_2");
        assertEquals(ConnectorType.TYPE_2, type2Results.get(0).getConnectorType());

        assertEquals(1, ccs2Results.size(), "Deve trovare una stazione CCS_2");
        assertEquals(ConnectorType.CCS_2, ccs2Results.get(0).getConnectorType());
    }

    @Test
    void testFindByOperatorAndTransformer() {
        // ARRANGEMENT
        // Il nostro createMockStation assegna di default l'operatore 1L e il trasformatore 1L (i mock)
        ChargingStation station = createMockStation("Stazione Mock", 0.0, 0.0, StationStatus.ACTIVE);
        stationDao.save(station);

        // ACT
        List<ChargingStation> byOperator = stationDao.findByOperator(1L);
        List<ChargingStation> byWrongOperator = stationDao.findByOperator(999L);

        List<ChargingStation> byTransformer = stationDao.findByTransformer(1L);
        List<ChargingStation> byWrongTransformer = stationDao.findByTransformer(999L);

        // ASSERT
        assertEquals(1, byOperator.size(), "Deve trovare la stazione per l'operatore 1");
        assertTrue(byWrongOperator.isEmpty(), "Non deve trovare stazioni per un operatore inesistente");

        assertEquals(1, byTransformer.size(), "Deve trovare la stazione per il trasformatore 1");
        assertTrue(byWrongTransformer.isEmpty(), "Non deve trovare stazioni per un trasformatore inesistente");
    }

    @Test
    @DisplayName("Acquisizione atomica della stazione (Race Condition Defense)")
    void testAcquireAtomicHold() throws SQLException {
        // --- ARRANGE ---
        // Svuotiamo e prepariamo il database fittizio
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE charging_stations RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE power_transformers RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");

            // Inseriamo due Driver per simulare la concorrenza (ID 1 e ID 2)
            stmt.execute("INSERT INTO users (id, name, email, password, role) VALUES (1, 'Mario Rossi', 'mario@mail.com', 'pwd', 'DRIVER')");
            stmt.execute("INSERT INTO users (id, name, email, password, role) VALUES (2, 'Luigi Verdi', 'luigi@mail.com', 'pwd', 'DRIVER')");

            // Inseriamo Operatore e Trasformatore necessari per la stazione
            stmt.execute("INSERT INTO users (id, name, email, password, role) VALUES (3, 'Enel X', 'enel@mail.com', 'pwd', 'STATION_OPERATOR')");
            stmt.execute("INSERT INTO power_transformers (id, name) VALUES (1, 'Trasformatore Alpha')");

            // INSERIAMO LA STAZIONE IN STATO 'ACTIVE'
            stmt.execute("INSERT INTO charging_stations (id, operator_id, transformer_id, name, address, latitude, longitude, connector_type, power_kw, tariff_operator, status) " +
                    "VALUES (1, 3, 1, 'Stazione Test', 'Via Roma', 43.0, 11.0, 'TYPE_2', 50.0, 0.45, 'ACTIVE')");
        }

        // --- ACT 1: Il primo guidatore (ID 1) tenta di prenotare la stazione ---
        boolean success = stationDao.acquireAtomicHold(1L, 1L);

        // --- ASSERT 1: La prenotazione deve avere successo ---
        assertTrue(success, "La prima prenotazione deve avere successo perché la stazione è ACTIVE");

        // Verifichiamo direttamente sul database che lo stato sia cambiato e la scadenza impostata
        try (Statement stmt = connection.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT status, reserved_by_id, expiration_timestamp FROM charging_stations WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("RESERVED", rs.getString("status"), "Lo stato della stazione deve essere passato a RESERVED");
            assertEquals(1L, rs.getLong("reserved_by_id"), "La stazione deve essere riservata al Driver 1");
            assertNotNull(rs.getTimestamp("expiration_timestamp"), "Il database deve aver calcolato e inserito la scadenza di 15 minuti");
        }

        // --- ACT 2: Il secondo guidatore (ID 2) tenta di prenotare LA STESSA stazione ---
        boolean failedAttempt = stationDao.acquireAtomicHold(1L, 2L);

        // --- ASSERT 2: Il database DEVE bloccare questa operazione ---
        assertFalse(failedAttempt, "La seconda prenotazione DEVE fallire restituendo false, perché la stazione non è più ACTIVE");
    }

    @Test
    @DisplayName("Conteggio stazioni per trasformatore")
    void testCountByTransformer() {
        // --- ARRANGE ---
        // Usiamo il trasformatore ID=1 già inserito nel setUp()
        // Creiamo 3 stazioni collegate a questo trasformatore
        ChargingStation s1 = createMockStation("S1", 0.0, 0.0, StationStatus.ACTIVE);
        ChargingStation s2 = createMockStation("S2", 1.0, 1.0, StationStatus.ACTIVE);
        ChargingStation s3 = createMockStation("S3", 2.0, 2.0, StationStatus.ACTIVE);

        stationDao.save(s1);
        stationDao.save(s2);
        stationDao.save(s3);

        // --- ACT ---
        int count = stationDao.countByTransformer(1L);

        // --- ASSERT ---
        assertEquals(3, count, "Il conteggio delle stazioni per il trasformatore 1 deve essere 3");

        // Verifica anche un caso di trasformatore vuoto
        int zeroCount = stationDao.countByTransformer(999L);
        assertEquals(0, zeroCount, "Il conteggio per un trasformatore inesistente deve essere 0");
    }
}

