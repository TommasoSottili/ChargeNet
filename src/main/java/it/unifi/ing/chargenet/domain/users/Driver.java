package it.unifi.ing.chargenet.domain.users;

import java.math.BigDecimal;

public class Driver extends User {

    private Double latitude;
    private Double longitude;
    private SubscriptionPlan subscriptionPlan;
    private BigDecimal walletBalance;
    private Double batteryPercentage;
    private ConnectorType connectorType;

    protected  Driver() {
        super();
    }
    public Driver(Double latitude, Double longitude, ConnectorType connectorType, SubscriptionPlan subscriptionPlan, String name, String email, String password) {
        super(name, password, email, Role.DRIVER);
        this.latitude = latitude;
        this.longitude = longitude;
        this.connectorType = connectorType;
        this.subscriptionPlan = subscriptionPlan;
        this.walletBalance = BigDecimal.ZERO;
        this.batteryPercentage = null;
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
    public Double getBatteryPercentage() {
        return batteryPercentage;
    }
    public void setBatteryPercentage(Double batteryPercentage) {
        this.batteryPercentage = batteryPercentage;
    }
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
            throw new InsufficientBalanceException("Saldo insuficiente: impossibile addebitare " + amount + "£");
        }
        this.walletBalance = this.walletBalance.subtract(amount);
    }
    public void refund(BigDecimal amount) {
        this.walletBalance = this.walletBalance.add(amount);
    }
    public void updatePlan(SubscriptionPlan newPlan) {
        this.subscriptionPlan = newPlan;
    }
}

