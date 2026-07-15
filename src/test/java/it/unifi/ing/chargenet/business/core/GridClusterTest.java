package it.unifi.ing.chargenet.business.core;

import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.interfaces.TransformerDao;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class GridClusterTest {

    private GridCluster gridCluster;
    private TransformerDao transformerDaoMock;
    private StationDao stationDaoMock;

    @BeforeEach
    void setUp() throws Exception {
        // TRUCCO ARCHITETTURALE: Resettiamo il Singleton tramite Reflection
        // per garantire che ogni test parta da una memoria pulita.
        Field instanceField = GridCluster.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        gridCluster = GridCluster.getInstance();
        transformerDaoMock = Mockito.mock(TransformerDao.class);
        stationDaoMock = Mockito.mock(StationDao.class);
    }

    @Test
    void testInitAndAggregation() {
        // ARRANGEMENT: Prepariamo 2 trasformatori e 3 stazioni
        PowerTransformer t1 = PowerTransformer.reconstitute(1L, "T1", 25.0, 0.0);
        PowerTransformer t2 = PowerTransformer.reconstitute(2L, "T2", 25.0, 0.0);

        ChargingStation s1 = ChargingStation.reconstitute(10L, null, t1, "S1", null, 0.0, 0.0, null, 50.0, false, null, null, 0.0, 0, StationStatus.ACTIVE, null, null);
        ChargingStation s2 = ChargingStation.reconstitute(20L, null, t1, "S2", null, 0.0, 0.0, null, 50.0, false, null, null, 0.0, 0, StationStatus.ACTIVE, null, null);
        ChargingStation s3 = ChargingStation.reconstitute(30L, null, t2, "S3", null, 0.0, 0.0, null, 50.0, false, null, null, 0.0, 0, StationStatus.ACTIVE, null, null);

        when(transformerDaoMock.findAll()).thenReturn(Arrays.asList(t1, t2));
        when(stationDaoMock.findAll()).thenReturn(Arrays.asList(s1, s2, s3));

        // ACT
        gridCluster.init(transformerDaoMock, stationDaoMock);

        // ASSERT
        assertEquals(2, gridCluster.getTransformers().size());

        // Verifichiamo che il raggruppamento (mappa in memoria) sia corretto
        List<ChargingStation> stationsT1 = gridCluster.getStationsForTransformer(1L);
        List<ChargingStation> stationsT2 = gridCluster.getStationsForTransformer(2L);

        assertEquals(2, stationsT1.size(), "Il trasformatore 1 deve avere 2 stazioni associate");
        assertEquals(1, stationsT2.size(), "Il trasformatore 2 deve avere 1 stazione associata");
        assertTrue(stationsT1.contains(s1));
        assertTrue(stationsT2.contains(s3));
    }
}