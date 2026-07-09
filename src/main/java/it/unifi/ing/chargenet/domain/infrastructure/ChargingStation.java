package it.unifi.ing.chargenet.domain.infrastructure;

import it.unifi.ing.chargenet.domain.users.StationOperator;
import  it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import java.time.LocalDateTime;
import java.math.BigDecimal;


public class ChargingStation {

    private Long id;
    private StationOperator operator;
    private PowerTransformer transformer;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private ConnectorType connectorType;
    private Double powerKw;
    private Boolean isSolarPowered;
    private BigDecimal tariffOperator;
    private BigDecimal tariffPlatform;
    private Double averageRating;
    private Integer totalRatings;
    private StationStatus status;
    private Driver reservedBy;
    private LocalDateTime expirationTimeStamp;

    protected  ChargingStation() {};

    private ChargingStation(StationOperator operator, PowerTransformer transformer, String name,
                            String address, Double latitude, Double longitude,
                            ConnectorType connectorType, Double powerKw,
                            Boolean isSolarPowered, BigDecimal tariffOperator) {
        this.operator = operator;
        this.transformer = transformer;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.connectorType = connectorType;
        this.powerKw = powerKw;
        this.isSolarPowered = isSolarPowered;
        this.tariffOperator = tariffOperator;
        this.tariffPlatform = BigDecimal.ZERO;
        this.averageRating = 0.0;
        this.totalRatings = 0;
        this.status = StationStatus.PENDING_VERIFICATION;
        this.reservedBy = null;
        this.expirationTimeStamp = null;
    }

    public static  ChargingStation register(StationOperator operator, PowerTransformer transformer,
                                            String name, String address, Double lat, Double lng,
                                            ConnectorType connectorType, Double powerKw,
                                            Boolean isSolar, BigDecimal tariff) {
        if (lat < -90.0 || lat > 90.0) {
            throw new InvalidStationParametersException("Latitudine non valida");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new InvalidStationParametersException("Longitudine non valida");
        }
        // 2. Validazione potenza generale
        if (powerKw < 7.0 || powerKw > 350.0) {
            throw new InvalidStationParametersException("Potenza non valida. Deve essere compresa tra 7 kW e 350 kW.");
        }
        if (powerKw > connectorType.getMaxPowerKw()) {
            throw new InvalidStationParametersException("Errore di sicurezza: La potenza richiesta (" + powerKw +
                    " kW) supera il limite fisico supportato dal connettore " + connectorType.name() +
                    " (" + connectorType.getMaxPowerKw() + " kW).");
        }
        if (tariff.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidStationParametersException("La tariffa dell'operatore deve essere strettamente positiva.");
        }
        return new ChargingStation(operator, transformer, name, address, lat, lng, connectorType, powerKw, isSolar, tariff);
    }

    public static ChargingStation reconstitute(
            Long id, StationOperator operator, PowerTransformer transformer, String name, String address,
            Double latitude, Double longitude, ConnectorType connectorType, Double powerKw,
            Boolean isSolarPowered, java.math.BigDecimal tariffOperator, java.math.BigDecimal tariffPlatform,
            Double averageRating, Integer totalRatings, StationStatus status,
            Driver reservedBy, java.time.LocalDateTime expirationTimestamp) {

        // Crea una stazione fittizia aggirando la validazione del tuo metodo register()
        ChargingStation station = new ChargingStation();

        station.id = id;
        station.operator = operator;
        station.transformer = transformer;
        station.name = name;
        station.address = address;
        station.latitude = latitude;
        station.longitude = longitude;
        station.connectorType = connectorType;
        station.powerKw = powerKw;
        station.isSolarPowered = isSolarPowered;
        station.tariffOperator = tariffOperator;
        station.tariffPlatform = tariffPlatform;
        station.averageRating = averageRating;
        station.totalRatings = totalRatings;
        station.status = status;
        station.expirationTimeStamp = expirationTimestamp;
        station.reservedBy = reservedBy;

        return station;
    }

    public static ChargingStation reconstitute(Long id) {
        ChargingStation s = new ChargingStation();
        s.id = id;
        return s;
    }

    /**
     * Prezzo di una ricarica per questo Driver, secondo la tariffa a doppio livello.
     * Lo sconto del piano si applica SOLO alla quota piattaforma (regola di dominio).
     */
    public BigDecimal priceFor(double kwh, Driver driver) {
        BigDecimal discount = (driver != null && driver.getSubscriptionPlan() != null)
                ? BigDecimal.valueOf(driver.getSubscriptionPlan().getDiscount())
                : BigDecimal.ZERO;

        BigDecimal platformAfterDiscount = tariffPlatform.multiply(BigDecimal.ONE.subtract(discount));
        BigDecimal ratePerKwh = tariffOperator.add(platformAfterDiscount);

        return ratePerKwh.multiply(BigDecimal.valueOf(kwh))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isAvailableFor(ConnectorType type) {
        return this.status == StationStatus.ACTIVE && this.connectorType == type;
    }

    public boolean isReservedBy(Driver driver) {
        return this.status == StationStatus.RESERVED &&
                this.reservedBy != null &&
                driver != null &&
                this.reservedBy.getId().equals(driver.getId());
    }

    /**
     * Rimuove la prenotazione dalla colonnina: azzera il riferimento al Driver
     * e la scadenza. Da chiamare quando la colonnina torna disponibile, così
     * il campo reserved_by_id non resta popolato con un dato ormai obsoleto.
     */
    public void clearReservation() {
        this.reservedBy = null;
        this.expirationTimeStamp = null;
    }

    public void activate(BigDecimal platformTariff) {
        this.status = StationStatus.ACTIVE;
        this.tariffPlatform = platformTariff;
    }

    public void reject() {
        this.status = StationStatus.REJECTED;
    }
    public void setReserved(Driver driver) {
        this.reservedBy = driver;
        this.status = StationStatus.RESERVED;
        this.expirationTimeStamp = LocalDateTime.now().plusMinutes(15);
    }

    public void cancelHold() {
        this.status = StationStatus.ACTIVE;
        this.reservedBy = null;
        this.expirationTimeStamp = null;
    }

    public void setBusy() {
        this.status = StationStatus.BUSY;
    }
    public void setActive() {
        this.status = StationStatus.ACTIVE;
    }

    public void setOverloaded() {
        this.status = StationStatus.OVERLOADED;
    }
    public void setSuspended() {
        this.status = StationStatus.SUSPENDED;
    }

    public void updateRating(int newStars) {
        this.totalRatings++;
        this.averageRating = ((this.averageRating * (this.totalRatings - 1)) + newStars) / this.totalRatings;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StationOperator getOperator() { return operator; }
    public PowerTransformer getTransformer() { return transformer; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public ConnectorType getConnectorType() { return connectorType; }
    public Double getPowerKw() { return powerKw; }
    public Boolean getSolarPowered() { return isSolarPowered; }

    public BigDecimal getTariffOperator() { return tariffOperator; }
    public BigDecimal getTariffPlatform() { return tariffPlatform; }

    public Double getAverageRating() { return averageRating; }
    public Integer getTotalRatings() { return totalRatings; }

    public StationStatus getStatus() { return status; }

    public Driver getReservedBy() { return reservedBy; }
    public LocalDateTime getExpirationTimestamp() { return expirationTimeStamp; }
}
