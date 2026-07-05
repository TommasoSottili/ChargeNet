package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.business.core.GridCluster;
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.observer.TransformerEvent;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.util.Collections;

import static org.mockito.Mockito.*;

class LoadBalancerServiceTest {

    private LoadBalancerService loadBalancerService;

    // Dipendenze Mockate
    private SessionService sessionServiceMock;
    private DaoFactory daoFactoryMock;
    private StationDao stationDaoMock;
    private Connection connectionMock;

    // Mock Statici per bypassare i problemi architetturali
    private MockedStatic<DatabaseManager> mockedDbManagerStatic;
    private MockedStatic<GridCluster> mockedGridClusterStatic;
    private GridCluster gridClusterMock;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Inizializziamo i mock di base
        sessionServiceMock = mock(SessionService.class);
        daoFactoryMock = mock(DaoFactory.class);
        stationDaoMock = mock(StationDao.class);
        connectionMock = mock(Connection.class);

        doReturn(stationDaoMock).when(daoFactoryMock).createStationDao(any());

        // 2. IL MIRACOLO PER I METODI STATICI: Dirottiamo getConnection()
        mockedDbManagerStatic = mockStatic(DatabaseManager.class);
        mockedDbManagerStatic.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 3. RISOLUZIONE COSTRUTTORE PRIVATO TRAMITE REFLECTION
        Constructor<DatabaseManager> constructor = DatabaseManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        DatabaseManager dbManagerInstance = constructor.newInstance();

        // 4. Gestione sicura del Singleton GridCluster
        gridClusterMock = mock(GridCluster.class);
        mockedGridClusterStatic = mockStatic(GridCluster.class);
        mockedGridClusterStatic.when(GridCluster::getInstance).thenReturn(gridClusterMock);

        // 5. Creiamo il service da testare
        loadBalancerService = new LoadBalancerService(sessionServiceMock, daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        // Chiusura fondamentale per non inquinare gli altri test
        if (mockedDbManagerStatic != null) {
            mockedDbManagerStatic.close();
        }
        if (mockedGridClusterStatic != null) {
            mockedGridClusterStatic.close();
        }
    }

    // =========================================================================
    // --- 1. TEST: ALLARME TERMICO (THERMAL_ALERT) ---
    // =========================================================================

    @Test
    void testUpdate_ThermalAlert_ClosesSessionsAndOverloadsStations() throws Exception {
        // ARRANGEMENT
        PowerTransformer transformerMock = mock(PowerTransformer.class);
        doReturn(100L).when(transformerMock).getId();
        doReturn("Trans-1").when(transformerMock).getName();

        ChargingStation stationMock = mock(ChargingStation.class);
        doReturn(10L).when(stationMock).getId();

        ChargingSession sessionMock = mock(ChargingSession.class);
        doReturn(stationMock).when(sessionMock).getStation();

        doReturn(Collections.singletonList(stationMock))
                .when(gridClusterMock).getStationsForTransformer(100L);

        doReturn(Collections.singletonList(sessionMock))
                .when(sessionServiceMock).getActiveSessions();

        // ACT
        loadBalancerService.update(transformerMock, TransformerEvent.THERMAL_ALERT);

        // ASSERT
        verify(stationMock, times(1)).setOverloaded();
        verify(stationDaoMock, times(1)).update(stationMock);
        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();

        verify(sessionServiceMock, times(1)).forceClose(sessionMock);
    }

    // =========================================================================
    // --- 2. TEST: RAFFREDDAMENTO COMPLETATO (COOLING_COMPLETE) ---
    // =========================================================================

    @Test
    void testUpdate_CoolingComplete_RestoresOverloadedStations() throws Exception {
        // ARRANGEMENT
        PowerTransformer transformerMock = mock(PowerTransformer.class);
        doReturn(100L).when(transformerMock).getId();
        doReturn("Trans-1").when(transformerMock).getName();

        ChargingStation overloadedStationMock = mock(ChargingStation.class);
        doReturn(10L).when(overloadedStationMock).getId();
        doReturn(StationStatus.OVERLOADED).when(overloadedStationMock).getStatus();

        ChargingStation activeStationMock = mock(ChargingStation.class);
        doReturn(20L).when(activeStationMock).getId();
        doReturn(StationStatus.ACTIVE).when(activeStationMock).getStatus();

        doReturn(java.util.Arrays.asList(overloadedStationMock, activeStationMock))
                .when(gridClusterMock).getStationsForTransformer(100L);

        // ACT
        loadBalancerService.update(transformerMock, TransformerEvent.COOLING_COMPLETE);

        // ASSERT
        verify(overloadedStationMock, times(1)).setActive();
        verify(stationDaoMock, times(1)).update(overloadedStationMock);

        verify(activeStationMock, never()).setActive();
        verify(stationDaoMock, never()).update(activeStationMock);

        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();
    }
}