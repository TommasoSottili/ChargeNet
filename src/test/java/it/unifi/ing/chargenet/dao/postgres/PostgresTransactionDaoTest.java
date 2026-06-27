package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.financials.TransactionType;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresTransactionDaoTest {

    private static Connection connection;
    private PostgresTransactionDao transactionDao;
    private PostgresUserDao userDao;


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
        transactionDao = new PostgresTransactionDao(connection);
        userDao = new PostgresUserDao(connection); // Ci serve per salvare i Driver!

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE transactions RESTART IDENTITY");
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
    /**
     * Crea e SALVA un Driver nel database, restituendolo pronto all'uso.
     */
    private Driver createAndSaveMockDriver() {
        Driver driver = Driver.reconstitute(
                null, "Test Driver", "driver@mail.com", "pwd",
                0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.BASIC,
                50.0, new BigDecimal("100.00")
        );
        userDao.save(driver);
        return driver;
    }

    /**
     * Crea un oggetto Transaction (in memoria) associato a un Driver.
     */
    private Transaction createMockTransaction(Driver driver, TransactionType type, String amount, String kwh) {
        return Transaction.reconstitute(
                null,
                driver,
                type,
                new BigDecimal(amount),
                kwh != null ? Double.parseDouble(kwh) : null,
                "Transazione di test",
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) // Tronchiamo ai secondi per evitare problemi di millisecondi con SQL
        );
    }

    @Test
    @DisplayName("Salvataggio e recupero tramite ID (Join corretta)")
    void testSaveAndFindById() {
        // ARRANGE: Creiamo il driver e la transazione
        Driver savedDriver = createAndSaveMockDriver();
        Transaction newTransaction = createMockTransaction(savedDriver, TransactionType.CHARGE, "25.50", "15.5");

        // ACT 1: Salviamo la transazione
        transactionDao.save(newTransaction);

        // ASSERT 1: Verifica generazione ID
        assertNotNull(newTransaction.getId(), "Il database deve generare un ID per la transazione");

        // ACT 2: Ricerca tramite ID
        Transaction retrieved = transactionDao.findById(newTransaction.getId());

        // ASSERT 2: Verifica dei campi della transazione
        assertNotNull(retrieved, "La transazione non deve essere null");
        assertEquals(TransactionType.CHARGE, retrieved.getType());
        assertEquals(0, new BigDecimal("25.50").compareTo(retrieved.getAmount()));
        assertEquals(15.5, retrieved.getKwh());

        // ASSERT 3: Verifica fondamentale della JOIN (L'oggetto Driver deve essere stato ricostruito)
        assertNotNull(retrieved.getDriver(), "Il driver associato deve essere recuperato tramite la JOIN");
        assertEquals(savedDriver.getId(), retrieved.getDriver().getId());
        assertEquals("Test Driver", retrieved.getDriver().getName());
    }

    @Test
    @DisplayName("Ricerca transazioni per Driver ID (FindByDriver)")
    void testFindByDriver() {
        // --- ARRANGE ---
        // 1. Creiamo e salviamo il nostro Driver principale
        Driver driver1 = createAndSaveMockDriver();

        // 2. Creiamo e salviamo un SECONDO Driver (usiamo reconstitute per fargli un'email diversa)
        Driver driver2 = Driver.reconstitute(
                null, "Altro Driver", "altro@mail.com", "pwd",
                0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.BASIC,
                50.0, new BigDecimal("0.00")
        );
        userDao.save(driver2);

        // 3. Salviamo 2 transazioni per il Driver 1
        transactionDao.save(createMockTransaction(driver1, TransactionType.FUND_ADDED, "50.00", null));
        transactionDao.save(createMockTransaction(driver1, TransactionType.CHARGE, "10.00", "5.0"));

        // 4. Salviamo 1 transazione per il Driver 2
        transactionDao.save(createMockTransaction(driver2, TransactionType.FUND_ADDED, "20.00", null));


        // --- ACT ---
        // Chiediamo al DAO di estrarre gli storici separati
        List<Transaction> driver1History = transactionDao.findByDriver(driver1.getId());
        List<Transaction> driver2History = transactionDao.findByDriver(driver2.getId());


        // --- ASSERT ---
        // Verifichiamo che il DAO abbia filtrato correttamente
        assertEquals(2, driver1History.size(), "Dovrebbe trovare esattamente le 2 transazioni del Driver 1");
        assertEquals(1, driver2History.size(), "Dovrebbe trovare esattamente 1 transazione per il Driver 2");

        // Verifica di sicurezza: controlliamo che nella lista del Driver 1 non ci siano transazioni di altri
        for (Transaction t : driver1History) {
            assertEquals(driver1.getId(), t.getDriver().getId(), "Ogni transazione nella lista deve appartenere al Driver 1");
        }
    }

    @Test
    @DisplayName("Ricerca transazioni per Tipo (FindByType)")
    void testFindByType() {
        // --- ARRANGE ---
        // 1. Abbiamo bisogno di un Driver a cui associare le transazioni
        Driver driver = createAndSaveMockDriver();

        // 2. Inseriamo un mix di transazioni diverse
        // 2 di tipo CHARGE
        transactionDao.save(createMockTransaction(driver, TransactionType.CHARGE, "15.00", "5.0"));
        transactionDao.save(createMockTransaction(driver, TransactionType.CHARGE, "25.00", "10.0"));
        // 1 di tipo FUND_ADDED
        transactionDao.save(createMockTransaction(driver, TransactionType.FUND_ADDED, "50.00", null));
        // 1 di tipo REFUND
        transactionDao.save(createMockTransaction(driver, TransactionType.REFUND, "15.00", null));

        // --- ACT ---
        // Chiediamo al database di filtrare
        List<Transaction> charges = transactionDao.findByType(TransactionType.CHARGE);
        List<Transaction> fundsAdded = transactionDao.findByType(TransactionType.FUND_ADDED);
        List<Transaction> subscriptions = transactionDao.findByType(TransactionType.SUBSCRIPTION);

        // --- ASSERT ---
        // Verifichiamo che i conteggi siano esatti
        assertEquals(2, charges.size(), "Il database deve trovare esattamente 2 transazioni di tipo CHARGE");
        assertEquals(1, fundsAdded.size(), "Il database deve trovare esattamente 1 transazione di tipo FUND_ADDED");
        assertEquals(0, subscriptions.size(), "Non essendoci abbonamenti, la lista deve essere vuota");

        // Verifica di sicurezza profonda: ogni elemento della lista 'charges' deve essere effettivamente un CHARGE
        for (Transaction t : charges) {
            assertEquals(TransactionType.CHARGE, t.getType(), "Il tipo della transazione estratta deve corrispondere al filtro richiesto");
        }
    }

    @Test
    @DisplayName("Recupero di tutte le transazioni con ordinamento (FindAll)")
    void testFindAll() {
        // --- ARRANGE ---
        Driver driver = createAndSaveMockDriver();

        // Creiamo la prima transazione "Vecchia" (impostata a ieri)
        Transaction olderTransaction = Transaction.reconstitute(
                null, driver, TransactionType.CHARGE, new BigDecimal("10.00"), 5.0, "Ricarica di ieri",
                LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS)
        );

        // Creiamo la seconda transazione "Nuova" (impostata a oggi)
        Transaction newerTransaction = Transaction.reconstitute(
                null, driver, TransactionType.FUND_ADDED, new BigDecimal("50.00"), null, "Ricarica di oggi",
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        );

        // Le salviamo nel database
        transactionDao.save(olderTransaction);
        transactionDao.save(newerTransaction);

        // --- ACT ---
        List<Transaction> allTransactions = transactionDao.findAll();

        // --- ASSERT ---
        assertEquals(2, allTransactions.size(), "Il database deve restituire esattamente 2 transazioni in totale");

        // Verifichiamo che l'ordinamento (DESC) funzioni correttamente
        assertEquals(newerTransaction.getId(), allTransactions.get(0).getId(), "La transazione più RECENTE deve trovarsi al primo posto (indice 0)");
        assertEquals(olderTransaction.getId(), allTransactions.get(1).getId(), "La transazione più VECCHIA deve trovarsi al secondo posto (indice 1)");
    }

    @Test
    @DisplayName("Sicurezza: Modifica e Cancellazione di transazioni non consentite")
    void testUpdateAndDelete_ThrowException() {
        // --- ARRANGE ---
        // Creiamo una transazione fittizia in memoria (non serve salvarla nel DB per questo test)
        Transaction mockTransaction = Transaction.reconstitute(
                1L, null, TransactionType.CHARGE, new BigDecimal("10.00"), 5.0, "Test Immutabilità", LocalDateTime.now()
        );

        // --- ACT & ASSERT per UPDATE ---
        // Verifichiamo che chiamare update lanci l'eccezione esatta
        UnsupportedOperationException updateException = assertThrows(UnsupportedOperationException.class, () -> {
            transactionDao.update(mockTransaction);
        });
        // Verifichiamo che il messaggio contenga la parola "Sicurezza"
        assertTrue(updateException.getMessage().contains("Sicurezza"), "Il messaggio dell'eccezione deve spiegare i motivi di sicurezza");


        // --- ACT & ASSERT per DELETE ---
        // Verifichiamo che chiamare delete lanci l'eccezione esatta
        UnsupportedOperationException deleteException = assertThrows(UnsupportedOperationException.class, () -> {
            transactionDao.delete(1L);
        });
        assertTrue(deleteException.getMessage().contains("Sicurezza"), "Il messaggio dell'eccezione deve spiegare i motivi di sicurezza");
    }
}