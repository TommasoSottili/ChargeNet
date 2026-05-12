package it.unifi.ing.chargenet.dao.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    // URL magico per H2: crea DB in memoria, lo tiene vivo, e lancia lo script SQL all'avvio
    private static final String URL = "jdbc:h2:mem:chargenet;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:schema.sql'";
    private static final String USER = "sa"; // Utente default di H2
    private static final String PASSWORD = ""; // Nessuna password per H2

    // Costruttore privato: è una classe di utility, non si istanzia
    private DatabaseManager() {}

    /**
     * Restituisce un'istanza di java.sql.Connection pronta all'uso.
     */
    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("Errore critico di connessione al database H2", e);
        }
    }
}