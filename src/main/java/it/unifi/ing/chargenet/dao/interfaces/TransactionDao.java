package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.financials.Transaction;

public interface TransactionDao extends GenericDao<Transaction> {
    // Eredita automaticamente save, update, findById, ecc. dal GenericDao.
    // Il documento non specifica metodi extra qui.
}