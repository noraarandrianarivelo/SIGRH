package com.projet.sigrh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.Contrat;

public interface ContratRepository extends JpaRepository<Contrat, Integer> {
    Optional<Contrat> findByPropositionId(Integer propositionId);
}
