package it.unifi.ing.chargenet.domain.feedback;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.users.Driver;
import java.time.LocalDateTime;

public class Rating {

    private final Long id;
    private final Driver driver;
    private final ChargingStation station;
    private final ChargingSession session;
    private final Integer stars;
    private final String comment;
    private final LocalDateTime createdAt;

    protected Rating() {
        this.id = null;
        this.driver = null;
        this.station = null;
        this.session = null;
        this.stars = null;
        this.comment = null;
        this.createdAt = null;
    }

    private Rating(Driver driver, ChargingStation station, ChargingSession session, Integer stars, String comment) {
        this.id = null;
        this.driver = driver;
        this.station = station;
        this.session = session;
        this.stars = stars;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
    }

    public static Rating leave(Driver driver, ChargingStation station, ChargingSession session, Integer stars, String comment) {
        if (stars == null || stars < 1 || stars > 5) {
            throw new InvalidRatingException("Errore di validazione: il rating deve essere un valore intero compreso tra 1 e 5 stelle.");
        }
        return new Rating(driver, station, session, stars, comment);
    }
    public Long getId() {
        return id;
    }

    public Driver getDriver() {
        return driver;
    }

    public ChargingStation getStation() {
        return station;
    }

    public ChargingSession getSession() {
        return session;
    }

    public Integer getStars() {
        return stars;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
