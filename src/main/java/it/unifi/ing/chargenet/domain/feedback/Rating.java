package it.unifi.ing.chargenet.domain.feedback;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.users.Driver;
import java.time.LocalDateTime;

public class Rating {

    private Long id;
    private Driver driver;
    private ChargingStation station;
    private ChargingSession session;
    private Integer stars;
    private String comment;
    private LocalDateTime createdAt;

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

    // Metodo per ricostruire un Rating dal Database aggirando le validazioni di "leave"
    public static Rating reconstitute(Long id, Driver driver, ChargingStation station,
                                      ChargingSession session, Integer stars,
                                      String comment, LocalDateTime createdAt) {
        Rating rating = new Rating(); // Usa il costruttore protected vuoto

        rating.id = id;
        rating.driver = driver;
        rating.station = station;
        rating.session = session;
        rating.stars = stars;
        rating.comment = comment;
        rating.createdAt = createdAt;

        return rating;
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

    public void setId(Long id) { this.id = id; }
}
