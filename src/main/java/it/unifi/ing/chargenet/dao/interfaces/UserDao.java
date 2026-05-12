package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.domain.users.Role; // Assicurati di avere questo Enum
import java.util.List;

public interface UserDao extends GenericDao<User> {
    User findByEmail(String email);
    List<User> findByRole(Role role);
}