package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.TransactionDao;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.financials.TransactionType;
import it.unifi.ing.chargenet.domain.users.Driver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletServiceTest {

    private WalletService walletService;

    // Gestore di Mockito per intercettare i metodi statici
    private MockedStatic<DatabaseManager> mockedDbManager;

    // Dipendenze mockate
    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private UserDao userDaoMock;
    private TransactionDao transactionDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        // 1. Creiamo la finta connessione
        connectionMock = mock(Connection.class);

        // 2. Intercettiamo globalmente la chiamata a DatabaseManager.getConnection()
        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        // 3. Creiamo i mock per Factory e DAO
        daoFactoryMock = mock(DaoFactory.class);
        userDaoMock = mock(UserDao.class);
        transactionDaoMock = mock(TransactionDao.class);

        // 4. Istruiamo la Factory sui DAO da restituire per questa connessione
        doReturn(userDaoMock).when(daoFactoryMock).createUserDao(any(Connection.class));
        doReturn(transactionDaoMock).when(daoFactoryMock).createTransactionDao(any(Connection.class));

        // 5. Istanziamo il Service
        walletService = new WalletService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        // Rilasciamo il blocco statico per non interferire con gli altri test
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    // =========================================================================
    // --- 1. TEST: RICARICA PORTAFOGLIO (FUND WALLET) ---
    // =========================================================================

    @Test
    @DisplayName("Ricarica portafoglio con successo")
    void testFundWallet_Success() throws SQLException {
        // --- ARRANGE ---
        // Creiamo il Driver (rispettando l'ordine corretto: nome, email, password)
        Driver mockDriver = new Driver(null, null, null, null, null, "Mario Rossi", "mario@mail.com", "pwd");

        // Assicuriamoci che il portafoglio parta da zero (o il valore di default)
        BigDecimal initialBalance = mockDriver.getWalletBalance();
        BigDecimal amountToAdd = new BigDecimal("50.00");

        // --- ACT ---
        Transaction resultTx = walletService.fundWallet(mockDriver, amountToAdd);

        // --- ASSERT ---
        // 1. Verifiche sull'oggetto Transaction restituito
        assertNotNull(resultTx, "La transazione non deve essere null");
        assertEquals(TransactionType.FUND_ADDED, resultTx.getType(), "Il tipo di transazione deve essere FUND_ADDED");
        assertEquals(amountToAdd, resultTx.getAmount(), "L'importo della transazione deve coincidere con la ricarica");

        // 2. Verifica dello stato del Driver in memoria
        assertEquals(initialBalance.add(amountToAdd), mockDriver.getWalletBalance(), "Il saldo del Driver deve essere stato incrementato");

        // 3. Verifiche comportamentali (Transazioni sul DB)
        verify(connectionMock, times(1)).setAutoCommit(false); // Inizio transazione
        verify(userDaoMock, times(1)).update(mockDriver);      // Aggiornamento saldo driver
        verify(transactionDaoMock, times(1)).save(resultTx);   // Salvataggio della ricevuta
        verify(connectionMock, times(1)).commit();             // Conferma operazioni
        verify(connectionMock, times(1)).close();              // Chiusura connessione
    }

    @Test
    @DisplayName("Ricarica fallita per importo non valido")
    void testFundWallet_InvalidAmount() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = new Driver(null, null, null, null, null, "Mario", "mario@mail.com", "pwd");
        BigDecimal invalidAmount = new BigDecimal("-10.00"); // Importo negativo!

        // --- ACT & ASSERT ---
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            walletService.fundWallet(mockDriver, invalidAmount);
        });

        assertTrue(exception.getMessage().contains("maggiore di zero"), "Il messaggio d'errore deve essere esplicito");

        // --- VERIFICHE COMPORTAMENTALI ---
        // Essendo fallito al primo if, NON deve aver neanche aperto la connessione!
        mockedDbManager.verify(DatabaseManager::getConnection, never());
    }

    @Test
    @DisplayName("Cambio piano con successo (PREMIUM a 25.00€)")
    void testChangePlan_Success() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = new Driver(null, null, null, null, null, "Mario Rossi", "mario@mail.com", "pwd");

        // Diamo al driver 100€, così può permettersi ampiamente l'abbonamento PREMIUM (25€)
        mockDriver.refund(new BigDecimal("100.00"));

        // Usiamo il tuo enum esatto
        it.unifi.ing.chargenet.domain.users.SubscriptionPlan plan = it.unifi.ing.chargenet.domain.users.SubscriptionPlan.PREMIUM;

        // --- ACT ---
        Transaction resultTx = walletService.changePlan(mockDriver, plan);

        // --- ASSERT ---
        assertNotNull(resultTx, "La transazione non deve essere null");
        assertEquals(TransactionType.SUBSCRIPTION, resultTx.getType(), "Il tipo deve essere SUBSCRIPTION");

        // Verifica che l'importo sia negativo (un addebito) e corrisponda esattamente a -25.00
        assertEquals(new BigDecimal("-25.00"), resultTx.getAmount(), "L'addebito deve corrispondere a -25.00€");

        // Verifiche comportamentali sul DB
        verify(connectionMock, times(1)).setAutoCommit(false); // Inizio transazione
        verify(userDaoMock, times(1)).update(mockDriver);      // Aggiornamento piano utente nel DB
        verify(transactionDaoMock, times(1)).save(resultTx);   // Salvataggio ricevuta nel DB
        verify(connectionMock, times(1)).commit();             // Conferma operazioni
    }

    @Test
    @DisplayName("Cambio piano fallito per fondi insufficienti")
    void testChangePlan_InsufficientFunds() throws SQLException {
        // --- ARRANGE ---
        Driver mockDriver = new Driver(null, null, null, null, null, "Povero", "povero@mail.com", "pwd");
        // Il portafoglio parte da 0. Non può permettersi un piano PREMIUM da 25€.

        it.unifi.ing.chargenet.domain.users.SubscriptionPlan plan = it.unifi.ing.chargenet.domain.users.SubscriptionPlan.PREMIUM;

        // --- ACT & ASSERT ---
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            walletService.changePlan(mockDriver, plan);
        });

        assertTrue(exception.getMessage().contains("Fondi insufficienti"), "Deve segnalare la mancanza di fondi");

        // --- VERIFICHE COMPORTAMENTALI ---
        verify(userDaoMock, never()).update(any());      // NON deve aggiornare l'utente
        verify(transactionDaoMock, never()).save(any()); // NON deve salvare la transazione
        verify(connectionMock, never()).commit();        // NON deve confermare
        verify(connectionMock, times(1)).rollback();     // DEVE annullare le operazioni aperte
        verify(connectionMock, times(1)).close();        // DEVE chiudere il rubinetto
    }
}