package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import java.util.List;

public interface SessionDao extends GenericDao<ChargingSession> {
    List<ChargingSession> findByDriver(Long driverId);
    List<ChargingSession> findActiveSessions();
    List<ChargingSession> findActiveByStation(Long stationId);
}