package it.unifi.ing.chargenet.domain.financials;

import it.unifi.ing.chargenet.domain.users.Driver;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private final Long id;
    private final Driver driver;
    private final TransactionType type;
    private final BigDecimal amount;
    private final Double kwh;
    private final String description;
    private final LocalDateTime createdAt;

    protected Transaction() {
        this.id = null;
        this.driver = null;
        this.type = null;
        this.amount = null;
        this.kwh = null;
        this.description = null;
        this.createdAt = null;
    }

    private Transaction(Driver driver, TransactionType type, BigDecimal amount, Double kwh, String description) {
        this.id = null;
        this.driver = driver;
        this.type = type;
        this.amount = amount;
        this.kwh = kwh;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public static Transaction create(Driver driver, TransactionType type, BigDecimal amount,
                                     Double kwh, String description) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Errore di validazione: l'importo della transazione non può essere zero.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Errore di validazione: la transazione deve avere una descrizione valida.");
        }
        return new Transaction(driver, type, amount, kwh, description);
    }

    public Long getId() {
        return id;
    }

    public Driver getDriver() {
        return driver;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Double getKwh() {
        return kwh;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

