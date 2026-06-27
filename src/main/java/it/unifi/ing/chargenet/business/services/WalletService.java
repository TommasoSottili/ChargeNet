package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.dao.interfaces.TransactionDao;
import it.unifi.ing.chargenet.domain.financials.TransactionType;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;

public class WalletService {


    private final DaoFactory daoFactory;

    public WalletService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public Transaction fundWallet(Driver driver, BigDecimal amount) {
        // Validazione preventiva della business logic usando i metodi di BigDecimal
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("L'importo della ricarica deve essere valido e maggiore di zero.");
        }

        Connection connection = null;
        try {
            // 1. Apriamo la connessione ed entriamo in modalità "Transazione"
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);

            // 2. Creiamo i DAO legati a QUESTA specifica connessione temporanea
            UserDao userDao = daoFactory.createUserDao(connection);
            TransactionDao transactionDao = daoFactory.createTransactionDao(connection);

            // 3. LOGICA DI DOMINIO: Aggiorniamo lo stato del Driver in memoria
            // Assicurati che il metodo refund() nella classe Driver accetti un parametro BigDecimal!
            driver.refund(amount);

            // 4. CREAZIONE DELLA RICEVUTA: Usiamo direttamente il BigDecimal
            Transaction transaction = Transaction.create(
                    driver,
                    TransactionType.FUND_ADDED,
                    amount,
                    0.0,                        // I kWh sono 0 perché è una ricarica monetaria
                    "Ricarica portafoglio (Accredito di " + amount + "€)"
            );

            // 5. PERSISTENZA: Diciamo ai DAO di preparare le query di update e save
            userDao.update(driver);
            transactionDao.save(transaction);

            // 6. COMMIT: Se siamo arrivati qui senza errori, salviamo tutto definitivamente nel DB
            connection.commit();
            System.out.println("[WalletService] Ricarica di " + amount + "€ completata con successo per: " + driver.getEmail());

            return transaction;

        } catch (Exception e) {
            // Se qualcosa si è rotto (es. database disconnesso a metà), annulliamo ogni modifica
            rollbackQuietly(connection);
            throw new RuntimeException("Errore critico durante l'esecuzione della ricarica nel WalletService", e);
        } finally {
            // In ogni caso, chiudiamo il rubinetto della connessione al DB
            closeQuietly(connection);
        }
    }

    public Transaction changePlan(Driver driver, SubscriptionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Il piano di abbonamento specificato non è valido.");
        }

        Connection connection = null;
        try {
            // 1. Apriamo la connessione ed entriamo in modalità "Transazione"
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);

            // 2. Creiamo i DAO
            UserDao userDao = daoFactory.createUserDao(connection);
            TransactionDao transactionDao = daoFactory.createTransactionDao(connection);

            // Otteniamo il costo del piano.
            // NOTA: Se getMonthlyFee() restituisce ancora un double, cambialo in BigDecimal nella classe SubscriptionPlan!
            BigDecimal fee = plan.getMonthlyFee();

            // Controllo preventivo dei fondi (fondamentale per non mandare il driver "in rosso")
            if (driver.getWalletBalance().compareTo(fee) < 0) {
                throw new IllegalStateException("Fondi insufficienti nel portafoglio per attivare il piano: " + plan.name());
            }

            // 3. LOGICA DI DOMINIO: Aggiorniamo lo stato del Driver
            // Come da documentazione, usiamo il metodo interno della tua classe Driver
            driver.updatePlan(plan);

            // 4. CREAZIONE DELLA RICEVUTA: L'importo è negativo (fee.negate()) perché è un'uscita dal portafoglio
            Transaction transaction = Transaction.create(
                    driver,
                    TransactionType.SUBSCRIPTION,
                    fee.negate(),
                    0.0,
                    "Attivazione piano mensile: " + plan.name()
            );

            // 5. PERSISTENZA: Diciamo ai DAO di preparare le query
            userDao.update(driver);
            transactionDao.save(transaction);

            // 6. COMMIT: Salvataggio definitivo
            connection.commit();
            System.out.println("[WalletService] Cambio piano a " + plan.name() + " completato con successo per: " + driver.getEmail());

            return transaction;

        } catch (Exception e) {
            rollbackQuietly(connection);
            // Rilanciamo le eccezioni note per farle gestire alla UI di JavaFX
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new RuntimeException("Errore critico durante il cambio del piano di abbonamento", e);
        } finally {
            closeQuietly(connection);
        }
    }

    // Aggiunto per permettere al SessionService di finalizzare il pagamento
    public Transaction processSessionPayment(Driver driver, double amount, String description) {
        return null;
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try { connection.rollback(); } catch (SQLException ex) { /* Log silenzioso */ }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ex) { /* Log silenzioso */ }
        }
    }
}