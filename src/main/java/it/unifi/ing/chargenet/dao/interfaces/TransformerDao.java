package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import java.util.List;

public interface TransformerDao extends GenericDao<PowerTransformer> {
    List<PowerTransformer> findOverheated();
}