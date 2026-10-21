package com.projet.sigrh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.Candidature;

public interface CandidatureRepository extends JpaRepository<Candidature, Integer> {
    List<Candidature> findByOffreId(Integer offreId);

    boolean existsByCandidatIdAndOffreId(Integer candidatId, Integer offreId);
}
