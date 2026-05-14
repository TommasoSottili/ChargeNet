package it.unifi.ing.chargenet.domain.users;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;

import java.math.BigDecimal;
;

public class StationOperator  extends User {

    private BigDecimal totalEarnings;

    protected StationOperator() {
        super();
    }

    public StationOperator(String name, String password, String email) {
        super(name, password, email, Role.STATION_OPERATOR);
    }

    public static StationOperator reconstitute(Long id) {
        StationOperator operator = new StationOperator();
        operator.setId(id);
        return operator;
    }

    public void addEarnings(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.totalEarnings = this.totalEarnings.add(amount);
        }
    }

    public BigDecimal getTotalEarnings() {
        return totalEarnings;
    }
    public void setTotalEarnings(BigDecimal totalEarnings) {
        this.totalEarnings = totalEarnings;
    }
}
