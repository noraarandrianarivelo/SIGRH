package com.projet.sigrh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.OffreEmploi;

public interface OffreEmploiRepository extends JpaRepository<OffreEmploi, Integer> {
    List<OffreEmploi> findByStatut(String statut);
    boolean existsByBesoinPosteId(Long besoinPosteId);
}
