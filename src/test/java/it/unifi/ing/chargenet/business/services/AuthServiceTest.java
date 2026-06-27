package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.business.services.DuplicateEmailException;
import it.unifi.ing.chargenet.business.services.AuthenticationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private AuthService authService;

    // Gestore di Mockito per intercettare i metodi statici
    private MockedStatic<DatabaseManager> mockedDbManager;

    // Dipendenze mockate
    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private UserDao userDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        // 1. Creiamo la finta connessione
        connectionMock = mock(Connection.class);

        // 2. Intercettiamo globalmente la chiamata a DatabaseManager.getConnection()
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 3. Creiamo i mock per Factory e DAO
        daoFactoryMock = mock(DaoFactory.class);
        userDaoMock = mock(UserDao.class);

        // 4. Istruiamo la Factory: "Quando ti chiedono uno UserDao, dai quello finto!"
        doReturn(userDaoMock).when(daoFactoryMock).createUserDao(any(Connection.class));

        // 5. Istanziamo il Service (con il tuo costruttore aggiornato!)
        authService = new AuthService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        // Fondamentale: rilasciamo il blocco statico per non inquinare altri test
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    @Test
    @DisplayName("Registrazione con successo (Driver)")
    void testRegister_Success() throws SQLException {
        // --- ARRANGE ---
        String email = "test@mail.com";
        String password = "password123";

        // Diciamo al finto DAO che l'email NON esiste nel database (restituisce null)
        when(userDaoMock.findByEmail(email)).thenReturn(null);

        // --- ACT ---
        User createdUser = authService.register("Mario Rossi", email, password, Role.DRIVER);

        // --- ASSERT ---
        assertNotNull(createdUser, "L'utente creato non deve essere null");
        assertEquals(email, createdUser.getEmail());
        assertTrue(createdUser instanceof Driver, "L'utente deve essere di tipo Driver");

        // Verifica che la libreria BCrypt abbia hashato correttamente la password
        assertTrue(BCrypt.checkpw(password, createdUser.getPassword()), "La password deve essere stata hashata correttamente");

        // Verifiche comportamentali di Mockito (ha usato il DB nel modo giusto?)
        verify(connectionMock, times(1)).setAutoCommit(false); // Ha iniziato la transazione?
        verify(userDaoMock, times(1)).save(any(User.class));   // Ha chiamato il salvataggio?
        verify(connectionMock, times(1)).commit();             // Ha confermato su DB?
        verify(connectionMock, times(1)).close();              // Ha chiuso il rubinetto?
    }

    @Test
    @DisplayName("Registrazione fallita per Email già in uso")
    void testRegister_DuplicateEmail() throws SQLException {
        // --- ARRANGE ---
        String email = "esiste@mail.com";

        // Diciamo a Mockito di simulare che il DAO trovi già un utente con questa email
        User existingUser = mock(User.class);
        when(userDaoMock.findByEmail(email)).thenReturn(existingUser);

        // --- ACT & ASSERT ---
        // Ora usiamo la TUA eccezione specifica!
        DuplicateEmailException exception = assertThrows(DuplicateEmailException.class, () -> {
            authService.register("Luigi Bianchi", email, "password123", Role.DRIVER);
        });

        // Verifichiamo che il messaggio d'errore sia corretto
        assertTrue(exception.getMessage().contains("già in uso"), "Il messaggio d'errore deve segnalare l'email duplicata");

        // --- VERIFICHE COMPORTAMENTALI CRUCIALI ---
        verify(userDaoMock, never()).save(any());        // NON deve aver salvato nulla!
        verify(connectionMock, never()).commit();        // NON deve aver confermato la transazione!
        verify(connectionMock, times(1)).rollback();     // DEVE aver annullato le operazioni!
        verify(connectionMock, times(1)).close();        // DEVE comunque chiudere la connessione per non bloccare il server
    }

    // =========================================================================
    // --- 2. TEST: LOGIN E LOGOUT ---
    // =========================================================================

    @Test
    @DisplayName("Login con successo")
    void testLogin_Success() throws SQLException {
        // --- ARRANGE ---
        String email = "login@mail.com";
        String rawPassword = "mySecretPassword";
        // Simuliamo l'hash che troveremmo nel database
        String hashedPwd = BCrypt.hashpw(rawPassword, BCrypt.gensalt());

        // Creiamo un utente fittizio (ricorda l'ordine corretto: nome, email, password!)
        Driver mockDriver = new Driver(null, null, null, null, null, "Mario Rossi", email, hashedPwd);
        when(userDaoMock.findByEmail(email)).thenReturn(mockDriver);

        // --- ACT ---
        User loggedUser = authService.login(email, rawPassword);

        // --- ASSERT ---
        assertNotNull(loggedUser, "L'utente restituito non deve essere null");
        assertEquals(email, loggedUser.getEmail(), "L'email deve corrispondere");
        assertEquals(mockDriver, authService.getCurrentUser(), "Il service deve aver salvato l'utente nella variabile currentUser");

        verify(connectionMock, times(1)).close(); // Ha chiuso la connessione alla fine?
    }

    @Test
    @DisplayName("Login fallito per Password errata")
    void testLogin_WrongPassword() throws SQLException {
        // --- ARRANGE ---
        String email = "login@mail.com";
        String correctPassword = "correctPassword";
        String wrongPassword = "wrongPassword";
        String hashedPwd = BCrypt.hashpw(correctPassword, BCrypt.gensalt());

        Driver mockDriver = new Driver(null, null, null, null, null, "Mario", email, hashedPwd);
        when(userDaoMock.findByEmail(email)).thenReturn(mockDriver);

        // --- ACT & ASSERT ---
        // Ci aspettiamo la TUA eccezione specifica
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login(email, wrongPassword);
        });

        assertTrue(exception.getMessage().contains("Credenziali non valide"), "Il messaggio d'errore deve essere generico per sicurezza");
        assertNull(authService.getCurrentUser(), "In caso di errore, nessuno deve risultare loggato");
    }

    @Test
    @DisplayName("Login fallito per Email inesistente")
    void testLogin_UserNotFound() {
        // --- ARRANGE ---
        when(userDaoMock.findByEmail("fantasma@mail.com")).thenReturn(null);

        // --- ACT & ASSERT ---
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login("fantasma@mail.com", "pwd");
        });

        assertTrue(exception.getMessage().contains("Credenziali non valide"));
    }

    @Test
    @DisplayName("Logout rimuove l'utente corrente")
    void testLogout() {
        // --- ARRANGE ---
        // Prima facciamo loggare un utente in modo forzato per il test
        String email = "test@mail.com";
        String rawPwd = "pwd";
        Driver mockDriver = new Driver(null, null, null, null, null, "Mario", email, BCrypt.hashpw(rawPwd, BCrypt.gensalt()));
        when(userDaoMock.findByEmail(email)).thenReturn(mockDriver);

        authService.login(email, rawPwd);
        assertNotNull(authService.getCurrentUser(), "Assicuriamoci che il setup del test abbia loggato l'utente");

        // --- ACT ---
        authService.logout();

        // --- ASSERT ---
        assertNull(authService.getCurrentUser(), "L'utente corrente deve essere null dopo il logout");
    }
}