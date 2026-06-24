package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import java.util.List;

public interface StationDao extends GenericDao<ChargingStation> {
    List<ChargingStation> findByStatus(StationStatus status);
    List<ChargingStation> findByOperator(Long operatorId);
    List<ChargingStation> findByConnectorType(ConnectorType type);
    List<ChargingStation> findNearestAvailable(double lat, double lng, ConnectorType type, Long excludeId);
    List<ChargingStation> findByTransformer(Long transformerId);
    void expireHolds();
    List<ChargingStation> findActive();
    List<ChargingStation> findAll();
}