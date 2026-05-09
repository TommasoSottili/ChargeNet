package it.unifi.ing.chargenet.business.core;

import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.dao.interfaces.TransformerDao;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import java.util.List;
import java.util.Collections;

public class GridCluster {

    // Struttura Singleton
    private static GridCluster instance;

    private GridCluster() {}

    public static synchronized GridCluster getInstance() {
        if (instance == null) {
            instance = new GridCluster();
        }
        return instance;
    }

    public void init(TransformerDao tDao, StationDao sDao) {
    }

    public List<PowerTransformer> getTransformers() {
        return Collections.emptyList();
    }

    public List<ChargingStation> getStationsForTransformer(int id) {
        return Collections.emptyList();
    }

    // Serve al collega (StationService) per trovare le stazioni
    public List<ChargingStation> getNearestAvailable(double lat, double lon) {
        return Collections.emptyList();
    }
}