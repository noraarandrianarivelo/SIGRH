package com.projet.sigrh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.PropositionEmbauche;

public interface PropositionEmbaucheRepository extends JpaRepository<PropositionEmbauche, Integer> {
    Optional<PropositionEmbauche> findFirstByCandidatureIdAndStatutInOrderByDateEnvoiDesc(
            Integer candidatureId, java.util.Collection<String> statuts);
}
