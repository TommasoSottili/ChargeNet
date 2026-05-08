package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.domain.feedback.Rating;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;

public class RatingService {

    public Rating leaveRating(Driver driver, ChargingStation station, ChargingSession session, int stars, String comment) {
        return null;
    }

    public void checkRatingAlerts() {
        // Chiamato dal tuo GridMonitor per verificare se le medie sono crollate
    }
}