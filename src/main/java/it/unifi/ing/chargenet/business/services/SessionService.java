package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.business.strategies.ChargingStrategy;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.financials.TransactionType;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.dao.interfaces.SessionDao;
import it.unifi.ing.chargenet.dao.interfaces.TransactionDao;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;

import java.math.BigDecimal;
import java.util.List;

public class SessionService {

    // Dipendenze DAO (Dependency Injection tramite Costruttore)
    private final SessionDao sessionDao;
    private final TransactionDao transactionDao;
    private final StationDao stationDao;
    private final UserDao userDao;

    public SessionService(SessionDao sessionDao, TransactionDao transactionDao, StationDao stationDao, UserDao userDao) {
        this.sessionDao = sessionDao;
        this.transactionDao = transactionDao;
        this.stationDao = stationDao;
        this.userDao = userDao;
    }

    /**
     * Apre una nuova sessione verificando i fondi del guidatore.
     */
    public ChargingSession openSession(Driver driver, ChargingStation station, ChargingStrategy strategy, double batteryStart) {
        // 1. Verifica saldo per almeno 5 kWh (Requisito di Business)
        double minRequiredCost = strategy.calculateCost(5.0, station, driver);
        BigDecimal minCostBD = BigDecimal.valueOf(minRequiredCost);
        if (driver.getWalletBalance().compareTo(minCostBD) < 0) {
            throw new IllegalStateException("Saldo insufficiente: sono necessari fondi per almeno 5 kWh.");
        }

        // 2. Delega la creazione al Factory Method
        ChargingSession session = ChargingSession.open(driver, station, strategy.getName(), batteryStart);

        // 3. Imposta la colonnina a BUSY
        station.setBusy();

        // 4. Persistenza
        sessionDao.save(session);
        stationDao.update(station);

        return session;
    }

    /**
     * Chiamato dal GridMonitor ogni 5 secondi per far avanzare la ricarica ed erodere il portafoglio.
     */
    public void addTick(ChargingSession session, ChargingStrategy strategy) {

        // 1. Calcolo fisico: Quanta energia è stata erogata in 5 secondi?
        double oreTick = 5.0 / 3600.0; // 5 secondi convertiti in ore
        // Presupponiamo che station abbia un metodo per conoscere la potenza in kW
        double kwhThisTick = session.getStation().getPowerKw() * oreTick;

        // 2. Calcolo economico: Quanto costa questa energia?
        double costDouble = strategy.calculateCost(kwhThisTick, session.getStation(), session.getDriver());
        BigDecimal costThisTick = BigDecimal.valueOf(costDouble);

        // 3. Passiamo i dati alla sessione usando il metodo del tuo collega
        session.addTick(kwhThisTick, costThisTick);

        // 4. Scaliamo i soldi dal portafoglio del Driver
        Driver driver = session.getDriver();
        driver.charge(costThisTick);

        // 5. Salviamo i progressi nel Database
        userDao.update(driver);
        sessionDao.update(session);

        // --- CONTROLLI DI BUSINESS FINALI ---

        // A. Controllo anti-debito: se i soldi finiscono, stacca la corrente e rimborsa il rimanente
        if (driver.getWalletBalance().compareTo(BigDecimal.ZERO) <= 0) {
            forceClose(session);
            return; // Usciamo dal metodo, la sessione è forzatamente chiusa
        }

        // B. Controllo auto-completamento (Logica del collega)
        // Se il metodo del collega ha visto che la batteria è al 100%, avrà cambiato lo stato
        // della sessione (presumibilmente togliendolo da ACTIVE). In tal caso dobbiamo
        // liberare la colonnina e generare la transazione.
        if (session.getStatus() != SessionStatus.ACTIVE) {
            closeSession(session);
        }
    }

    /**
     * Chiusura volontaria da parte dell'utente (Happy Path - UC7).
     */
    public Transaction closeSession(ChargingSession session) {
        // 1. Finalizza lo stato della sessione (es. set endTime)
        session.complete();

        // 2. Libera la colonnina
        ChargingStation station = session.getStation();
        station.setActive();

        // 3. Genera la ricevuta/transazione (CHARGE)
        BigDecimal totalCost = session.getCostTotal();
        Double totalKwhDelivered = session.getKwhDelivered();
        String description = "Addebito pari a: " + totalCost + " per ricarica completata";
        Transaction transaction = Transaction.create(session.getDriver(), TransactionType.CHARGE, totalCost, totalKwhDelivered, description);

        // 4. Aggiorna i guadagni dell'operatore (es. versando i soldi nel suo conto)
        // (Nota: assumo che la logica di accredito all'operatore venga gestita qui o tramite un altro metodo/service)

        // 5. Salva tutto nel DB
        transactionDao.save(transaction);
        stationDao.update(station);
        sessionDao.update(session);

        return transaction;
    }

    /**
     * Chiusura forzata dal sistema (es. emergenza termica, spegnimento per portafoglio vuoto).
     */
    public Transaction forceClose(ChargingSession session) {
        // 1. Interruzione formale
        session.interrupt();

        ChargingStation station = session.getStation();
        if (station.getStatus() != StationStatus.OVERLOADED) {
            station.setActive();;
        }

        // 2. CALCOLO DEL RIMBORSO (Tua logica: Rimborso dell'ultimo tick)
        // Recuperiamo quanto è stato erogato in 5 secondi (stessa logica di addTick)
        double oreTick = 5.0 / 3600.0;
        double kwhLastTick = station.getPowerKw() * oreTick;

        // Recuperiamo la strategia usata (tramite la nostra factory/metodo)
        ChargingStrategy strategy = ChargingStrategy.fromString(session.getStrategyUsed());

        // Calcoliamo il costo di quel singolo tick
        double costLastTickDouble = strategy.calculateCost(kwhLastTick, station, session.getDriver());
        BigDecimal refundAmount = BigDecimal.valueOf(costLastTickDouble);
        String refundDescription = "Rimborso pari a: " + refundAmount.toPlainString() + " a causa di interruzione tecnica";


        // 3. LOGICA DI APPLICAZIONE
        // Se il wallet è vuoto, il blocco è dovuto al cliente -> Rimborso 0
        // Se il wallet ha ancora soldi, il blocco è per Thermal Alert -> Applichiamo il rimborso
        if (session.getDriver().getWalletBalance().compareTo(BigDecimal.ZERO) <= 0) {
            refundAmount = BigDecimal.ZERO;
        }

        // 4. Generazione Transazione e Accredito
        Transaction transaction = Transaction.create(session.getDriver(), TransactionType.REFUND, refundAmount, kwhLastTick, refundDescription);

        Driver driver = session.getDriver();
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            driver.refund(refundAmount);
        }

        // 5. Persistenza atomica
        transactionDao.save(transaction);
        userDao.update(driver);
        stationDao.update(station);
        sessionDao.update(session);

        return transaction;
    }

    /**
     * Recupera le sessioni attualmente in corso per il GridMonitor.
     */
    public List<ChargingSession> getActiveSessions() {
        return sessionDao.findActiveSessions();
    }
}