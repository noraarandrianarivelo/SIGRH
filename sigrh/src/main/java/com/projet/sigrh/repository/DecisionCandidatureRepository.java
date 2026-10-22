package com.projet.sigrh.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.DecisionCandidature;

public interface DecisionCandidatureRepository extends JpaRepository<DecisionCandidature, Integer> {
    java.util.Optional<DecisionCandidature> findByLabel(String label);
}
