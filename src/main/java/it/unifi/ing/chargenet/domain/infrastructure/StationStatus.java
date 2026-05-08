package it.unifi.ing.chargenet.domain.infrastructure;

public enum StationStatus {
    PENDING_VERIFICATION, // in attesa di approvazione dall' EnergyManager
    ACTIVE, // libera e funzionanate
    RESERVED, // prenotata (Short-Therm-Hold)
    BUSY, // in ricarica
    OVERLOADED, // sospesa dal LoadBalancer (Thermal_Alert)
    SUSPENDED, // sospesa dall' EnergyManager
    REJECTED, // rifiutata in fase di registrazione
}
