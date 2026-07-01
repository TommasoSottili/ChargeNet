package it.unifi.ing.chargenet.business.core;

import it.unifi.ing.chargenet.business.services.RatingService;
import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.interfaces.TransformerDao;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GridMonitorTest {

    private GridMonitor gridMonitor;
    private StationService stationServiceMock;
    private SessionService sessionServiceMock;
    private RatingService ratingServiceMock;

    @BeforeEach
    void setUp() throws Exception {
        // Resettiamo e prepariamo il GridCluster Singleton che il Monitor usa internamente
        Field instanceField = GridCluster.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        stationServiceMock = mock(StationService.class);
        sessionServiceMock = mock(SessionService.class);
        ratingServiceMock = mock(RatingService.class);

        gridMonitor = new GridMonitor(stationServiceMock, sessionServiceMock, ratingServiceMock);
    }

    @Test
    void testOnTickDelegationsAndHeatSimulation() throws Exception {
        // ARRANGEMENT: Inizializziamo il GridCluster con un trasformatore "spia" (usando un'istanza reale non mockata)
        // in modo da poter verificare l'aumento della temperatura.
        PowerTransformer spyTransformer = PowerTransformer.reconstitute(1L, "Spy", 20.0, 0.0);

        TransformerDao tDao = mock(TransformerDao.class);
        StationDao sDao = mock(StationDao.class);
        when(tDao.findAll()).thenReturn(Collections.singletonList(spyTransformer));
        when(sDao.findAll()).thenReturn(new ArrayList<>());
        GridCluster.getInstance().init(tDao, sDao);

        // Prepariamo una sessione attiva associata a quel trasformatore
        ChargingStation mockStation = ChargingStation.reconstitute(10L, null, spyTransformer, "S", null, 0.0, 0.0, null, 50.0, false, null, null, 0.0, 0, StationStatus.ACTIVE, null, null);
        ChargingSession activeSession = ChargingSession.reconstitute(100L, null, mockStation, ChargingType.FAST, 20.0, 20.0, 0.0, java.math.BigDecimal.ZERO, SessionStatus.ACTIVE, java.time.LocalDateTime.now(), null);

        List<ChargingSession> activeSessionsList = new ArrayList<>();
        activeSessionsList.add(activeSession);

        when(sessionServiceMock.getActiveSessions()).thenReturn(activeSessionsList);

        // ACT: Invochiamo il metodo privato onTick() tramite Reflection per simulare lo scorrere di un ciclo
        Method onTickMethod = GridMonitor.class.getDeclaredMethod("onTick");
        onTickMethod.setAccessible(true);
        onTickMethod.invoke(gridMonitor);

        // ASSERT
        // 1. Verifichiamo che i servizi esterni delegati siano stati chiamati
        verify(stationServiceMock, times(1)).expireHolds();
        verify(ratingServiceMock, times(1)).checkRatingAlerts();

        // 2. Verifichiamo che sia stato aggiunto il tick di calcolo della sessione con la strategy FAST
        verify(sessionServiceMock, times(1)).addTick(eq(activeSession), any());

        // 3. Verifichiamo l'impatto fisico sul modello di dominio: la temperatura del trasformatore deve essere salita!
        // Visto che FAST genera un incremento termico pari a 2.0, la temperatura iniziale (20.0) deve essere aumentata
        assertTrue(spyTransformer.getTemperature() > 20.0, "La temperatura del trasformatore deve essere aumentata grazie alla sessione attiva");
    }
}