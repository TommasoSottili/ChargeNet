package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.users.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PostgresUserDaoTest {

    private static Connection connection;
    private PostgresUserDao userDao;


    @BeforeAll
    static void startDatabase() throws SQLException {
        // Stringa di connessione che usa il file scheme.sql per creare le tabelle
        String url = "jdbc:h2:mem:chargenet_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=RUNSCRIPT FROM './src/main/resources/scheme.sql'";
        connection = DriverManager.getConnection(url, "sa", "");
    }

    @AfterAll
    static void stopDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        userDao = new PostgresUserDao(connection);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");

            // Svuotiamo la tabella users per avere un ambiente pulito a ogni test
            stmt.execute("TRUNCATE TABLE users RESTART IDENTITY");

            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
    /**
     * Crea un oggetto Driver di test non ancora salvato nel DB (ID null).
     */
    private Driver createMockDriver(String email) {
        return Driver.reconstitute(
                null,
                "Mario Rossi",
                email,
                "passwordSegreta",
                43.7695,
                11.2558,
                ConnectorType.TYPE_2,
                SubscriptionPlan.BASIC,
                50.0,
                new BigDecimal("10.50")
        );
    }

    /**
     * Crea un oggetto StationOperator di test.
     */
    private StationOperator createMockOperator(String email) {
        return StationOperator.reconstitute(
                null,
                "Enel X",
                email,
                "pwd123",
                new BigDecimal("100.00")
        );
    }

    /**
     * Crea un oggetto EnergyManager di test.
     */
    private EnergyManager createMockEnergyManager(String email) {
        return EnergyManager.reconstitute(
                null,
                "Admin System",
                email,
                "adminPwd"
        );
    }

    @Test
    @DisplayName("Salvataggio e recupero tramite ID di un Driver")
    void testSaveAndFindById_Driver() {
        // ARRANGE: Creiamo un driver usando il nostro helper
        Driver newDriver = createMockDriver("driver.test@mail.com");

        // ACT 1: Salviamo nel DB
        userDao.save(newDriver);

        // ASSERT 1: Verifichiamo che Postgres abbia restituito un ID generato
        assertNotNull(newDriver.getId(), "Il DB deve generare un ID per il nuovo utente");

        // ACT 2: Andiamo a ripescarlo dal DB usando l'ID appena ottenuto
        User retrievedUser = userDao.findById(newDriver.getId());

        // ASSERT 2: Controlliamo che i campi specifici siano stati mappati correttamente
        assertNotNull(retrievedUser, "L'utente non deve essere null");
        assertInstanceOf(Driver.class, retrievedUser, "L'utente recuperato deve essere mappato come Driver");

        Driver retrievedDriver = (Driver) retrievedUser;
        assertEquals("driver.test@mail.com", retrievedDriver.getEmail());
        assertEquals(Role.DRIVER, retrievedDriver.getRole());
        assertEquals(43.7695, retrievedDriver.getLatitude());
        assertEquals(ConnectorType.TYPE_2, retrievedDriver.getConnectorType());
        // Per i BigDecimal è sempre bene usare compareTo per ignorare la scala (es. 10.5 vs 10.50)
        assertEquals(0, new BigDecimal("10.50").compareTo(retrievedDriver.getWalletBalance()));
    }

    @Test
    @DisplayName("Salvataggio e recupero tramite ID di uno StationOperator")
    void testSaveAndFindById_StationOperator() {
        // ARRANGE: Creiamo un operatore
        StationOperator newOperator = createMockOperator("operator.test@mail.com");

        // ACT
        userDao.save(newOperator);
        User retrievedUser = userDao.findById(newOperator.getId());

        // ASSERT
        assertNotNull(retrievedUser);
        assertInstanceOf(StationOperator.class, retrievedUser, "L'utente recuperato deve essere mappato come StationOperator");

        StationOperator retrievedOperator = (StationOperator) retrievedUser;
        assertEquals("Enel X", retrievedOperator.getName());
        assertEquals(Role.STATION_OPERATOR, retrievedOperator.getRole());
        assertEquals(0, new BigDecimal("100.00").compareTo(retrievedOperator.getTotalEarnings()));
    }

    @Test
    @DisplayName("Salvataggio e recupero tramite ID di un Energy Manager")
    void testSaveAndFindById_EnergyManager() {
        // ARRANGE: Creiamo un manager
        EnergyManager newManager = createMockEnergyManager("manager.test@mail.com");

        // ACT
        userDao.save(newManager);
        User retrievedUser = userDao.findById(newManager.getId());

        // ASSERT
        assertNotNull(retrievedUser, "L'utente non deve essere null");
        assertInstanceOf(EnergyManager.class, retrievedUser, "L'utente recuperato deve essere mappato come EnergyManager");

        EnergyManager retrievedManager = (EnergyManager) retrievedUser;
        assertEquals("Admin System", retrievedManager.getName());
        assertEquals("manager.test@mail.com", retrievedManager.getEmail());
        assertEquals(Role.ENERGY_MANAGER, retrievedManager.getRole());
    }

    @Test
    @DisplayName("Aggiornamento di un utente (Update)")
    void testUpdate() {
        // ARRANGE: Creiamo e salviamo un utente di partenza
        Driver driver = createMockDriver("update.test@mail.com");
        userDao.save(driver);

        // Ci salviamo l'ID che il database gli ha assegnato
        Long id = driver.getId();

        // Simuliamo l'aggiornamento: usiamo reconstitute per ricreare l'utente
        // con lo STESSO ID, ma modifichiamo la password e incrementiamo il saldo.
        Driver updatedDriver = Driver.reconstitute(
                id,
                "Mario Rossi",
                "update.test@mail.com",
                "nuovaPassword123", // <-- Modifica 1: Password cambiata
                43.7695, 11.2558, ConnectorType.TYPE_2, SubscriptionPlan.BASIC, 50.0,
                new BigDecimal("500.00") // <-- Modifica 2: Saldo ricaricato
        );

        // ACT: Chiamiamo il metodo update del DAO per sovrascrivere i vecchi dati
        userDao.update(updatedDriver);

        // ASSERT: Rileggiamo l'utente dal database e verifichiamo che i dati siano stati effettivamente sovrascritti
        User retrievedUser = userDao.findById(id);
        assertNotNull(retrievedUser, "L'utente deve esistere");

        Driver dbDriver = (Driver) retrievedUser;
        assertEquals("nuovaPassword123", dbDriver.getPassword(), "La password deve essere stata aggiornata nel DB");
        assertEquals(0, new BigDecimal("500.00").compareTo(dbDriver.getWalletBalance()), "Il saldo deve essere stato aggiornato nel DB");
    }

    @Test
    @DisplayName("Ricerca utente tramite Email (FindByEmail)")
    void testFindByEmail() {
        // ARRANGE: Creiamo e salviamo un utente nel DB
        Driver driver = createMockDriver("login.user@mail.com");
        userDao.save(driver);

        // ACT: Proviamo a cercare l'email appena salvata e una palesemente inventata
        User foundUser = userDao.findByEmail("login.user@mail.com");
        User notFoundUser = userDao.findByEmail("inesistente@mail.com");

        // ASSERT: Verifichiamo che il DAO reagisca correttamente in entrambi i casi
        assertNotNull(foundUser, "Deve trovare l'utente con l'email esistente");
        assertEquals(driver.getId(), foundUser.getId(), "L'ID dell'utente trovato deve combaciare con quello salvato");
        assertEquals("login.user@mail.com", foundUser.getEmail(), "L'email recuperata deve corrispondere");

        assertNull(notFoundUser, "Deve restituire null se l'email non è registrata nel sistema");
    }

    @Test
    @DisplayName("Ricerca utenti per Ruolo (FindByRole)")
    void testFindByRole() {
        // ARRANGE: Creiamo un ecosistema misto inserendo 2 Driver e 1 Operatore
        userDao.save(createMockDriver("driver1@mail.com"));
        userDao.save(createMockDriver("driver2@mail.com"));
        userDao.save(createMockOperator("operator1@mail.com"));

        // ACT: Chiediamo al DAO di restituirci le liste filtrate per ruolo
        List<User> drivers = userDao.findByRole(Role.DRIVER);
        List<User> operators = userDao.findByRole(Role.STATION_OPERATOR);
        List<User> managers = userDao.findByRole(Role.ENERGY_MANAGER);

        // ASSERT: Verifichiamo che il DAO abbia contato e separato correttamente
        assertEquals(2, drivers.size(), "Dovrebbero esserci esattamente 2 Driver");
        assertEquals(1, operators.size(), "Dovrebbe esserci esattamente 1 Operatore");
        assertEquals(0, managers.size(), "Non essendoci Energy Manager, la lista deve essere vuota (size 0)");
    }

    @Test
    @DisplayName("Recupero totale e cancellazione (FindAll e Delete)")
    void testFindAllAndDelete() {
        // ARRANGE: Inseriamo un paio di utenti nel nostro DB pulito
        Driver driver = createMockDriver("da.cancellare@mail.com");
        StationOperator operator = createMockOperator("da.mantenere@mail.com");
        userDao.save(driver);
        userDao.save(operator);

        // ACT 1: Testiamo il metodo findAll
        List<User> allUsers = userDao.findAll();

        // ASSERT 1: Verifichiamo che li abbia pescati entrambi
        assertEquals(2, allUsers.size(), "Il database deve contenere esattamente 2 utenti");

        // ACT 2: Testiamo il metodo delete eliminando SOLO il driver
        userDao.delete(driver.getId());

        // ASSERT 2: Verifichiamo le conseguenze dell'eliminazione sul database
        List<User> remainingUsers = userDao.findAll();
        assertEquals(1, remainingUsers.size(), "Dopo l'eliminazione deve rimanere 1 solo utente");
        assertEquals(operator.getId(), remainingUsers.get(0).getId(), "L'utente rimasto deve essere l'Operatore");

        // La prova del nove: cercare l'utente eliminato tramite ID deve restituire null
        assertNull(userDao.findById(driver.getId()), "Cercando l'ID appena eliminato, il DAO deve restituire null");
    }
}


