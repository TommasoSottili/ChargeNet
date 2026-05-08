package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.users.User;

public interface UserDao extends GenericDao<User> {

    // Fondamentale per l'AuthService: permette il Login tramite email
    User findByEmail(String email);
}