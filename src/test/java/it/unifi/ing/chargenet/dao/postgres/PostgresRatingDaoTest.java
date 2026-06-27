package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.feedback.Rating;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.users.Driver;
import org.junit.jupiter.api.*;

import java.util.List;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class PostgresRatingDaoTest {

    private static Connection connection;
    private PostgresRatingDao ratingDao;

    // Variabili per conservare i dati "padre" pre-caricati
    private Driver mockDriver;
    private ChargingStation mockStation;
    private ChargingSession mockSession;


    @BeforeAll
    static void startDatabase() throws SQLException {
        String url = "jdbc:h2:mem:chargenet_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=RUNSCRIPT FROM './src/main/resources/scheme.sql'";
        connection = DriverManager.getConnection(url, "sa", "");
    }

    @AfterAll
    static void stopDatabase() throws SQLException {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void setUp() throws SQLException {
        ratingDao = new PostgresRatingDao(connection);

        // Pulizia totale
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE ratings RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE charging_sessions RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE charging_stations RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE power_transformers RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");

            // 1. Inseriamo gli Utenti (Driver e Operatore)
            stmt.execute("INSERT INTO users (id, name, email, password, role) VALUES (1, 'Mario Rossi', 'mario@mail.com', 'password123', 'DRIVER')");
            stmt.execute("INSERT INTO users (id, name, email, password, role) VALUES (2, 'Enel X', 'enelx@mail.com', 'password123', 'STATION_OPERATOR')");

            // 2. Inseriamo il Trasformatore (Ora con il campo 'name' obbligatorio!)
            stmt.execute("INSERT INTO power_transformers (id, name) VALUES (1, 'Trasformatore Alpha')");

            // 3. Inseriamo la Stazione (Con TUTTI i campi NOT NULL richiesti dal tuo schema)
            stmt.execute("INSERT INTO charging_stations (id, operator_id, transformer_id, name, address, latitude, longitude, connector_type, power_kw, tariff_operator, status) " +
                    "VALUES (1, 2, 1, 'Stazione Test', 'Via Roma 1', 43.0, 11.0, 'TYPE_2', 50.0, 0.45, 'AVAILABLE')");

            // 4. Inseriamo la Sessione (Con TUTTI i campi NOT NULL richiesti)
            stmt.execute("INSERT INTO charging_sessions (id, driver_id, station_id, strategy_used, battery_start, battery_current, status, opened_at) " +
                    "VALUES (1, 1, 1, 'STANDARD', 20.0, 80.0, 'COMPLETED', CURRENT_TIMESTAMP)");
        }

        // --- Ricreiamo gli oggetti fittizi in memoria da agganciare alla recensione ---

        mockDriver = Driver.reconstitute(1L, "Mario Rossi", "mario@mail.com", "password123", 43.0, 11.0, null, null, 50.0, BigDecimal.ZERO);

        it.unifi.ing.chargenet.domain.users.StationOperator mockOperator =
                it.unifi.ing.chargenet.domain.users.StationOperator.reconstitute(2L, "Enel X", "enelx@mail.com", "password123", BigDecimal.ZERO);

        mockStation = ChargingStation.reconstitute(
                1L, mockOperator, null, "Stazione Test", "Via Roma 1",
                43.0, 11.0, null, 50.0,
                false, BigDecimal.ZERO, BigDecimal.ZERO,
                0.0, 0, null,
                null, null
        );

        mockSession = ChargingSession.reconstitute(
                1L, mockDriver, mockStation,
                "STANDARD", 20.0, 80.0, 30.0,
                new BigDecimal("15.00"), null,
                LocalDateTime.now().minusHours(1), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Salvataggio e recupero tramite ID con Join su 4 tabelle")
    void testSaveAndFindById() {
        // ARRANGE: Creiamo una recensione in memoria (con ID null) usando i padri creati nel Setup
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        Rating newRating = Rating.reconstitute(
                null,
                mockDriver,
                mockStation,
                mockSession,
                5,
                "Ottima ricarica, veloce!",
                now
        );

        // ACT 1: Salviamo la recensione nel database
        ratingDao.save(newRating);

        // ASSERT 1: La prova del nove! Il DAO aggiornato deve aver valorizzato l'ID
        assertNotNull(newRating.getId(), "Il DAO deve recuperare l'ID generato dal DB e assegnarlo all'oggetto!");

        // ACT 2: Ricerchiamo la recensione usando dinamicamente l'ID appena valorizzato
        Rating retrieved = ratingDao.findById(newRating.getId());

        // ASSERT 2: Controlli serrati sui dati della recensione
        assertNotNull(retrieved, "Il rating non deve essere null, la query di JOIN deve averlo trovato");
        assertEquals(5, retrieved.getStars());
        assertEquals("Ottima ricarica, veloce!", retrieved.getComment());
        assertEquals(now, retrieved.getCreatedAt());

        // ASSERT 3: Verifichiamo che gli oggetti padre siano stati rimontati correttamente da "extractRatingFromResultSet"
        assertNotNull(retrieved.getDriver(), "Il Driver deve essere recuperato");
        assertEquals(1L, retrieved.getDriver().getId());

        assertNotNull(retrieved.getStation(), "La Stazione deve essere recuperata");
        assertEquals(1L, retrieved.getStation().getId());

        assertNotNull(retrieved.getSession(), "La Sessione deve essere recuperata");
        assertEquals(1L, retrieved.getSession().getId());
    }

    @Test
    @DisplayName("Verifica esistenza recensione (ExistsByDriverAndSession)")
    void testExistsByDriverAndSession() {
        // ARRANGE: Salviamo una recensione nel database usando i nostri mock
        Rating rating = Rating.reconstitute(
                null, mockDriver, mockStation, mockSession, 4, "Tutto ok", LocalDateTime.now()
        );
        ratingDao.save(rating);

        // ACT 1: Chiediamo al DAO se esiste una recensione per la combinazione corretta
        boolean exists = ratingDao.existsByDriverAndSession(mockDriver.getId(), mockSession.getId());

        // ACT 2: Chiediamo al DAO combinazioni palesemente inesistenti
        boolean notExistsWrongDriver = ratingDao.existsByDriverAndSession(999L, mockSession.getId());
        boolean notExistsWrongSession = ratingDao.existsByDriverAndSession(mockDriver.getId(), 999L);

        // ASSERT: Verifichiamo che il database risponda correttamente in tutti gli scenari
        assertTrue(exists, "Il DAO deve restituire TRUE per la combinazione Driver/Sessione appena salvata");
        assertFalse(notExistsWrongDriver, "Il DAO deve restituire FALSE se il Driver ID non esiste");
        assertFalse(notExistsWrongSession, "Il DAO deve restituire FALSE se il Session ID non esiste");
    }

    @Test
    @DisplayName("Ricalcolo media recensioni (RecalculateAverage)")
    void testRecalculateAverage() throws SQLException {
        // --- ARRANGE ---
        // 1. Inseriamo una SECONDA sessione di ricarica via SQL per aggirare il vincolo di unicità
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO charging_sessions (id, driver_id, station_id, strategy_used, battery_start, battery_current, status, opened_at) " +
                    "VALUES (2, 1, 1, 'STANDARD', 20.0, 80.0, 'COMPLETED', CURRENT_TIMESTAMP)");
        }

        // 2. Creiamo l'oggetto Sessione fittizio con ID 2
        ChargingSession mockSession2 = ChargingSession.reconstitute(
                2L, mockDriver, mockStation, "STANDARD", 20.0, 80.0, 30.0,
                new BigDecimal("15.00"), null, LocalDateTime.now(), LocalDateTime.now()
        );

        // 3. Salviamo due recensioni per la STESSA stazione
        Rating rating1 = Rating.reconstitute(null, mockDriver, mockStation, mockSession, 4, "Buona", LocalDateTime.now());
        Rating rating2 = Rating.reconstitute(null, mockDriver, mockStation, mockSession2, 5, "Perfetta", LocalDateTime.now());

        ratingDao.save(rating1);
        ratingDao.save(rating2);

        // Lanciamo il metodo che deve aggiornare i dati della stazione
        ratingDao.recalculateAverage(mockStation.getId());

        // Siccome il RatingDao non ha un metodo per leggere la Stazione, usiamo una query diretta
        try (Statement stmt = connection.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT average_rating, total_ratings FROM charging_stations WHERE id = " + mockStation.getId())) {

            assertTrue(rs.next(), "La stazione deve esistere nel database");

            double average = rs.getDouble("average_rating");
            int total = rs.getInt("total_ratings");

            // La media matematica tra 4 e 5 è esattamente 4.5
            assertEquals(4.5, average, 0.001, "La media calcolata dal database deve essere 4.5");
            assertEquals(2, total, "Il conteggio totale delle recensioni deve essere 2");
        }
    }

    @Test
    @DisplayName("Ricerca recensioni per Stazione con ordinamento (FindByStation)")
    void testFindByStation() throws SQLException {
        // --- ARRANGE ---
        // 1. Inseriamo una SECONDA sessione di ricarica per poter lasciare una seconda recensione
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO charging_sessions (id, driver_id, station_id, strategy_used, battery_start, battery_current, status, opened_at) " +
                    "VALUES (2, 1, 1, 'STANDARD', 20.0, 80.0, 'COMPLETED', CURRENT_TIMESTAMP)");
        }

        // 2. Ricreiamo l'oggetto in memoria per la seconda sessione
        ChargingSession mockSession2 = ChargingSession.reconstitute(
                2L, mockDriver, mockStation, "STANDARD", 20.0, 80.0, 30.0,
                new BigDecimal("15.00"), null, LocalDateTime.now(), LocalDateTime.now()
        );

        // 3. Creiamo due recensioni sfalsate nel tempo
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime today = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Rating olderRating = Rating.reconstitute(null, mockDriver, mockStation, mockSession, 4, "Ricarica di ieri", yesterday);
        Rating newerRating = Rating.reconstitute(null, mockDriver, mockStation, mockSession2, 5, "Ricarica di oggi", today);

        // Salviamo le recensioni nel database
        ratingDao.save(olderRating);
        ratingDao.save(newerRating);

        // --- ACT ---
        // Chiediamo al DAO di estrarre lo storico della stazione
        List<Rating> stationRatings = ratingDao.findByStation(mockStation.getId());

        // --- ASSERT ---
        assertEquals(2, stationRatings.size(), "Il database deve trovare esattamente le 2 recensioni della stazione");

        // Verifica fondamentale: l'ordinamento (DESC)
        assertEquals(newerRating.getId(), stationRatings.get(0).getId(), "La recensione più RECENTE deve trovarsi al primo posto (indice 0)");
        assertEquals(olderRating.getId(), stationRatings.get(1).getId(), "La recensione più VECCHIA deve trovarsi al secondo posto (indice 1)");

        // Verifica di sicurezza profonda
        for (Rating r : stationRatings) {
            assertEquals(mockStation.getId(), r.getStation().getId(), "Ogni recensione estratta deve appartenere alla stazione richiesta");
        }
    }

    @Test
    @DisplayName("Ricerca recensioni per Utente con ordinamento (FindByDriver)")
    void testFindByDriver() throws SQLException {
        // --- ARRANGE ---
        // 1. Dobbiamo creare una seconda sessione per il nostro mockDriver, e un nuovo utente con la sua sessione
        try (Statement stmt = connection.createStatement()) {
            // Seconda sessione per il Driver 1 (mockDriver)
            stmt.execute("INSERT INTO charging_sessions (id, driver_id, station_id, strategy_used, battery_start, battery_current, status, opened_at) " +
                    "VALUES (2, 1, 1, 'STANDARD', 20.0, 80.0, 'COMPLETED', CURRENT_TIMESTAMP)");

            // Inseriamo un SECONDO guidatore (ID 3, visto che il 2 è l'Operatore)
            stmt.execute("INSERT INTO users (id, name, email, password, role) VALUES (3, 'Luigi Verdi', 'luigi@mail.com', 'pwd123', 'DRIVER')");

            // Inseriamo una sessione per il SECONDO guidatore
            stmt.execute("INSERT INTO charging_sessions (id, driver_id, station_id, strategy_used, battery_start, battery_current, status, opened_at) " +
                    "VALUES (3, 3, 1, 'STANDARD', 20.0, 80.0, 'COMPLETED', CURRENT_TIMESTAMP)");
        }

        // 2. Ricreiamo gli oggetti in memoria corrispondenti
        ChargingSession mockSession2 = ChargingSession.reconstitute(
                2L, mockDriver, mockStation, "STANDARD", 20.0, 80.0, 30.0,
                new BigDecimal("15.00"), null, LocalDateTime.now(), LocalDateTime.now()
        );

        Driver driver2 = Driver.reconstitute(
                3L, "Luigi Verdi", "luigi@mail.com", "pwd123",
                43.0, 11.0, null, null, 50.0, BigDecimal.ZERO
        );

        ChargingSession mockSession3 = ChargingSession.reconstitute(
                3L, driver2, mockStation, "STANDARD", 20.0, 80.0, 30.0,
                new BigDecimal("15.00"), null, LocalDateTime.now(), LocalDateTime.now()
        );

        // 3. Creiamo le recensioni sfalsate nel tempo
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime today = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // Due recensioni per il mockDriver (una ieri, una oggi)
        Rating olderRatingDriver1 = Rating.reconstitute(null, mockDriver, mockStation, mockSession, 4, "Buona", yesterday);
        Rating newerRatingDriver1 = Rating.reconstitute(null, mockDriver, mockStation, mockSession2, 5, "Ottima", today);

        // Una recensione per l'altro driver
        Rating ratingDriver2 = Rating.reconstitute(null, driver2, mockStation, mockSession3, 3, "Normale", today);

        // Salviamo tutto nel DB
        ratingDao.save(olderRatingDriver1);
        ratingDao.save(newerRatingDriver1);
        ratingDao.save(ratingDriver2);


        // --- ACT ---
        // Estraiamo gli storici separati
        List<Rating> driver1History = ratingDao.findByDriver(mockDriver.getId());
        List<Rating> driver2History = ratingDao.findByDriver(driver2.getId());


        // --- ASSERT ---
        assertEquals(2, driver1History.size(), "Dovrebbe trovare esattamente le 2 recensioni del Driver 1");
        assertEquals(1, driver2History.size(), "Dovrebbe trovare esattamente 1 recensione per il Driver 2");

        // Verifica dell'ordinamento (DESC) per il Driver 1
        assertEquals(newerRatingDriver1.getId(), driver1History.get(0).getId(), "La transazione più RECENTE deve essere al primo posto");
        assertEquals(olderRatingDriver1.getId(), driver1History.get(1).getId(), "La transazione più VECCHIA deve essere al secondo posto");

        // Verifica di sicurezza profonda: niente intrusioni
        for (Rating r : driver1History) {
            assertEquals(mockDriver.getId(), r.getDriver().getId(), "Ogni recensione nella lista deve appartenere al Driver 1");
        }
    }

    @Test
    @DisplayName("Sicurezza: Modifica, Cancellazione e FindAll bloccati correttamente")
    void testUnsupportedOperations() {
        // --- ARRANGE ---
        // Creiamo un rating fittizio in memoria (non serve nemmeno salvarlo)
        Rating mockRating = Rating.reconstitute(1L, mockDriver, mockStation, mockSession, 5, "Test", LocalDateTime.now());

        // --- ACT & ASSERT per UPDATE ---
        UnsupportedOperationException updateEx = assertThrows(UnsupportedOperationException.class, () -> {
            ratingDao.update(mockRating);
        });
        assertTrue(updateEx.getMessage().contains("Sicurezza"), "Il messaggio per l'update deve menzionare la sicurezza");

        // --- ACT & ASSERT per DELETE ---
        UnsupportedOperationException deleteEx = assertThrows(UnsupportedOperationException.class, () -> {
            ratingDao.delete(1L);
        });
        assertTrue(deleteEx.getMessage().contains("Sicurezza"), "Il messaggio per la delete deve menzionare la sicurezza");

        // --- ACT & ASSERT per FIND ALL ---
        UnsupportedOperationException findAllEx = assertThrows(UnsupportedOperationException.class, () -> {
            ratingDao.findAll();
        });
        assertTrue(findAllEx.getMessage().contains("performance"), "Il messaggio per findAll deve menzionare le performance");
    }
}