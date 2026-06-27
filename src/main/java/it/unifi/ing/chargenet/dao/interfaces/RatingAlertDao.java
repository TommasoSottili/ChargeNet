package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.feedback.RatingAlert;
import it.unifi.ing.chargenet.domain.feedback.RatingAlertStatus;

import java.util.List;

public interface RatingAlertDao extends GenericDao<RatingAlert> {
    boolean existsOpenAlertForStation(Long stationId);
    List<RatingAlert> findByStatus(RatingAlertStatus status);
    List<RatingAlert> findByStation(Long stationId);
}

