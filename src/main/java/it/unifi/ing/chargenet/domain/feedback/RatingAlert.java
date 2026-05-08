package it.unifi.ing.chargenet.domain.feedback;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import java.time.LocalDateTime;

public class RatingAlert {

    private Long id;
    private ChargingStation station;
    private Double avgAtCreation;
    private RatingAlertStatus status;
    private String managerNote;
    private LocalDateTime createdAt;

    protected RatingAlert() {};

    private RatingAlert(ChargingStation station, Double avgAtCreation) {
        this.station = station;
        this.avgAtCreation = avgAtCreation;
        this.status = RatingAlertStatus.PENDING;
        this.managerNote = null;
        this.createdAt = LocalDateTime.now();
    }
    public static RatingAlert create(ChargingStation station, Double avgAtCreation) {
        return new RatingAlert(station, avgAtCreation);
    }
    public void resolveSuspend(String note) {
        if (isPending()) {
            this.status = RatingAlertStatus.RESOLVED_SUSPENDED;
            this.managerNote = note;
        }
    }
    public void resolveIgnore(String note) {
        if (isPending()) {
            this.status = RatingAlertStatus.RESOLVED_IGNORED;
            this.managerNote = note;
        }
    }
    public boolean isPending() {
        return this.status == RatingAlertStatus.PENDING;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public ChargingStation getStation() {
        return station;
    }

    public Double getAvgAtCreation() {
        return avgAtCreation;
    }

    public RatingAlertStatus getStatus() {
        return status;
    }

    public String getManagerNote() {
        return managerNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
