package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.business.strategies.ChargingStrategy;
import it.unifi.ing.chargenet.dao.interfaces.*;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import it.unifi.ing.chargenet.domain.users.StationOperator; // Aggiunto import
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private SessionService sessionService;

    // Gestore di Mockito per intercettare i metodi statici
    private MockedStatic<DatabaseManager> mockedDbManager;

    // Dipendenze mockate
    private DaoFactory daoFactoryMock;
    private Connection connectionMock;

    // DAO Mockati
    private SessionDao sessionDaoMock;
    private StationDao stationDaoMock;
    private UserDao userDaoMock;
    private TransactionDao transactionDaoMock;

    @BeforeEach
    void setUp() throws Exception {
        connectionMock = mock(Connection.class);

        // 1. Intercettiamo globalmente la chiamata al metodo statico DatabaseManager.getConnection()
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 2. RISOLUZIONE COSTRUTTORE PRIVATO: Usiamo la Reflection per istanziare DatabaseManager
        Constructor<DatabaseManager> constructor = DatabaseManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        DatabaseManager dbManagerInstance = constructor.newInstance();

        // 3. Creiamo i mock delle interfacce
        daoFactoryMock = mock(DaoFactory.class);
        sessionDaoMock = mock(SessionDao.class);
        stationDaoMock = mock(StationDao.class);
        userDaoMock = mock(UserDao.class);
        transactionDaoMock = mock(TransactionDao.class);

        // 4. Istruiamo la Factory
        doReturn(sessionDaoMock).when(daoFactoryMock).createSessionDao(any());
        doReturn(stationDaoMock).when(daoFactoryMock).createStationDao(any());
        doReturn(userDaoMock).when(daoFactoryMock).createUserDao(any());
        doReturn(transactionDaoMock).when(daoFactoryMock).createTransactionDao(any());

        // 5. Istanziamo il Service con l'oggetto ottenuto tramite Reflection
        sessionService = new SessionService(dbManagerInstance, daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    // =========================================================================
    // --- 1. TEST: APERTURA SESSIONE ---
    // =========================================================================

    @Test
    void testOpenSession_InsufficientFunds_ThrowsException() {
        Driver poorDriver = new Driver(0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.BASIC, 50.0, "Povero", "p@test.com", "pwd");
        ChargingStation station = ChargingStation.reconstitute(1L, null, null, "Stazione", null, 0.0, 0.0, ConnectorType.TYPE_2, 50.0, false, null, null, 0.0, 0, StationStatus.ACTIVE, null, null);

        ChargingStrategy strategyMock = mock(ChargingStrategy.class);
        doReturn(5.0).when(strategyMock).calculateCost(anyDouble(), eq(station), eq(poorDriver));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            sessionService.openSession(poorDriver, station, strategyMock, 20.0);
        });

        assertEquals("Saldo insufficiente: sono necessari fondi per almeno 5 kWh.", exception.getMessage());
        verify(daoFactoryMock, never()).createSessionDao(any());
    }

    @Test
    void testOpenSession_Success() throws Exception {
        Driver richDriver = new Driver(0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.PREMIUM, 50.0, "Ricco", "r@test.com", "pwd");
        richDriver.refund(new BigDecimal("100.00"));

        ChargingStation station = ChargingStation.reconstitute(1L, null, null, "Stazione", null, 0.0, 0.0, ConnectorType.TYPE_2, 50.0, false, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0, StationStatus.ACTIVE, null, null);

        ChargingStrategy strategyMock = mock(ChargingStrategy.class);
        doReturn("FAST").when(strategyMock).getName();
        doReturn(5.0).when(strategyMock).calculateCost(anyDouble(), eq(station), eq(richDriver));

        ChargingSession openedSession = sessionService.openSession(richDriver, station, strategyMock, 20.0);

        assertNotNull(openedSession);
        assertEquals("FAST", openedSession.getStrategyUsed());

        verify(sessionDaoMock, times(1)).save(openedSession);
        verify(stationDaoMock, times(1)).update(station);
        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();
    }

    @Test
    void testCloseSession_Success() throws Exception {
        Driver driver = new Driver(0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.BASIC, 50.0, "Test", "t@test.com", "pwd");

        ChargingStation station = ChargingStation.reconstitute(1L, null, null, "Stazione", null, 0.0, 0.0, ConnectorType.TYPE_2, 50.0, false, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0, StationStatus.ACTIVE, null, null);
        ChargingSession session = ChargingSession.open(driver, station, "FAST", 20.0);

        session.addTick(10.0, new BigDecimal("5.00"));

        sessionService.closeSession(session);

        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertEquals(StationStatus.ACTIVE, station.getStatus());

        verify(transactionDaoMock, times(1)).save(any());
        verify(stationDaoMock, times(1)).update(station);
        verify(sessionDaoMock, times(1)).update(session);
        verify(connectionMock, times(1)).commit();
    }

    @Test
    void testForceClose_WithRefund() throws Exception {
        Driver driver = new Driver(0.0, 0.0, ConnectorType.TYPE_2, SubscriptionPlan.BASIC, 50.0, "Test", "t@test.com", "pwd");
        driver.refund(new BigDecimal("50.00"));

        ChargingStation station = ChargingStation.reconstitute(1L, null, null, "Stazione", null, 0.0, 0.0, ConnectorType.TYPE_2, 50.0, false, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0, StationStatus.ACTIVE, null, null);
        ChargingSession session = ChargingSession.open(driver, station, "FAST", 20.0);

        sessionService.forceClose(session);

        assertEquals(StationStatus.ACTIVE, station.getStatus());

        verify(transactionDaoMock, times(1)).save(any());
        verify(userDaoMock, times(1)).update(driver);
        verify(connectionMock, times(1)).commit();
    }

    // =========================================================================
    // --- 3. TEST: GET ACTIVE SESSIONS ---
    // =========================================================================

    @Test
    void testGetActiveSessions() throws Exception {
        ChargingSession dummySession = mock(ChargingSession.class);
        List<ChargingSession> mockList = Collections.singletonList(dummySession);

        doReturn(mockList).when(sessionDaoMock).findActiveSessions();

        List<ChargingSession> result = sessionService.getActiveSessions();

        assertEquals(1, result.size());
        verify(sessionDaoMock, times(1)).findActiveSessions();
        verify(connectionMock, times(1)).close();
    }

    // =========================================================================
    // --- 4. TEST: METODI DI LETTURA E AGGREGAZIONE (Use Cases UI) ---
    // =========================================================================

    @Test
    void testCountSessionsByOperator_Success() throws Exception {
        StationOperator mockOperator = mock(StationOperator.class);
        doReturn(10L).when(mockOperator).getId();

        doReturn(25).when(sessionDaoMock).countByOperatorId(10L);

        int result = sessionService.countSessionsByOperator(mockOperator);

        assertEquals(25, result);
        verify(sessionDaoMock, times(1)).countByOperatorId(10L);
        verify(connectionMock, times(1)).close();
    }

    @Test
    void testCountSessionsByOperator_NullOperator() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sessionService.countSessionsByOperator(null);
        });
        assertEquals("L'operatore non può essere nullo.", exception.getMessage());
    }

    @Test
    void testCalculateTotalEnergySoldByOperator_Success() throws Exception {
        StationOperator mockOperator = mock(StationOperator.class);
        doReturn(15L).when(mockOperator).getId();

        doReturn(450.75).when(sessionDaoMock).sumEnergyByOperatorId(15L);

        double result = sessionService.calculateTotalEnergySoldByOperator(mockOperator);

        assertEquals(450.75, result);
        verify(sessionDaoMock, times(1)).sumEnergyByOperatorId(15L);
        verify(connectionMock, times(1)).close();
    }

    @Test
    void testCalculateTotalEnergySoldByOperator_NullOperator() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sessionService.calculateTotalEnergySoldByOperator(null);
        });
        assertEquals("L'operatore non può essere nullo.", exception.getMessage());
    }

    @Test
    void testGetSessionHistoryByDriver_Success() throws Exception {
        Driver mockDriver = mock(Driver.class);
        doReturn(5L).when(mockDriver).getId();

        ChargingSession session1 = mock(ChargingSession.class);
        ChargingSession session2 = mock(ChargingSession.class);
        List<ChargingSession> mockHistory = List.of(session1, session2);

        doReturn(mockHistory).when(sessionDaoMock).findCompletedByDriverId(5L);

        List<ChargingSession> result = sessionService.getSessionHistoryByDriver(mockDriver);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(sessionDaoMock, times(1)).findCompletedByDriverId(5L);
        verify(connectionMock, times(1)).close();
    }

    @Test
    void testGetSessionHistoryByDriver_NullDriver() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sessionService.getSessionHistoryByDriver(null);
        });
        assertEquals("Il driver non può essere nullo.", exception.getMessage());
    }

    @Test
    void testGetActiveSessionForDriver_ReturnsSession() throws Exception {
        Driver mockDriver = mock(Driver.class);
        doReturn(8L).when(mockDriver).getId();

        ChargingSession mockSession = mock(ChargingSession.class);
        doReturn(mockSession).when(sessionDaoMock).findActiveByDriverId(8L);

        ChargingSession result = sessionService.getActiveSessionForDriver(mockDriver);

        assertNotNull(result);
        assertEquals(mockSession, result);
        verify(sessionDaoMock, times(1)).findActiveByDriverId(8L);
        verify(connectionMock, times(1)).close();
    }

    @Test
    void testGetActiveSessionForDriver_ReturnsNull() throws Exception {
        Driver mockDriver = mock(Driver.class);
        doReturn(9L).when(mockDriver).getId();

        doReturn(null).when(sessionDaoMock).findActiveByDriverId(9L);

        ChargingSession result = sessionService.getActiveSessionForDriver(mockDriver);

        assertNull(result); // Corretto: il driver non ha sessioni in corso
        verify(sessionDaoMock, times(1)).findActiveByDriverId(9L);
        verify(connectionMock, times(1)).close();
    }

    @Test
    void testGetActiveSessionForDriver_NullDriver() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sessionService.getActiveSessionForDriver(null);
        });
        assertEquals("Il driver non può essere nullo.", exception.getMessage());
    }
}