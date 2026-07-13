package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidationServiceTest {

    private ValidationService validationService;

    private MockedStatic<DatabaseManager> mockedDbManager;

    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private StationDao stationDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        // 1. Setup Connessione DB
        connectionMock = mock(Connection.class);
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 2. Setup Factory e DAO
        daoFactoryMock = mock(DaoFactory.class);
        stationDaoMock = mock(StationDao.class);
        doReturn(stationDaoMock).when(daoFactoryMock).createStationDao(any(Connection.class));

        validationService = new ValidationService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) mockedDbManager.close();
    }

    @Test
    @DisplayName("Approvazione stazione con successo")
    void testApprove_Success() throws SQLException {
        // --- ARRANGE ---
        ChargingStation mockStation = mock(ChargingStation.class);
        java.math.BigDecimal tariff = new java.math.BigDecimal("0.45");

        // --- ACT ---
        validationService.approve(mockStation, tariff);

        // --- ASSERT ---
        // 1. Verifichiamo che sia stato chiamato il metodo di business sull'oggetto di dominio
        verify(mockStation, times(1)).activate(tariff);

        // 2. Verifichiamo le interazioni con il database
        verify(connectionMock, times(1)).setAutoCommit(false); // Transazione aperta?
        verify(stationDaoMock, times(1)).update(mockStation);  // Salvataggio nel DB?
        verify(connectionMock, times(1)).commit();             // Conferma definitiva?
        verify(connectionMock, times(1)).close();              // Chiusura connessione?
    }

    @Test
    @DisplayName("Approvazione fallita per tariffa non valida")
    void testApprove_InvalidTariff() throws SQLException {
        // --- ARRANGE ---
        ChargingStation mockStation = mock(ChargingStation.class);
        java.math.BigDecimal invalidTariff = new java.math.BigDecimal("-0.10"); // Tariffa negativa!

        // --- ACT & ASSERT ---
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            validationService.approve(mockStation, invalidTariff);
        });

        assertTrue(exception.getMessage().contains("non negativo"), "Il messaggio d'errore deve essere esplicito");

        // Il blocco preventivo deve impedire l'apertura della connessione e il salvataggio
        verify(stationDaoMock, never()).update(any());
    }

    @Test
    @DisplayName("Rifiuto stazione con successo")
    void testReject_Success() throws SQLException {
        // --- ARRANGE ---
        ChargingStation mockStation = mock(ChargingStation.class);
        String motivation = "Danni strutturali evidenti";

        // --- ACT ---
        validationService.reject(mockStation, motivation);

        // --- ASSERT ---
        // 1. Verifichiamo la logica di dominio
        verify(mockStation, times(1)).reject();

        // 2. Verifichiamo le interazioni con il database
        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(stationDaoMock, times(1)).update(mockStation);
        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Rifiuto fallito per motivazione assente")
    void testReject_MissingMotivation() throws SQLException {
        // --- ARRANGE ---
        ChargingStation mockStation = mock(ChargingStation.class);
        String invalidMotivation = "   "; // Spazi vuoti (invalidi)

        // --- ACT & ASSERT ---
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            validationService.reject(mockStation, invalidMotivation);
        });

        assertTrue(exception.getMessage().contains("obbligatorio fornire una motivazione"));

        // Il database non deve essere toccato
        verify(stationDaoMock, never()).update(any());
    }
}