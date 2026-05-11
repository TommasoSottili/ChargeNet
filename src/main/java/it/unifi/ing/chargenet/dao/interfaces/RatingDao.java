package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.feedback.Rating;
import java.util.List;

public interface RatingDao extends GenericDao<Rating> {
    // Estrae tutte le recensioni di una specifica stazione per calcolare la media
    List<Rating> findByStation(Long stationId);
}

