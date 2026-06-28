package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.users.Driver;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriverServiceTest {

    private DriverService driverService;
    private MockedStatic<DatabaseManager> mockedDbManager;
    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private UserDao userDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        // 1. Setup Connessione
        connectionMock = mock(Connection.class);
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 2. Setup Factory e DAO
        daoFactoryMock = mock(DaoFactory.class);
        userDaoMock = mock(UserDao.class);
        // Assicuriamoci che la factory ritorni il nostro mock di userDao
        when(daoFactoryMock.createUserDao(connectionMock)).thenReturn(userDaoMock);

        // 3. Inizializzazione Service
        driverService = new DriverService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    @Test
    @DisplayName("Aggiornamento posizione GPS driver - Successo")
    void testUpdateDriverLocation_Success() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        double lat = 43.7696;
        double lng = 11.2558;

        // --- ACT ---
        driverService.updateDriverLocation(mockDriver, lat, lng);

        // --- ASSERT ---
        // 1. Verifica che la logica di dominio sia stata chiamata
        verify(mockDriver, times(1)).updatePosition(lat, lng);

        // 2. Verifica che il DAO sia stato chiamato per la persistenza
        verify(userDaoMock, times(1)).update(mockDriver);

        // 3. Verifica transazione
        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Aggiornamento posizione GPS driver - Fallimento DB")
    void testUpdateDriverLocation_Failure() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        // Simuliamo un errore nel database durante l'update
        doThrow(new RuntimeException("Database error")).when(userDaoMock).update(any(Driver.class));

        // --- ACT & ASSERT ---
        assertThrows(RuntimeException.class, () -> {
            driverService.updateDriverLocation(mockDriver, 0.0, 0.0);
        });

        // Verifica che il rollback sia stato chiamato
        verify(connectionMock, times(1)).rollback();
        verify(connectionMock, times(1)).close();

        // Verifica che il commit NON sia mai stato chiamato
        verify(connectionMock, never()).commit();
    }
}