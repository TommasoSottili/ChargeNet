package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.feedback.RatingAlert;
import java.util.List;

public interface RatingAlertDao extends GenericDao<RatingAlert> {
    // Estrae tutti gli avvisi "Pending" che il Manager deve ancora gestire
    List<RatingAlert> findPendingAlerts();
}
