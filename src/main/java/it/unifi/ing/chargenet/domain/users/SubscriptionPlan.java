package it.unifi.ing.chargenet.domain.users;

import java.math.BigDecimal;

public enum SubscriptionPlan {
    BASIC(0.0,BigDecimal.ZERO),
    PLUS(0.15, new BigDecimal("9.00")),
    PREMIUM(0.30,new BigDecimal("25.00")),;

    private final double discount;
    private final BigDecimal monthlyFee;

    SubscriptionPlan(double discount,  BigDecimal monthlyFee) {
        this.discount = discount;
        this.monthlyFee = monthlyFee;
    }

    public double getDiscount() {
        return discount;
    }
    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }
}
