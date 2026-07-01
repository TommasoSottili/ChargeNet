package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.interfaces.TransactionDao;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.financials.TransactionType;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
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
    private MockedStatic<DatabaseManager> mockedDbManager;
    private DaoFactory daoFactoryMock;
    private Connection connectionMock;
    private UserDao userDaoMock;
    private TransactionDao transactionDaoMock;

    @BeforeEach
    void setUp() throws SQLException {
        connectionMock = mock(Connection.class);

        mockedDbManager = mockStatic(DatabaseManager.class);
        mockedDbManager.when(DatabaseManager::getConnection).thenReturn(connectionMock);

        daoFactoryMock = mock(DaoFactory.class);
        userDaoMock = mock(UserDao.class);
        transactionDaoMock = mock(TransactionDao.class);

        doReturn(userDaoMock).when(daoFactoryMock).createUserDao(any(Connection.class));
        doReturn(transactionDaoMock).when(daoFactoryMock).createTransactionDao(any(Connection.class));

        walletService = new WalletService(daoFactoryMock);
    }

    @AfterEach
    void tearDown() {
        if (mockedDbManager != null) {
            mockedDbManager.close();
        }
    }

    // Helper: costruttore di REGISTRAZIONE. Va benissimo qui perché nessun
    // test di questo file richiede un piano diverso da BASIC o coordinate
    // specifiche: il default del costruttore (BASIC, wallet a zero) È
    // esattamente lo stato di partenza che serve.
    private Driver newTestDriver(String name, String email) {
        return new Driver(name, email, "pwd", ConnectorType.TYPE_2, 43.7696, 11.2558);
    }

    @Test
    @DisplayName("Ricarica portafoglio con successo")
    void testFundWallet_Success() throws SQLException {
        Driver mockDriver = newTestDriver("Mario Rossi", "mario@mail.com");

        BigDecimal initialBalance = mockDriver.getWalletBalance();
        BigDecimal amountToAdd = new BigDecimal("50.00");

        Transaction resultTx = walletService.fundWallet(mockDriver, amountToAdd);

        assertNotNull(resultTx, "La transazione non deve essere null");
        assertEquals(TransactionType.FUND_ADDED, resultTx.getType(), "Il tipo di transazione deve essere FUND_ADDED");
        assertEquals(amountToAdd, resultTx.getAmount(), "L'importo della transazione deve coincidere con la ricarica");
        assertEquals(initialBalance.add(amountToAdd), mockDriver.getWalletBalance(), "Il saldo del Driver deve essere stato incrementato");

        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(userDaoMock, times(1)).update(mockDriver);
        verify(transactionDaoMock, times(1)).save(resultTx);
        verify(connectionMock, times(1)).commit();
        verify(connectionMock, times(1)).close();
    }

    @Test
    @DisplayName("Ricarica fallita per importo non valido")
    void testFundWallet_InvalidAmount() throws SQLException {
        Driver mockDriver = newTestDriver("Mario", "mario@mail.com");
        BigDecimal invalidAmount = new BigDecimal("-10.00");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                walletService.fundWallet(mockDriver, invalidAmount)
        );

        assertTrue(exception.getMessage().contains("maggiore di zero"), "Il messaggio d'errore deve essere esplicito");
        mockedDbManager.verify(DatabaseManager::getConnection, never());
    }

    @Test
    @DisplayName("Cambio piano con successo (PREMIUM a 25.00€)")
    void testChangePlan_Success() throws SQLException {
        Driver mockDriver = newTestDriver("Mario Rossi", "mario@mail.com");
        mockDriver.refund(new BigDecimal("100.00"));

        it.unifi.ing.chargenet.domain.users.SubscriptionPlan plan =
                it.unifi.ing.chargenet.domain.users.SubscriptionPlan.PREMIUM;

        Transaction resultTx = walletService.changePlan(mockDriver, plan);

        assertNotNull(resultTx, "La transazione non deve essere null");
        assertEquals(TransactionType.SUBSCRIPTION, resultTx.getType(), "Il tipo deve essere SUBSCRIPTION");
        assertEquals(new BigDecimal("-25.00"), resultTx.getAmount(), "L'addebito deve corrispondere a -25.00€");

        verify(connectionMock, times(1)).setAutoCommit(false);
        verify(userDaoMock, times(1)).update(mockDriver);
        verify(transactionDaoMock, times(1)).save(resultTx);
        verify(connectionMock, times(1)).commit();
    }

    @Test
    @DisplayName("Cambio piano fallito per fondi insufficienti")
    void testChangePlan_InsufficientFunds() throws SQLException {
        Driver mockDriver = newTestDriver("Povero", "povero@mail.com");
        // Il portafoglio parte da 0 (default del costruttore di registrazione).

        it.unifi.ing.chargenet.domain.users.SubscriptionPlan plan =
                it.unifi.ing.chargenet.domain.users.SubscriptionPlan.PREMIUM;

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                walletService.changePlan(mockDriver, plan)
        );

        assertTrue(exception.getMessage().contains("Fondi insufficienti"), "Deve segnalare la mancanza di fondi");

        verify(userDaoMock, never()).update(any());
        verify(transactionDaoMock, never()).save(any());
        verify(connectionMock, never()).commit();
        verify(connectionMock, times(1)).rollback();
        verify(connectionMock, times(1)).close();
    }
}