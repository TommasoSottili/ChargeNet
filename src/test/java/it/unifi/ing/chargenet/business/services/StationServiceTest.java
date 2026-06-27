package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.business.services.InvalidStationParametersException;
import it.unifi.ing.chargenet.domain.users.Driver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StationServiceTest {

    private StationService stationService;

    // Gestore per la connessione statica
    private MockedStatic<DatabaseManager> mockedDbManager;

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

        // 3. Inizializzazione Service
        stationService = new StationService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
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

        // Creiamo una finta lista di risultati
        java.util.List<ChargingStation> mockList = java.util.Collections.singletonList(mock(ChargingStation.class));

        when(stationDaoMock.findNearestAvailable(45.0, 9.0, ConnectorType.CCS_2, null))
                .thenReturn(mockList);

        // --- ACT ---
        java.util.List<ChargingStation> result = stationService.findNearestAvailable(mockDriver);

        // --- ASSERT ---
        assertNotNull(result);
        assertEquals(1, result.size());

        // Verifichiamo che la connessione sia stata chiusa anche in sola lettura
        verify(connectionMock, times(1)).close();
    }
}