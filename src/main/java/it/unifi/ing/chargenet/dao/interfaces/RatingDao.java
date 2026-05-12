package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.feedback.Rating;

public interface RatingDao extends GenericDao<Rating> {
    boolean existsByDriverAndSession(Long driverId, Long sessionId);
    void recalculateAverage(Long stationId); // Implicito nella descrizione del RatingService
}