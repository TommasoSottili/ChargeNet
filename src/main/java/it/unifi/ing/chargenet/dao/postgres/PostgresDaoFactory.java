package it.unifi.ing.chargenet.dao.postgres;

// Importiamo tutte le interfacce
import it.unifi.ing.chargenet.dao.interfaces.*;

import java.sql.Connection;

/**
 * Implementazione concreta della DaoFactory per PostgreSQL.
 * Questa è l'UNICA classe del sistema autorizzata a conoscere e istanziare
 * le classi concrete "Postgres...Dao".
 */
public class PostgresDaoFactory implements DaoFactory {

    @Override
    public SessionDao createSessionDao(Connection connection) {
        return new PostgresSessionDao(connection);
    }

    @Override
    public StationDao createStationDao(Connection connection) {
        return new PostgresStationDao(connection);
    }

    @Override
    public UserDao createUserDao(Connection connection) {
        return new PostgresUserDao(connection);
    }

    @Override
    public TransactionDao createTransactionDao(Connection connection) {
        return new PostgresTransactionDao(connection);
    }

    @Override
    public RatingDao createRatingDao(Connection connection) {
        return new PostgresRatingDao(connection);
    }

    @Override
    public RatingAlertDao createRatingAlertDao(Connection connection) {
        return new PostgresRatingAlertDao(connection);
    }

    @Override
    public TransformerDao createTransformerDao(Connection connection) {
        return new PostgresTransformerDao(connection);
    }
}