package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.financials.TransactionType;
import java.util.List;

public interface TransactionDao extends GenericDao<Transaction> {
    List<Transaction> findByDriver (Long driverId);
    List<Transaction> findByType (TransactionType type);
}