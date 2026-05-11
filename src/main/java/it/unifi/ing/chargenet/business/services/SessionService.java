package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.business.strategies.ChargingStrategy;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.financials.Transaction;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import it.unifi.ing.chargenet.dao.interfaces.SessionDao;

import java.util.List;

public class SessionService {

    private SessionDao sessionDao;

    public SessionService(SessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    public ChargingSession openSession(Driver driver, ChargingStation station, ChargingType type) {
        return null;
    }

    public void addTick(ChargingSession session, ChargingStrategy strategy) {
        // Metodo void, aggiorna lo stato della sessione e il DB
    }

    public Transaction closeSession(ChargingSession session) {
        return null;
    }

    public Transaction forceClose(ChargingSession session) {
        return null;
    }

    public List<ChargingSession> getActiveSessions() {
        // Delega la lettura al DAO
        return sessionDao.findActiveSessions();
    }
}