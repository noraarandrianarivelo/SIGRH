package com.projet.sigrh.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.PropositionEmbauche;

public interface PropositionEmbaucheRepository extends JpaRepository<PropositionEmbauche, Integer> {
    List<PropositionEmbauche> findByCandidatureCandidatIdOrderByDateEnvoiDesc(Integer candidatId);

    Optional<PropositionEmbauche> findFirstByCandidatureIdAndStatutInOrderByDateEnvoiDesc(
            Integer candidatureId, java.util.Collection<String> statuts);
}
