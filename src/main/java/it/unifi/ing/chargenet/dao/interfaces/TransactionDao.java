package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.users.Driver;
import java.util.List;

public interface TransactionDao extends GenericDao<Transaction> {

    // Serve per generare l'estratto conto del Driver nel suo profilo
    List<Transaction> findByDriver(Driver driver);
}