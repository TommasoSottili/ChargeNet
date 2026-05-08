package it.unifi.ing.chargenet.domain.users;

public enum ConnectorType {
    TYPE_2(22.0),
    CCS_2(350.0),
    CHADEMO(100.0);

    private final double maxPowerKw;
    ConnectorType(double maxPowerKw) {
        this.maxPowerKw = maxPowerKw;
    }
    public double getMaxPowerKw() {
        return maxPowerKw;
    }
}
