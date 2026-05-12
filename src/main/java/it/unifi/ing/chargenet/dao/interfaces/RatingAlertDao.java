package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.feedback.RatingAlert;

public interface RatingAlertDao extends GenericDao<RatingAlert> {
    boolean existsOpenAlertForStation(Long stationId);
}