package it.unifi.ing.chargenet.dao.interfaces;

import java.util.List;

public interface GenericDao<T> {
    void save(T entity);
    T findByI (Long id);
    List<T> findAll();
    void update(T entity);
    void delete(Long id);
}