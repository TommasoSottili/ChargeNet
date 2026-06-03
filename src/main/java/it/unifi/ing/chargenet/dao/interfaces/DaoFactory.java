package it.unifi.ing.chargenet.dao.interfaces;

import java.sql.Connection;

/**
 * Interfaccia Abstract Factory per la creazione dei Data Access Object (DAO).
 * Garantisce che i Service dipendano solo dalle interfacce e mai dalle
 * implementazioni concrete (es. Postgres).
 */
public interface DaoFactory {
    SessionDao createSessionDao(Connection connection);
    StationDao createStationDao(Connection connection);
    UserDao createUserDao(Connection connection);
    TransactionDao createTransactionDao(Connection connection);
    RatingDao createRatingDao(Connection connection);
    RatingAlertDao createRatingAlertDao(Connection connection);
    TransformerDao createTransformerDao(Connection connection);
}