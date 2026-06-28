package it.unifi.ing.chargenet.business.services;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.dao.interfaces.UserDao;

import java.sql.Connection;
import java.sql.SQLException;

public class DriverService {

    private final DaoFactory daoFactory;

    public DriverService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public void updateDriverLocation(Driver driver, double newLat, double newLng) {
        Connection connection = null;
        try {
            connection = DatabaseManager.getConnection();
            connection.setAutoCommit(false);

            // Usiamo il DAO che hai già: UserDao
            UserDao userDao = daoFactory.createUserDao(connection);

            // 1. Logica di Dominio
            driver.updatePosition(newLat, newLng);

            // 2. Persistenza: PostgresUserDao.update(User) gestisce già l'instanceof Driver
            userDao.update(driver);

            connection.commit();
        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException("Errore aggiornamento posizione: " + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try { connection.rollback(); } catch (SQLException ex) { }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                connection.close();
            } catch (SQLException ex) { }
        }
    }
}