package it.unifi.ing.chargenet.domain.users;

import java.math.BigDecimal;

public class Driver extends User {

    private Double latitude;
    private Double longitude;
    private SubscriptionPlan subscriptionPlan;
    private BigDecimal walletBalance;
    private Double batteryCapacity;
    private ConnectorType connectorType;

    protected  Driver() {
        super();
    }

    public Driver(String name, String email, String password,
                  ConnectorType connectorType, Double latitude, Double longitude) {
        super(name, email, password, Role.DRIVER);

        if (connectorType == null) {
            throw new IllegalArgumentException(
                    "Il tipo di connettore è obbligatorio per un Driver");
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException(
                    "Le coordinate sono obbligatorie per un Driver");
        }

        this.connectorType = connectorType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.subscriptionPlan = SubscriptionPlan.BASIC;   // default alla registrazione
        this.walletBalance = BigDecimal.ZERO;
        this.batteryCapacity = 50.0;  // vedi nota sotto su questo campo
    }

    public static Driver reconstitute(Long id) {
        Driver d = new Driver();
        d.setId(id);
        return d;
    }

    public Double getLatitude() {
        return latitude;
    }
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    public Double getLongitude() {
        return longitude;
    }
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    public SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan;
    }
    public BigDecimal getWalletBalance() {
        return walletBalance;
    }
    public void setWalletBalance(BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }
    public Double getBatteryCapacity() { return batteryCapacity; }
    public void setBatteryCapacity(Double batteryCapacity) { this.batteryCapacity = batteryCapacity; }
    public ConnectorType getConnectorType() {
        return connectorType;
    }
    public void setConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
    }

    public double getDiscount() {
        if (this.subscriptionPlan != null) {
            return this.subscriptionPlan.getDiscount();
        }
        return 0.0;
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.walletBalance.compareTo(amount) >= 0;
    }

    public void charge(BigDecimal amount) {
        if (!hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException("Saldo insufficiente: impossibile addebitare " + amount + "€");
        }
        this.walletBalance = this.walletBalance.subtract(amount);
    }

    public void refund(BigDecimal amount) {
        this.walletBalance = this.walletBalance.add(amount);
    }
    public void updatePlan(SubscriptionPlan newPlan) {
        BigDecimal fee = newPlan.getMonthlyFee();
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            charge(fee);  // riusa la validazione esistente — lancia InsufficientBalanceException se serve
        }
        this.subscriptionPlan = newPlan;
    }

    /**
     * Aggiorna la posizione corrente del Driver simulando una lettura GPS.
     */
    public void updatePosition(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Metodo statico per ricostruire l'oggetto dal Database
    public static Driver reconstitute(Long id, String name, String email, String password,
                                      Double latitude, Double longitude, ConnectorType connectorType,
                                      SubscriptionPlan subscriptionPlan, Double batteryCapacity,
                                       BigDecimal walletBalance) {

        // Usiamo il costruttore protected vuoto
        Driver driver = new Driver();

        // Usiamo i setter per i campi ereditati dal padre (User)
        driver.setId(id);
        driver.setName(name);
        driver.setEmail(email);
        driver.setPassword(password);
        driver.setRole(Role.DRIVER);

        // Assegniamo direttamente i campi specifici del Driver
        driver.latitude = latitude;
        driver.longitude = longitude;
        driver.connectorType = connectorType;
        driver.subscriptionPlan = subscriptionPlan;
        driver.batteryCapacity = batteryCapacity;
        driver.walletBalance = walletBalance;

        return driver;
    }
}

