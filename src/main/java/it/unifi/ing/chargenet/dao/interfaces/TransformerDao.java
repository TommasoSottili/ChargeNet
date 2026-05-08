package it.unifi.ing.chargenet.dao.interfaces;

import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;

public interface TransformerDao extends GenericDao<PowerTransformer> {
    // Le 5 operazioni base del GenericDao sono sufficienti per avviare il GridCluster
    // e caricare tutti i trasformatori della rete all'avvio.
}