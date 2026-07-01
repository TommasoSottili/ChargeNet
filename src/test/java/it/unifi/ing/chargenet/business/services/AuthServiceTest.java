package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.business.exceptions.AuthenticationException;
import it.unifi.ing.chargenet.business.exceptions.DuplicateEmailException;
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.users.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private AuthService authService;
    private MockedStatic<DatabaseManager> mockedDbManager;
    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private UserDao userDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        connectionMock = mock(Connection.class);
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        daoFactoryMock = mock(DaoFactory.class);
        userDaoMock = mock(UserDao.class);
        doReturn(userDaoMock).when(daoFactoryMock).createUserDao(any(Connection.class));

        authService = new AuthService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    // =========================================================================
    // --- 1. REGISTRAZIONE — register() per StationOperator / EnergyManager
    // =========================================================================

    @Test
    @DisplayName("Registrazione con successo (Station Operator)")
    void testRegister_Success() throws SQLException {
        String email = "operator@mail.com";
        String password = "password123";

        when(userDaoMock.findByEmail(email)).thenReturn(null);

        User createdUser = authService.register("Mario Rossi", email, password, Role.STATION_OPERATOR);

        assertNotNull(createdUser);
        assertEquals(email, createdUser.getEmail());
        assertTrue(createdUser instanceof StationOperator);
        assertTrue(BCrypt.checkpw(password, createdUser.getPassword()));

        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(userDaoMock, times(1)).save(any(User.class));
        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Registrazione fallita per Email già in uso (Station Operator)")
    void testRegister_DuplicateEmail() throws SQLException {
        String email = "esiste@mail.com";

        User existingUser = mock(User.class);
        when(userDaoMock.findByEmail(email)).thenReturn(existingUser);

        DuplicateEmailException exception = assertThrows(DuplicateEmailException.class, () ->
                authService.register("Luigi Bianchi", email, "password123", Role.STATION_OPERATOR)
        );

        assertTrue(exception.getMessage().contains("già in uso"));

        verify(userDaoMock, never()).save(any());
        verify(connectionMock, never()).commit();
        verify(connectionMock, times(1)).rollback();
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("register() rifiuta Role.DRIVER e indirizza a registerDriver()")
    void testRegister_DriverRoleRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                authService.register("Mario Rossi", "driver@mail.com", "pwd", Role.DRIVER)
        );

        assertTrue(exception.getMessage().contains("registerDriver"));

        // La guardia scatta PRIMA di apoire qualunque connessione: nessuna query va al DB
        mockedDbManager.verify(DatabaseManager::getConnection, never());
    }

    // =========================================================================
    // --- 2. REGISTRAZIONE DRIVER — registerDriver()
    // =========================================================================

    @Test
    @DisplayName("Registrazione Driver con successo: posizione e piano assegnati automaticamente")
    void testRegisterDriver_Success() throws SQLException {
        String email = "driver@mail.com";
        String password = "password123";

        when(userDaoMock.findByEmail(email)).thenReturn(null);

        Driver createdDriver = authService.registerDriver(
                "Mario Rossi", email, password, ConnectorType.TYPE_2
        );

        assertNotNull(createdDriver);
        assertEquals(email, createdDriver.getEmail());
        assertEquals(ConnectorType.TYPE_2, createdDriver.getConnectorType());
        assertEquals(SubscriptionPlan.BASIC, createdDriver.getSubscriptionPlan(),
                "Il piano di default alla registrazione deve essere BASIC");
        assertEquals(BigDecimal.ZERO, createdDriver.getWalletBalance());
        assertTrue(BCrypt.checkpw(password, createdDriver.getPassword()));

        // Verifica che GpsSimulator abbia assegnato coordinate plausibili
        // (dentro il bounding box di Firenze, non semplicemente "non null")
        assertNotNull(createdDriver.getLatitude());
        assertNotNull(createdDriver.getLongitude());
        assertTrue(createdDriver.getLatitude() >= 43.74 && createdDriver.getLatitude() <= 43.82);
        assertTrue(createdDriver.getLongitude() >= 11.18 && createdDriver.getLongitude() <= 11.32);

        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(userDaoMock, times(1)).save(any(Driver.class));
        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Registrazione Driver fallita per Email già in uso")
    void testRegisterDriver_DuplicateEmail() throws SQLException {
        String email = "driverexists@mail.com";

        User existingUser = mock(User.class);
        when(userDaoMock.findByEmail(email)).thenReturn(existingUser);

        DuplicateEmailException exception = assertThrows(DuplicateEmailException.class, () ->
                authService.registerDriver("Luigi", email, "pwd", ConnectorType.CCS_2)
        );

        assertTrue(exception.getMessage().contains("già in uso"));

        verify(userDaoMock, never()).save(any());
        verify(connectionMock, never()).commit();
        verify(connectionMock, times(1)).rollback();
        verify(connectionMock, times(1)).close();
    }

    // =========================================================================
    // --- 3. LOGIN E LOGOUT ---
    // =========================================================================

    @Test
    @DisplayName("Login con successo")
    void testLogin_Success() throws SQLException {
        String email = "login@mail.com";
        String rawPassword = "mySecretPassword";
        String hashedPwd = BCrypt.hashpw(rawPassword, BCrypt.gensalt());

        // Simuliamo esattamente cosa farebbe PostgresUserDao.findByEmail():
        // ricostituisce un Driver già esistente con TUTTI i campi, incluso il wallet
        Driver mockDriver = Driver.reconstitute(
                1L, "Mario Rossi", email, hashedPwd,
                43.7696, 11.2558, ConnectorType.TYPE_2, SubscriptionPlan.BASIC,
                50.0, BigDecimal.ZERO
        );
        when(userDaoMock.findByEmail(email)).thenReturn(mockDriver);

        User loggedUser = authService.login(email, rawPassword);

        assertNotNull(loggedUser);
        assertEquals(email, loggedUser.getEmail());
        assertEquals(mockDriver, authService.getCurrentUser());

        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Login fallito per Password errata")
    void testLogin_WrongPassword() throws SQLException {
        String email = "login@mail.com";
        String correctPassword = "correctPassword";
        String wrongPassword = "wrongPassword";
        String hashedPwd = BCrypt.hashpw(correctPassword, BCrypt.gensalt());

        Driver mockDriver = Driver.reconstitute(
                1L, "Mario", email, hashedPwd,
                43.7696, 11.2558, ConnectorType.TYPE_2, SubscriptionPlan.BASIC,
                50.0, BigDecimal.ZERO
        );
        when(userDaoMock.findByEmail(email)).thenReturn(mockDriver);

        AuthenticationException exception = assertThrows(AuthenticationException.class, () ->
                authService.login(email, wrongPassword)
        );

        assertTrue(exception.getMessage().contains("Credenziali non valide"));
        assertNull(authService.getCurrentUser());
    }

    @Test
    @DisplayName("Login fallito per Email inesistente")
    void testLogin_UserNotFound() {
        when(userDaoMock.findByEmail("fantasma@mail.com")).thenReturn(null);

        AuthenticationException exception = assertThrows(AuthenticationException.class, () ->
                authService.login("fantasma@mail.com", "pwd")
        );

        assertTrue(exception.getMessage().contains("Credenziali non valide"));
    }

    @Test
    @DisplayName("Logout rimuove l'utente corrente")
    void testLogout() {
        String email = "test@mail.com";
        String rawPwd = "pwd";
        Driver mockDriver = Driver.reconstitute(
                1L, "Mario", email, BCrypt.hashpw(rawPwd, BCrypt.gensalt()),
                43.7696, 11.2558, ConnectorType.TYPE_2, SubscriptionPlan.BASIC,
                50.0, BigDecimal.ZERO
        );
        when(userDaoMock.findByEmail(email)).thenReturn(mockDriver);

        authService.login(email, rawPwd);
        assertNotNull(authService.getCurrentUser());

        authService.logout();

        assertNull(authService.getCurrentUser());
    }

    // =========================================================================
    // --- 4. REFRESH USER — refreshUser()
    // =========================================================================

    @Test
    @DisplayName("refreshUser restituisce l'utente ricaricato dal DAO tramite id")
    void testRefreshUser_Success() throws SQLException {
        Long userId = 1L;

        // Il DAO (mockato) restituisce un Driver con saldo 42.50:
        // simula lo stato "fresco" presente nel DB dopo una ricarica.
        Driver freshDriver = Driver.reconstitute(
                userId, "Mario Rossi", "driver@mail.com", "hashedPwd",
                43.7696, 11.2558, ConnectorType.TYPE_2, SubscriptionPlan.BASIC,
                50.0, new BigDecimal("42.50")
        );
        when(userDaoMock.findById(userId)).thenReturn(freshDriver);

        User result = authService.refreshUser(userId);

        // Verifica il contratto: torna ciò che il DAO ha fornito, col saldo aggiornato
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertTrue(result instanceof Driver);
        assertEquals(new BigDecimal("42.50"), ((Driver) result).getWalletBalance(),
                "refreshUser deve riflettere il saldo restituito dal DAO, non un valore stantio");

        // Ha interrogato il DAO con l'id corretto e ha chiuso la connessione
        verify(userDaoMock, times(1)).findById(userId);
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("refreshUser restituisce null se l'utente non esiste più")
    void testRefreshUser_NotFound() throws SQLException {
        Long userId = 99L;
        when(userDaoMock.findById(userId)).thenReturn(null);

        User result = authService.refreshUser(userId);

        assertNull(result, "Se il DAO non trova l'utente, refreshUser propaga null");
        verify(userDaoMock, times(1)).findById(userId);
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("refreshUser non lascia mai la connessione aperta in caso di errore DAO")
    void testRefreshUser_ClosesConnectionOnError() throws SQLException {
        Long userId = 1L;
        when(userDaoMock.findById(userId)).thenThrow(new RuntimeException("DB giù"));

        // Il metodo incapsula l'errore in RuntimeException...
        assertThrows(RuntimeException.class, () -> authService.refreshUser(userId));

        // ...ma la connessione DEVE essere chiusa comunque (finally)
        verify(connectionMock, times(1)).close();
    }
}
