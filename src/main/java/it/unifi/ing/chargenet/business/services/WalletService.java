package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;

public class WalletService {

    public Transaction fundWallet(Driver driver, double amount) {
        return null;
    }

    public Transaction changePlan(Driver driver, SubscriptionPlan plan) {
        return null;
    }

    // Aggiunto per permettere al SessionService di finalizzare il pagamento
    public Transaction processSessionPayment(Driver driver, double amount, String description) {
        return null;
    }
}