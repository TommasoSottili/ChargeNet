package it.unifi.ing.chargenet.dao.interfaces;

import java.util.List;

public interface GenericDao<T> {
    void save(T entity);
    T findById(int id);
    List<T> findAll();
    void update(T entity);
    void delete(int id);
}