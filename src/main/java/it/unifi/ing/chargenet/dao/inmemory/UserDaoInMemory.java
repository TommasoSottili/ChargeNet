package it.unifi.ing.chargenet.dao.inmemory;

import it.unifi.ing.chargenet.dao.interfaces.UserDao;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.domain.users.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementazione in RAM del UserDao.
 * Utilizza una HashMap per simulare una tabella 'users' di un database relazionale
 * al fine di poter testare la logica di business in attesa del database reale.
 */
public class UserDaoInMemory implements UserDao {

    // La nostra "tabella" del database: la chiave è l'ID, il valore è l'entità User
    private final Map<Long, User> database = new HashMap<>();

    // Contatore interno per simulare la generazione automatica degli ID (AUTO_INCREMENT)
    private Long autoIncrementId = 1L;

    @Override
    public void save(User entity) {
        // Simuliamo l'inserimento nel DB: assegniamo l'ID progressivo e salviamo
        entity.setId(autoIncrementId);
        database.put(autoIncrementId, entity);

        System.out.println("[DB IN-MEMORY] Inserito nuovo User: " + entity.getEmail() + " con ID: " + autoIncrementId);

        // Incrementiamo il contatore per il prossimo inserimento
        autoIncrementId++;
    }

    @Override
    public User findById(Long id) {
        // Restituisce l'utente se la chiave esiste, altrimenti null
        return database.get(id);
    }

    @Override
    public List<User> findAll() {
        // Restituisce una nuova lista contenente tutti i valori della mappa
        return new ArrayList<>(database.values());
    }

    @Override
    public void update(User entity) {
        // Un UPDATE ha senso solo se l'entità ha un ID e se quell'ID esiste già nel DB
        if (entity.getId() != null && database.containsKey(entity.getId())) {
            database.put(entity.getId(), entity);
            System.out.println("[DB IN-MEMORY] Aggiornato User con ID: " + entity.getId());
        } else {
            throw new IllegalArgumentException("Errore di UPDATE: l'utente non esiste nel database.");
        }
    }

    @Override
    public void delete(Long id) {
        // Rimuove la riga dal database tramite la sua chiave primaria
        database.remove(id);
        System.out.println("[DB IN-MEMORY] Eliminato User con ID: " + id);
    }

    // ==========================================
    // METODI SPECIFICI EREDITATI DA UserDao
    // ==========================================

    @Override
    public User findByEmail(String email) {
        // Simula la query: SELECT * FROM users WHERE email = ? LIMIT 1
        for (User user : database.values()) {
            if (user.getEmail().equalsIgnoreCase(email)) {
                return user; // Utente trovato
            }
        }
        return null; // Nessun utente trovato con questa email
    }

    @Override
    public List<User> findByRole(Role role) {
        // Simula la query: SELECT * FROM users WHERE role = ?
        return database.values().stream()
                .filter(user -> user.getRole() == role)
                .collect(Collectors.toList());
    }
}