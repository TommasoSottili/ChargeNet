package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.RatingAlertDao;
import it.unifi.ing.chargenet.dao.interfaces.RatingDao;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.feedback.Rating;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.users.Driver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RatingServiceTest {

    private RatingService ratingService;

    // Gestore di Mockito per intercettare la connessione statica
    private MockedStatic<DatabaseManager> mockedDbManager;

    // Dipendenze mockate
    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private RatingDao ratingDaoMock;
    private StationDao stationDaoMock;
    private RatingAlertDao ratingAlertDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        // 1. Creiamo la finta connessione
        connectionMock = mock(Connection.class);

        // 2. Intercettiamo globalmente DatabaseManager.getConnection()
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 3. Creiamo i mock per Factory e DAO
        daoFactoryMock = mock(DaoFactory.class);
        ratingDaoMock = mock(RatingDao.class);
        stationDaoMock = mock(StationDao.class);
        ratingAlertDaoMock = mock(RatingAlertDao.class);

        // 4. Istruiamo la Factory sui DAO da restituire
        doReturn(ratingDaoMock).when(daoFactoryMock).createRatingDao(any(Connection.class));
        doReturn(stationDaoMock).when(daoFactoryMock).createStationDao(any(Connection.class));
        doReturn(ratingAlertDaoMock).when(daoFactoryMock).createRatingAlertDao(any(Connection.class));

        // 5. Istanziamo il Service
        ratingService = new RatingService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    @Test
    @DisplayName("Recensione inserita con successo (Nessun Alert scatenato)")
    void testLeaveRating_Success_NoAlert() throws SQLException {
        // --- ARRANGE ---
        // Mockiamo gli oggetti di dominio per non dover riempire costruttori chilometrici
        Driver mockDriver = mock(Driver.class);
        when(mockDriver.getId()).thenReturn(1L);

        ChargingStation mockStation = mock(ChargingStation.class);
        when(mockStation.getId()).thenReturn(1L);

        ChargingSession mockSession = mock(ChargingSession.class);
        when(mockSession.getId()).thenReturn(1L);

        // Il driver NON ha ancora recensito questa sessione
        when(ratingDaoMock.existsByDriverAndSession(1L, 1L)).thenReturn(false);

        // FINGIAMO LA RISPOSTA DEL DB DOPO IL RICALCOLO DELLA MEDIA
        // Creiamo una stazione aggiornata finta con media alta (es. 4.5) in modo che NON scatti l'allarme
        ChargingStation updatedStationMock = mock(ChargingStation.class);
        when(updatedStationMock.getId()).thenReturn(1L);
        when(updatedStationMock.getAverageRating()).thenReturn(4.5);
        when(updatedStationMock.getTotalRatings()).thenReturn(10);

        when(stationDaoMock.findById(1L)).thenReturn(updatedStationMock);

        // --- ACT ---
        Rating result = ratingService.leaveRating(mockDriver, mockStation, mockSession, 5, "Servizio eccellente!");

        // --- ASSERT ---
        assertNotNull(result, "Il rating creato non deve essere null");
        assertEquals(5, result.getStars(), "Le stelle devono corrispondere");
        assertEquals("Servizio eccellente!", result.getComment(), "Il commento deve corrispondere");

        // Verifiche comportamentali
        verify(connectionMock, times(1)).setAutoCommit(false);     // Transazione iniziata?
        verify(ratingDaoMock, times(1)).save(any(Rating.class));   // Rating salvato?
        verify(ratingDaoMock, times(1)).recalculateAverage(1L);    // Media ricalcolata?

        // Verifica di sicurezza vitale: Visto che la media è 4.5, l'Alert NON deve essere stato salvato!
        verify(ratingAlertDaoMock, never()).save(any());

        verify(connectionMock, times(1)).commit();                 // Transazione confermata?
    }

    @Test
    @DisplayName("Recensione inserita e Allarme Generato (Media < 2.0 e >= 5 recensioni)")
    void testLeaveRating_TriggersAlert() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        when(mockDriver.getId()).thenReturn(1L);

        ChargingStation mockStation = mock(ChargingStation.class);
        when(mockStation.getId()).thenReturn(1L);

        ChargingSession mockSession = mock(ChargingSession.class);
        when(mockSession.getId()).thenReturn(1L);

        // Nessuna recensione duplicata
        when(ratingDaoMock.existsByDriverAndSession(1L, 1L)).thenReturn(false);

        // FINGIAMO LA RISPOSTA DEL DB: Questa volta la stazione sta andando malissimo!
        ChargingStation updatedStationMock = mock(ChargingStation.class);
        when(updatedStationMock.getId()).thenReturn(1L);
        when(updatedStationMock.getAverageRating()).thenReturn(1.5); // Media sotto 2.0!
        when(updatedStationMock.getTotalRatings()).thenReturn(5);    // Ha raggiunto la soglia di 5!

        when(stationDaoMock.findById(1L)).thenReturn(updatedStationMock);

        // Nessun alert già aperto per questa stazione
        when(ratingAlertDaoMock.existsOpenAlertForStation(1L)).thenReturn(false);

        // --- ACT ---
        // Il guidatore lascia una recensione da 1 stella
        Rating result = ratingService.leaveRating(mockDriver, mockStation, mockSession, 1, "Disastro totale!");

        // --- ASSERT ---
        assertNotNull(result);

        // Verifiche comportamentali cruciali
        verify(ratingDaoMock, times(1)).save(any(Rating.class));
        verify(ratingDaoMock, times(1)).recalculateAverage(1L);

        // LA PROVA DEL NOVE: Il sistema DEVE aver salvato un nuovo Alert!
        verify(ratingAlertDaoMock, times(1)).save(any());

        verify(connectionMock, times(1)).commit();
    }

    @Test
    @DisplayName("Recensione fallita per duplicato (IllegalStateException)")
    void testLeaveRating_Duplicate() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = mock(Driver.class);
        when(mockDriver.getId()).thenReturn(1L);

        ChargingStation mockStation = mock(ChargingStation.class);
        ChargingSession mockSession = mock(ChargingSession.class);
        when(mockSession.getId()).thenReturn(1L);

        // Diciamo al DAO che questo guidatore ha GIÀ recensito questa sessione
        when(ratingDaoMock.existsByDriverAndSession(1L, 1L)).thenReturn(true);

        // --- ACT & ASSERT ---
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            ratingService.leaveRating(mockDriver, mockStation, mockSession, 5, "Ci riprovo!");
        });

        assertTrue(exception.getMessage().contains("già inserito una recensione"), "Il messaggio deve indicare il duplicato");

        // VERIFICHE COMPORTAMENTALI (Sicurezza Database)
        verify(ratingDaoMock, never()).save(any());     // NON deve salvare la recensione
        verify(ratingDaoMock, never()).recalculateAverage(anyLong()); // NON deve ricalcolare nulla
        verify(ratingAlertDaoMock, never()).save(any()); // NON deve generare alert
        verify(connectionMock, never()).commit();       // NON deve committare
        verify(connectionMock, times(1)).rollback();    // DEVE annullare la transazione
        verify(connectionMock, times(1)).close();       // DEVE chiudere la connessione
    }

    // =========================================================================
    // --- 3. TEST: CONTROLLO MASSIVO DEGLI ALERT (BATCH JOB) ---
    // =========================================================================

    @Test
    @DisplayName("Controllo massivo degli Alert su tutte le stazioni")
    void testCheckRatingAlerts_MassiveCheck() throws SQLException {
        // --- ARRANGE ---
        // Creiamo due stazioni finte: una "Buona" e una "Pessima"
        ChargingStation goodStation = mock(ChargingStation.class);
        when(goodStation.getId()).thenReturn(1L);
        when(goodStation.getAverageRating()).thenReturn(4.8);
        when(goodStation.getTotalRatings()).thenReturn(10);

        ChargingStation badStation = mock(ChargingStation.class);
        when(badStation.getId()).thenReturn(2L);
        when(badStation.getAverageRating()).thenReturn(1.2); // Sotto il 2.0!
        when(badStation.getTotalRatings()).thenReturn(8);    // Almeno 5 recensioni!

        // Simuliamo che il findById (chiamato dentro il metodo privato) restituisca le stesse stazioni
        when(stationDaoMock.findById(1L)).thenReturn(goodStation);
        when(stationDaoMock.findById(2L)).thenReturn(badStation);

        // Simuliamo che non ci siano alert già aperti per nessuna delle due
        when(ratingAlertDaoMock.existsOpenAlertForStation(anyLong())).thenReturn(false);

        // Il findAll del DAO restituisce la nostra lista di 2 stazioni
        java.util.List<ChargingStation> allStations = java.util.Arrays.asList(goodStation, badStation);
        when(stationDaoMock.findAll()).thenReturn(allStations);

        // --- ACT ---
        ratingService.checkRatingAlerts();

        // --- ASSERT ---
        // Verifiche comportamentali sul loop
        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(stationDaoMock, times(1)).findAll();

        // Il metodo privato ricalcola la media per entrambe le stazioni
        verify(ratingDaoMock, times(1)).recalculateAverage(1L);
        verify(ratingDaoMock, times(1)).recalculateAverage(2L);

        // IL CUORE DEL TEST: L'alert deve essere salvato SOLO 1 VOLTA (per la badStation)
        verify(ratingAlertDaoMock, times(1)).save(any());

        verify(connectionMock, times(1)).commit(); // Tutto salvato in blocco
        verify(connectionMock, times(1)).close();
    }
}