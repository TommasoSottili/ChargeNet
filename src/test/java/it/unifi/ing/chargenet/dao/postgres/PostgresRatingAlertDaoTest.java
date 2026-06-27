package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.feedback.RatingAlert;
import it.unifi.ing.chargenet.domain.feedback.RatingAlertStatus;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.StationOperator;
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

class PostgresRatingAlertDaoTest {

    private static Connection connection;
    private PostgresRatingAlertDao alertDao;

    // Ci serve solo la stazione (con il suo operatore) come dipendenza
    private ChargingStation mockStation;


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
        alertDao = new PostgresRatingAlertDao(connection);

        // Pulizia totale e inserimento delle chiavi esterne per la Stazione
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE rating_alerts RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE charging_stations RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE power_transformers RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");

            // 1. Inseriamo l'Operatore (id = 1)
            stmt.execute("INSERT INTO users (id, name, email, password, role) VALUES (1, 'Enel X', 'enelx@mail.com', 'password123', 'STATION_OPERATOR')");

            // 2. Inseriamo il Trasformatore (id = 1)
            stmt.execute("INSERT INTO power_transformers (id, name) VALUES (1, 'Trasformatore Alpha')");

            // 3. Inseriamo la Stazione collegata
            stmt.execute("INSERT INTO charging_stations (id, operator_id, transformer_id, name, address, latitude, longitude, connector_type, power_kw, tariff_operator, status) " +
                    "VALUES (1, 1, 1, 'Stazione Test', 'Via Roma 1', 43.0, 11.0, 'TYPE_2', 50.0, 0.45, 'AVAILABLE')");
        }

        // Ricreiamo l'oggetto Operatore fittizio
        StationOperator mockOperator = StationOperator.reconstitute(1L, "Enel X", "enelx@mail.com", "password123", BigDecimal.ZERO);

        // Ricreiamo l'oggetto Stazione fittizio per passarlo all'Alert
        mockStation = ChargingStation.reconstitute(
                1L, mockOperator, null, "Stazione Test", "Via Roma 1",
                43.0, 11.0, null, 50.0,
                false, BigDecimal.ZERO, BigDecimal.ZERO,
                0.0, 0, null,
                null, null
        );
    }


    @Test
    @DisplayName("Salvataggio e recupero tramite ID con auto-generazione della chiave")
    void testSaveAndFindById() {
        // ARRANGE: Creiamo un nuovo Alert (con ID null e nessuna nota del manager per ora)
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RatingAlert newAlert = RatingAlert.reconstitute(
                null,
                mockStation,
                2.5, // Media bassa che ha fatto scattare l'allarme
                RatingAlertStatus.PENDING,
                null,
                now
        );

        // ACT 1: Salviamo l'allarme
        alertDao.save(newAlert);

        // ASSERT 1: Verifichiamo che il database abbia restituito un ID all'oggetto
        assertNotNull(newAlert.getId(), "Il DAO deve aver recuperato e impostato l'ID generato dal database");

        // ACT 2: Usiamo l'ID appena generato per pescare l'allarme dal database
        RatingAlert retrieved = alertDao.findById(newAlert.getId());

        // ASSERT 2: Controlliamo che tutti i dati siano intatti
        assertNotNull(retrieved, "L'alert deve essere stato trovato");
        assertEquals(2.5, retrieved.getAvgAtCreation());
        assertEquals(RatingAlertStatus.PENDING, retrieved.getStatus());
        assertNull(retrieved.getManagerNote(), "La nota del manager deve essere null al momento della creazione");
        assertEquals(now, retrieved.getCreatedAt());

        // ASSERT 3: Controlliamo la JOIN con la stazione
        assertNotNull(retrieved.getStation(), "La stazione associata deve essere stata recuperata");
        assertEquals(mockStation.getId(), retrieved.getStation().getId());
        assertEquals("Stazione Test", retrieved.getStation().getName());
    }

    @Test
    @DisplayName("Aggiornamento stato e nota manager (Update)")
    void testUpdate() {
        // --- ARRANGE ---
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // 1. Creiamo e salviamo un allarme iniziale appena scattato (PENDING e managerNote = null)
        RatingAlert initialAlert = RatingAlert.reconstitute(
                null, mockStation, 2.0, RatingAlertStatus.PENDING, null, now
        );
        alertDao.save(initialAlert);

        // Salviamoci l'ID appena generato
        Long savedId = initialAlert.getId();

        // --- ACT ---
        // 2. Simuliamo il manager che analizza e decide di sospendere la stazione.
        // Ricreiamo l'oggetto con lo STESSO ID, stato RESOLVED_SUSPENDED e nota esplicativa.
        RatingAlert updatedAlert = RatingAlert.reconstitute(
                savedId, mockStation, 2.0, RatingAlertStatus.RESOLVED_SUSPENDED, "Stazione sospesa per guasto hardware critico", now
        );

        // Lanciamo l'aggiornamento nel database
        alertDao.update(updatedAlert);

        // --- ASSERT ---
        // 3. Peschiamo l'allarme fresco dal database per assicurarci che i dati siano stati sovrascritti
        RatingAlert retrieved = alertDao.findById(savedId);

        assertNotNull(retrieved, "L'alert deve esistere nel database");
        assertEquals(RatingAlertStatus.RESOLVED_SUSPENDED, retrieved.getStatus(), "Lo stato deve essere stato aggiornato a RESOLVED_SUSPENDED");
        assertEquals("Stazione sospesa per guasto hardware critico", retrieved.getManagerNote(), "La nota del manager deve essere stata salvata correttamente");
        assertEquals(2.0, retrieved.getAvgAtCreation(), "La media di attivazione non deve essere stata modificata");
    }

    @Test
    @DisplayName("Ricerca alert per Stato con ordinamento FIFO (FindByStatus)")
    void testFindByStatus() {
        // --- ARRANGE ---
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime yesterday = now.minusDays(1);

        // 1. Creiamo due allarmi PENDING sfalsati nel tempo
        RatingAlert oldestPending = RatingAlert.reconstitute(null, mockStation, 1.5, RatingAlertStatus.PENDING, null, yesterday);
        RatingAlert newestPending = RatingAlert.reconstitute(null, mockStation, 2.5, RatingAlertStatus.PENDING, null, now);

        // 2. Creiamo un terzo allarme con uno stato diverso per assicurarci che il filtro funzioni
        RatingAlert resolvedAlert = RatingAlert.reconstitute(null, mockStation, 1.0, RatingAlertStatus.RESOLVED_SUSPENDED, "Sostituita presa", now);

        // Salviamoli tutti e tre nel database
        alertDao.save(oldestPending);
        alertDao.save(newestPending);
        alertDao.save(resolvedAlert);

        // --- ACT ---
        // Estraiamo le code separate in base allo stato
        List<RatingAlert> pendingQueue = alertDao.findByStatus(RatingAlertStatus.PENDING);
        List<RatingAlert> resolvedQueue = alertDao.findByStatus(RatingAlertStatus.RESOLVED_SUSPENDED);

        // --- ASSERT ---
        // Verifica dei conteggi
        assertEquals(2, pendingQueue.size(), "Il database deve trovare esattamente 2 alert in stato PENDING");
        assertEquals(1, resolvedQueue.size(), "Il database deve trovare esattamente 1 alert in stato RESOLVED_SUSPENDED");

        // Verifica cruciale: Ordinamento FIFO (Crescente / ASC)
        assertEquals(oldestPending.getId(), pendingQueue.get(0).getId(), "L'allarme PIÙ VECCHIO deve essere il primo della lista (Urgenza maggiore)");
        assertEquals(newestPending.getId(), pendingQueue.get(1).getId(), "L'allarme PIÙ RECENTE deve essere il secondo della lista");

        // Verifica di sicurezza profonda: nessun intruso nella coda PENDING
        for (RatingAlert alert : pendingQueue) {
            assertEquals(RatingAlertStatus.PENDING, alert.getStatus(), "Ogni allarme estratto deve avere lo stato richiesto");
        }
    }

    @Test
    @DisplayName("Ricerca alert per Stazione con ordinamento DESC (FindByStation)")
    void testFindByStation() throws SQLException {
        // --- ARRANGE ---
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime yesterday = now.minusDays(1);

        // 1. Creiamo due allarmi sfalsati nel tempo per la nostra mockStation
        RatingAlert olderAlert = RatingAlert.reconstitute(null, mockStation, 1.5, RatingAlertStatus.PENDING, null, yesterday);
        RatingAlert newerAlert = RatingAlert.reconstitute(null, mockStation, 2.5, RatingAlertStatus.RESOLVED_SUSPENDED, "Risolto", now);

        alertDao.save(olderAlert);
        alertDao.save(newerAlert);

        // 2. Inseriamo una SECONDA stazione via SQL e un suo alert per assicurarci che non venga "pescato"
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO charging_stations (id, operator_id, transformer_id, name, address, latitude, longitude, connector_type, power_kw, tariff_operator, status) " +
                    "VALUES (2, 1, 1, 'Stazione Fantasma', 'Via Roma 2', 43.0, 11.0, 'TYPE_2', 50.0, 0.45, 'AVAILABLE')");
        }

        // Ricreiamo la stazione 2 in memoria e le assegniamo un allarme
        ChargingStation station2 = ChargingStation.reconstitute(
                2L, mockStation.getOperator(), null, "Stazione Fantasma", null,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        RatingAlert otherStationAlert = RatingAlert.reconstitute(null, station2, 3.0, RatingAlertStatus.PENDING, null, now);
        alertDao.save(otherStationAlert);

        // --- ACT ---
        // Chiediamo al DAO lo storico esclusivo della stazione 1 (mockStation)
        List<RatingAlert> stationAlerts = alertDao.findByStation(mockStation.getId());

        // --- ASSERT ---
        assertEquals(2, stationAlerts.size(), "Il DAO deve trovare esattamente 2 alert per la stazione richiesta (ignorando la Stazione Fantasma)");

        // Verifica dell'ordinamento DESC (I più recenti per primi)
        assertEquals(newerAlert.getId(), stationAlerts.get(0).getId(), "L'alert PIÙ RECENTE deve essere il primo della lista");
        assertEquals(olderAlert.getId(), stationAlerts.get(1).getId(), "L'alert PIÙ VECCHIO deve essere il secondo della lista");

        // Verifica di sicurezza: nessun intruso
        for (RatingAlert alert : stationAlerts) {
            assertEquals(mockStation.getId(), alert.getStation().getId(), "Ogni alert estratto deve appartenere alla stazione richiesta");
        }
    }

    @Test
    @DisplayName("Verifica esistenza alert aperti (ExistsOpenAlertForStation)")
    void testExistsOpenAlertForStation() {
        // --- ARRANGE ---
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // All'inizio il database è vuoto, non deve esserci nessun alert aperto
        assertFalse(alertDao.existsOpenAlertForStation(mockStation.getId()), "Non ci sono alert, deve restituire FALSE");

        // Salviamo un alert in stato RESOLVED (quindi chiuso)
        RatingAlert resolvedAlert = RatingAlert.reconstitute(null, mockStation, 2.0, RatingAlertStatus.RESOLVED_SUSPENDED, "Risolto", now);
        alertDao.save(resolvedAlert);

        // Anche con un alert risolto, non ci sono alert APERTI, deve ancora restituire FALSE
        assertFalse(alertDao.existsOpenAlertForStation(mockStation.getId()), "L'alert presente è chiuso, deve restituire FALSE");

        // Salviamo un alert in stato PENDING (aperto!)
        RatingAlert pendingAlert = RatingAlert.reconstitute(null, mockStation, 1.5, RatingAlertStatus.PENDING, null, now);
        alertDao.save(pendingAlert);

        // --- ACT & ASSERT ---
        // Ora che c'è un alert PENDING, deve restituire TRUE
        assertTrue(alertDao.existsOpenAlertForStation(mockStation.getId()), "C'è un alert PENDING, il DAO deve bloccare nuove creazioni restituendo TRUE");
    }

    @Test
    @DisplayName("Sicurezza: Cancellazione e FindAll bloccati correttamente")
    void testUnsupportedOperations() {
        // --- ACT & ASSERT per DELETE ---
        UnsupportedOperationException deleteEx = assertThrows(UnsupportedOperationException.class, () -> {
            alertDao.delete(1L);
        });
        assertTrue(deleteEx.getMessage().contains("Audit Log"), "Il messaggio per la delete deve spiegare il motivo (Audit Log)");

        // --- ACT & ASSERT per FIND ALL ---
        UnsupportedOperationException findAllEx = assertThrows(UnsupportedOperationException.class, () -> {
            alertDao.findAll();
        });
        assertTrue(findAllEx.getMessage().contains("performance"), "Il messaggio per findAll deve menzionare le performance");
    }
}