package it.unifi.ing.chargenet.dao.postgres;

import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresTransformerDaoTest {

    private static Connection connection;
    private PostgresTransformerDao transformerDao;

    // 1. SETUP DEL DATABASE (Eseguito una sola volta)
    @BeforeAll
    static void startDatabase() throws SQLException {
        // Usa il percorso esatto che hai verificato funzionare prima!
        String url = "jdbc:h2:mem:chargenet_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=RUNSCRIPT FROM './src/main/resources/scheme.sql'";
        connection = DriverManager.getConnection(url, "sa", "");
    }

    // 2. CHIUSURA
    @AfterAll
    static void stopDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    // 3. PULIZIA GARANTITA (Eseguito PRIMA di OGNI SINGOLO test)
    @BeforeEach
    void setUp() throws SQLException {
        transformerDao = new PostgresTransformerDao(connection);

        // LA SOLUZIONE DEFINITIVA E BLINDATA PER H2:
        try (Statement stmt = connection.createStatement()) {
            // 1. Disattiva il controllo delle chiavi esterne
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            // 2. Svuota la tabella resettando gli ID in totale sicurezza
            stmt.execute("TRUNCATE TABLE power_transformers RESTART IDENTITY");
            // 3. Riattiva le chiavi esterne per non compromettere il database
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    // --- I NOSTRI TEST ---

    @Test
    void testSaveAndFindById() {
        // ARRANGEMENT
        PowerTransformer t1 = new PowerTransformer("Trasformatore Nord");

        // ACT
        transformerDao.save(t1);

        // ASSERT
        assertNotNull(t1.getId(), "L'ID non dovrebbe essere nullo dopo il salvataggio");

        PowerTransformer retrieved = transformerDao.findById(t1.getId());
        assertNotNull(retrieved, "Il trasformatore recuperato non deve essere nullo");
        assertEquals("Trasformatore Nord", retrieved.getName());
    }

    @Test
    void testFindOverheated() {
        // ARRANGEMENT
        PowerTransformer safe = new PowerTransformer("Safe");
        PowerTransformer danger = new PowerTransformer("Danger");

        transformerDao.save(safe);
        transformerDao.save(danger);

        // Simulo l'innalzamento della temperatura tramite SQL
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("UPDATE power_transformers SET temperature = 50.0 WHERE name = 'Safe'");
            stmt.execute("UPDATE power_transformers SET temperature = 95.0 WHERE name = 'Danger'");
        } catch (SQLException e) {
            fail("Errore durante la preparazione dei dati di test: " + e.getMessage());
        }

        // ACT
        List<PowerTransformer> overheated = transformerDao.findOverheated();

        // ASSERT
        assertEquals(1, overheated.size(), "Dovrebbe trovare esattamente 1 trasformatore surriscaldato");
        assertEquals("Danger", overheated.get(0).getName(), "Il trasformatore surriscaldato deve essere 'Danger'");
    }
}