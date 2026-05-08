package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.users.Driver;
import java.util.List;

public interface SessionDao extends GenericDao<ChargingSession> {

    // Serve al tuo GridMonitor per aggiornare solo le ricariche in corso
    List<ChargingSession> findActiveSessions();

    // Serve per sapere quali auto stanno caricando a una specifica colonnina
    List<ChargingSession> findActiveByStation(int stationId);

    // Serve al tuo collega (Platform) per mostrare lo storico ricariche all'utente
    List<ChargingSession> findByDriver(Driver driver);
}