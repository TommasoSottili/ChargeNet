package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import java.util.List;

public interface StationDao extends GenericDao<ChargingStation> {

    // Serve per la mappa dell'utente: mostra solo le stazioni non guaste/sospese
    List<ChargingStation> findActive();

    // Filtro per tipo di connettore (es. mostrami solo colonnine compatibili CHADEMO)
    List<ChargingStation> findByConnector(ConnectorType type);

    // Serve all'Admin/Manager per trovare le colonnine in attesa di approvazione
    List<ChargingStation> findByStatus(StationStatus status);
}