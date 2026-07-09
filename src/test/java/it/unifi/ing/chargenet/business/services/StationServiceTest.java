package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.business.exceptions.StationNotAvailableException;
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.business.exceptions.NoTransformerAvailableException;
import it.unifi.ing.chargenet.business.core.GridCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StationServiceTest {

    private StationService stationService;

    // Gestore per la connessione statica
    private MockedStatic<DatabaseManager> mockedDbManager;
    private MockedStatic<GridCluster> mockedGridCluster;
    private GridCluster gridClusterMock;

    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private StationDao stationDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        // 1. Setup Connessione
        connectionMock = mock(Connection.class);
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 2. Setup Factory e DAO
        daoFactoryMock = mock(DaoFactory.class);
        stationDaoMock = mock(StationDao.class);
        doReturn(stationDaoMock).when(daoFactoryMock).createStationDao(any(Connection.class));

        // 3. SETUP GRIDCLUSTER (Globale per tutti i test)
        mockedGridCluster = mockStatic(GridCluster.class);
        gridClusterMock = mock(GridCluster.class);
        mockedGridCluster.when(GridCluster::getInstance).thenReturn(gridClusterMock);

        // 4. Inizializzazione Service
        stationService = new StationService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) mockedDbManager.close();
        if (mockedGridCluster != null) mockedGridCluster.close(); // Fondamentale
    }

    @Test
    @DisplayName("Registrazione colonnina con successo")
    void testRegisterStation_Success() throws SQLException {
        // --- ARRANGE ---
        // Mockiamo gli oggetti complessi per non dover riempire i loro costruttori
        StationOperator mockOperator = mock(StationOperator.class);
        PowerTransformer mockTransformer = mock(PowerTransformer.class);

        BigDecimal tariff = new BigDecimal("0.65");

        // --- ACT ---
        ChargingStation result = stationService.registerStation(
                mockOperator,
                mockTransformer,
                "Colonnina Fast Milano",
                "Via Roma 1",
                45.4654,
                9.1859,
                ConnectorType.CCS_2,
                150.0,
                false,
                tariff
        );

        // --- ASSERT ---
        assertNotNull(result, "La stazione creata non deve essere null");
        assertEquals("Colonnina Fast Milano", result.getName(), "Il nome deve corrispondere");
        assertEquals(tariff, result.getTariffOperator(), "La tariffa deve essere mappata correttamente");

        // Verifiche comportamentali sul Database
        verify(connectionMock, times(1)).setAutoCommit(false);               // Transazione iniziata
        verify(stationDaoMock, times(1)).save(any(ChargingStation.class));   // Metodo save() chiamato
        verify(connectionMock, times(1)).commit();                           // Conferma effettuata
        verify(connectionMock, times(1)).close();                            // Connessione chiusa
    }

    @Test
    @DisplayName("Prenotazione effettuata con successo (Hold)")
    void testHold_Success() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        when(mockDriver.getId()).thenReturn(1L);
        when(mockDriver.getEmail()).thenReturn("mario@mail.com");

        ChargingStation mockStation = mock(ChargingStation.class);
        when(mockStation.getId()).thenReturn(100L);

        // Simuliamo che il database dia il via libera per l'update atomico
        when(stationDaoMock.acquireAtomicHold(100L, 1L)).thenReturn(true);

        // --- ACT ---
        stationService.hold(mockDriver, mockStation);

        // --- ASSERT ---
        // Verifichiamo la logica di dominio e di persistenza
        verify(mockStation, times(1)).setReserved(mockDriver); // L'oggetto in memoria è stato aggiornato?
        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(connectionMock, times(1)).commit();
    }

    @Test
    @DisplayName("Prenotazione fallita - Colonnina occupata")
    void testHold_StationNotAvailable() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        when(mockDriver.getId()).thenReturn(1L);

        ChargingStation mockStation = mock(ChargingStation.class);
        when(mockStation.getId()).thenReturn(100L);
        when(mockStation.getName()).thenReturn("Colonnina Milano");

        // Simuliamo che qualcun altro abbia già preso la colonnina (il DAO restituisce false)
        when(stationDaoMock.acquireAtomicHold(100L, 1L)).thenReturn(false);

        // --- ACT & ASSERT ---
        // QUI USIAMO LA TUA ECCEZIONE PERSONALIZZATA!
        StationNotAvailableException exception = assertThrows(StationNotAvailableException.class, () -> {
            stationService.hold(mockDriver, mockStation);
        });

        assertTrue(exception.getMessage().contains("non è più disponibile"));

        // Il rollback DEVE scattare
        verify(mockStation, never()).setReserved(any());
        verify(connectionMock, never()).commit();
        verify(connectionMock, times(1)).rollback();
    }

    @Test
    @DisplayName("Annullamento prenotazione con successo")
    void testCancelHold_Success() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        ChargingStation mockStation = mock(ChargingStation.class);

        // Il driver è effettivamente il proprietario della prenotazione
        when(mockStation.isReservedBy(mockDriver)).thenReturn(true);

        // --- ACT ---
        stationService.cancelHold(mockDriver, mockStation);

        // --- ASSERT ---
        verify(mockStation, times(1)).cancelHold();
        verify(stationDaoMock, times(1)).update(mockStation);
        verify(connectionMock, times(1)).commit();
    }

    @Test
    @DisplayName("Annullamento fallito - Non autorizzato")
    void testCancelHold_NotAuthorized() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        ChargingStation mockStation = mock(ChargingStation.class);

        // Un furbetto prova ad annullare la prenotazione di qualcun altro!
        when(mockStation.isReservedBy(mockDriver)).thenReturn(false);

        // --- ACT & ASSERT ---
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            stationService.cancelHold(mockDriver, mockStation);
        });

        assertTrue(exception.getMessage().contains("Non puoi annullare"));

        // Nessuna interazione col DB
        verify(mockStation, never()).cancelHold();
        verify(stationDaoMock, never()).update(any());
    }

    @Test
    @DisplayName("Pulizia massiva delle prenotazioni scadute")
    void testExpireHolds_Success() throws SQLException {
        // --- ACT ---
        stationService.expireHolds();

        // --- ASSERT ---
        verify(stationDaoMock, times(1)).expireHolds();
        verify(connectionMock, times(1)).commit();
    }

    @Test
    @DisplayName("Ricerca delle colonnine vicine disponibili")
    void testFindNearestAvailable_Success() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        when(mockDriver.getLatitude()).thenReturn(45.0);
        when(mockDriver.getLongitude()).thenReturn(9.0);
        when(mockDriver.getConnectorType()).thenReturn(ConnectorType.CCS_2);
        when(mockDriver.getId()).thenReturn(1L);   // il service lo passa come reservedByDriverId

        java.util.List<ChargingStation> mockList =
                java.util.Collections.singletonList(mock(ChargingStation.class));

        // Firma nuova: (lat, lng, type, excludeId=null, reservedByDriverId=1L)
        when(stationDaoMock.findNearestAvailable(45.0, 9.0, ConnectorType.CCS_2, null, 1L))
                .thenReturn(mockList);

        // --- ACT ---
        java.util.List<ChargingStation> result = stationService.findNearestAvailable(mockDriver);

        // --- ASSERT ---
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Registrazione con assegnazione automatica del trasformatore")
    void testRegisterStation_AutoAssignTransformer_Success() throws SQLException {
        // --- ARRANGE ---
        StationOperator mockOperator = mock(StationOperator.class);

        PowerTransformer t1 = mock(PowerTransformer.class);
        PowerTransformer t2 = mock(PowerTransformer.class);
        when(t1.getId()).thenReturn(1L);
        when(t2.getId()).thenReturn(2L);

        // CONFIGURAZIONE MOCK STATICO:
        // Quando viene chiamato getInstance(), restituisci un mock che al getTransformers() ritorna la lista
        GridCluster gridClusterMock = mock(GridCluster.class);
        mockedGridCluster.when(GridCluster::getInstance).thenReturn(gridClusterMock);
        when(gridClusterMock.getTransformers()).thenReturn(java.util.List.of(t1, t2));

        when(stationDaoMock.countByTransformer(1L)).thenReturn(2);
        when(stationDaoMock.countByTransformer(2L)).thenReturn(5);

        // --- ACT ---
        stationService.registerStation(mockOperator, "Stazione Test", "Via X", 0.0, 0.0,
                ConnectorType.CCS_2, 50.0, false, BigDecimal.TEN);

        // --- ASSERT ---
        verify(stationDaoMock).save(argThat(s -> s.getTransformer().getId() == 1L));
        verify(connectionMock).commit();
    }

    @Test
    @DisplayName("Registrazione fallita - Rete satura")
    void testRegisterStation_NoTransformerAvailable() throws SQLException {
        // --- ARRANGE ---
        StationOperator mockOperator = mock(StationOperator.class);
        PowerTransformer t1 = mock(PowerTransformer.class);
        when(t1.getId()).thenReturn(1L);

        // Simuliamo che il trasformatore sia al massimo (es. 10 stazioni)
        when(stationDaoMock.countByTransformer(1L)).thenReturn(10);

        // --- ACT & ASSERT ---
        assertThrows(NoTransformerAvailableException.class, () -> {
            stationService.registerStation(mockOperator, "Stazione Fallita", "Via Y", 0.0, 0.0,
                    ConnectorType.CCS_2, 50.0, false, BigDecimal.TEN);
        });

        // Verifichiamo che NON sia stato chiamato save() e che sia stato fatto il rollback
        verify(stationDaoMock, never()).save(any());
        verify(connectionMock, never()).commit();
    }

    @Test
    @DisplayName("Recupero stazioni operatore - Successo")
    void testGetStationsByOperator_Success() {
        // ARRANGE
        StationOperator mockOp = mock(StationOperator.class);
        when(mockOp.getId()).thenReturn(42L);
        List<ChargingStation> expected = java.util.Collections.singletonList(mock(ChargingStation.class));
        when(stationDaoMock.findByOperator(42L)).thenReturn(expected);

        // ACT
        List<ChargingStation> result = stationService.getStationsByOperator(mockOp);

        // ASSERT
        assertEquals(expected, result);
        verify(stationDaoMock).findByOperator(42L);
    }

    @Test
    @DisplayName("Recupero mappa stazioni - Filtraggio corretto")
    void testGetAllStations_Filtering() {
        // ARRANGE
        // Creiamo 3 stazioni con status diversi
        ChargingStation s1 = mock(ChargingStation.class);
        when(s1.getStatus()).thenReturn(it.unifi.ing.chargenet.domain.infrastructure.StationStatus.ACTIVE);

        ChargingStation s2 = mock(ChargingStation.class);
        when(s2.getStatus()).thenReturn(it.unifi.ing.chargenet.domain.infrastructure.StationStatus.PENDING_VERIFICATION);

        ChargingStation s3 = mock(ChargingStation.class);
        when(s3.getStatus()).thenReturn(it.unifi.ing.chargenet.domain.infrastructure.StationStatus.REJECTED);

        List<ChargingStation> allInDb = java.util.Arrays.asList(s1, s2, s3);
        when(stationDaoMock.findAll()).thenReturn(allInDb);

        // ACT
        List<ChargingStation> result = stationService.getAllStations();

        // ASSERT
        assertEquals(1, result.size(), "Solo la stazione ACTIVE dovrebbe passare il filtro");
        assertTrue(result.contains(s1), "La stazione ACTIVE deve essere presente");
        assertFalse(result.contains(s2), "La stazione PENDING non deve essere presente");
        assertFalse(result.contains(s3), "La stazione REJECTED non deve essere presente");
    }

    @Test
    @DisplayName("Recupero dashboard sovraccarico - Successo")
    void testGetOverloadedStations_Success() {
        // ARRANGE
        List<ChargingStation> expected = java.util.Collections.singletonList(mock(ChargingStation.class));
        when(stationDaoMock.findByStatus(it.unifi.ing.chargenet.domain.infrastructure.StationStatus.OVERLOADED))
                .thenReturn(expected);

        // ACT
        List<ChargingStation> result = stationService.getOverloadedStations();

        // ASSERT
        assertEquals(expected, result);
        verify(stationDaoMock).findByStatus(it.unifi.ing.chargenet.domain.infrastructure.StationStatus.OVERLOADED);
    }

    @Test
    @DisplayName("Recupero delle colonnine in attesa di verifica")
    void testGetPendingStations_Success() throws Exception {
        ChargingStation pending1 = mock(ChargingStation.class);
        ChargingStation pending2 = mock(ChargingStation.class);
        List<ChargingStation> mockList = List.of(pending1, pending2);

        // Il service deve chiedere al DAO ESATTAMENTE lo stato PENDING_VERIFICATION
        doReturn(mockList).when(stationDaoMock).findByStatus(StationStatus.PENDING_VERIFICATION);

        List<ChargingStation> result = stationService.getPendingStations();

        assertEquals(2, result.size());
        verify(stationDaoMock, times(1)).findByStatus(StationStatus.PENDING_VERIFICATION);
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Nessuna colonnina in attesa: lista vuota")
    void testGetPendingStations_Empty() throws Exception {
        doReturn(java.util.Collections.emptyList()).when(stationDaoMock).findByStatus(StationStatus.PENDING_VERIFICATION);

        List<ChargingStation> result = stationService.getPendingStations();

        assertTrue(result.isEmpty());
        verify(connectionMock, times(1)).close();
    }
}