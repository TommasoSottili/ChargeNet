package it.unifi.ing.chargenet.business.core;

import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.dao.interfaces.TransformerDao;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

public class GridCluster {

    // Istanza statica
    private static GridCluster instance;

    // Strutture dati in memoria (Cache)
    private List<PowerTransformer> transformers;
    private Map<Long, List<ChargingStation>> stationsByTransformer;
    private List<ChargingStation> allStations;
    
    private GridCluster() {
        this.transformers = new ArrayList<>();
        this.stationsByTransformer = new HashMap<>();
        this.allStations = new ArrayList<>();
    }

    // Singleton sincronizzato sulla firma
    public static synchronized GridCluster getInstance() {
        if (instance == null) {
            instance = new GridCluster();
        }
        return instance;
    }

    // Inizializzazione: riempie la memoria all'avvio dell'app
    public void init(TransformerDao tDao, StationDao sDao) {
        this.transformers = tDao.findAll();
        this.allStations = sDao.findAll();

        for (PowerTransformer t : transformers) {
            stationsByTransformer.put(t.getId(), new ArrayList<>());
        }

        for (ChargingStation station : allStations) {
            Long tId = station.getTransformer().getId();
            if (stationsByTransformer.containsKey(tId)) {
                stationsByTransformer.get(tId).add(station);
            }
        }
    }

    public void registerNewStation(ChargingStation s) {
        this.allStations.add(s);
        if (stationsByTransformer.containsKey(s.getTransformer().getId())) {
            stationsByTransformer.get(s.getTransformer().getId()).add(s);
        }
    }

    // Metodi di accesso rapidissimo (Tempo costante O(1))
    public List<PowerTransformer> getTransformers() {
        return this.transformers;
    }

    public List<ChargingStation> getStationsForTransformer(Long id) {
        return stationsByTransformer.getOrDefault(id, new ArrayList<>());
    }

    // Ricerca della colonnina più vicina
    public List<ChargingStation> getNearestAvailable(double lat, double lon) {
        List<ChargingStation> availableStations = new ArrayList<>();

        for (ChargingStation station : allStations) {
            if (station.getStatus() == StationStatus.ACTIVE) {
                availableStations.add(station);
            }
        }

        availableStations.sort(Comparator.comparingDouble(s -> calculateDistance(lat, lon, s.getLatitude(), s.getLongitude())));

        return availableStations.subList(0, Math.min(5, availableStations.size()));
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Nota: in un progetto reale useresti la Formula di Haversine per la curvatura terrestre.
        // Per l'esame, la distanza euclidea è perfetta e molto più veloce.
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lon1 - lon2, 2));
    }
}