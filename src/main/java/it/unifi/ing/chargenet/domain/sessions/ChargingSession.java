package it.unifi.ing.chargenet.domain.sessions;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.InsufficientBalanceException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ChargingSession {

    private Long id;
    private Driver driver;
    private ChargingStation station;
    private ChargingType strategyUsed;
    private Double batteryStart;
    private Double batteryCurrent;
    private Double kwhDelivered;
    private BigDecimal costTotal;
    private SessionStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    protected ChargingSession() {};

    private ChargingSession(Driver driver, ChargingStation station, ChargingType strategyUsed, Double batteryStart) {
        this.driver = driver;
        this.station = station;
        this.strategyUsed = strategyUsed;
        this.batteryStart = batteryStart;
        this.batteryCurrent = batteryStart;
        this.kwhDelivered = 0.0;
        this.costTotal = BigDecimal.ZERO;
        this.status = SessionStatus.ACTIVE;
        this.openedAt = LocalDateTime.now();
        this.closedAt = null;
    }

    public static ChargingSession open(Driver driver, ChargingStation station, ChargingType strategy, Double batteryStart) {

        boolean isAvailable = station.isAvailableFor(driver.getConnectorType()) || station.isReservedBy(driver);
        if (!isAvailable) {
            throw new StationNotAvailableException("La colonnina " + station.getName() + " non è disponibile o non è compatibile col tuo veicolo.");
        }

        // Prezzo minimo per 5 kWh calcolato dal dominio (sconto solo sulla quota piattaforma).
        // Stesso metodo usato dalle strategie → gate e tariffazione coerenti.
        BigDecimal minimumRequired = station.priceFor(5.0, driver);

        if (!driver.hasSufficientBalance(minimumRequired)) {
            throw new InsufficientBalanceException("Credito insufficiente per avviare la sessione. È richiesto un saldo di almeno " +
                    minimumRequired + "€.");
        }

        return new ChargingSession(driver, station, strategy, batteryStart);
    }

    public static ChargingSession reconstitute(Long id, Driver driver, ChargingStation station,
                                                ChargingType strategyUsed, Double batteryStart,
                                                Double batteryCurrent, Double kwhDelivered,
                                                BigDecimal costTotal, SessionStatus status,
                                                LocalDateTime openedAt, LocalDateTime closedAt) {
        ChargingSession session = new ChargingSession(); // Usa il costruttore protected dall'interno!
        session.id = id;
        session.driver = driver;
        session.station = station;
        session.strategyUsed = strategyUsed;
        session.batteryStart = batteryStart;
        session.batteryCurrent = batteryCurrent;
        session.kwhDelivered = kwhDelivered;
        session.costTotal = costTotal;
        session.status = status;
        session.openedAt = openedAt;
        session.closedAt = closedAt;
        return session;
    }

    public void addTick(Double kwhThisTick, BigDecimal costThisTick) {
        if (isActive()) {
            this.kwhDelivered += kwhThisTick;
            this.costTotal = this.costTotal.add(costThisTick);

            // Calcola quanta percentuale rappresenta l'energia appena immessa
            double percentageGained = (kwhThisTick / driver.getBatteryCapacity()) * 100.0;
            // Aggiorna la batteria corrente, assicurandoci che non superi mai il 100%
            this.batteryCurrent = Math.min(100.0, this.batteryCurrent + percentageGained);
            // Opzionale: Se la batteria raggiunge il 100%, la sessione potrebbe auto-completarsi
            if (this.batteryCurrent >= 100.0) {
                this.complete();
            }
        }
    }

    public void complete() {
        if (isActive()) {
            this.status = SessionStatus.COMPLETED;
            this.closedAt = LocalDateTime.now();
        }
    }

    public void interrupt() {
        if (isActive()) {
            this.status = SessionStatus.INTERRUPTED_THERMAL;
            this.closedAt = LocalDateTime.now();
        }
    }

    public boolean isActive() {
        return this.status == SessionStatus.ACTIVE;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Driver getDriver() { return driver; }
    public ChargingStation getStation() { return station; }

    public ChargingType getStrategyUsed() { return strategyUsed; }

    public Double getBatteryStart() { return batteryStart; }

    public Double getBatteryCurrent() { return batteryCurrent; }
    public void setBatteryCurrent(Double batteryCurrent) { this.batteryCurrent = batteryCurrent; }

    public Double getKwhDelivered() { return kwhDelivered; }
    public BigDecimal getCostTotal() { return costTotal; }

    public SessionStatus getStatus() { return status; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
}
