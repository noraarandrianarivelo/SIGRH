package com.projet.sigrh.repository;

import com.projet.sigrh.model.DemandeRecrutement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeRecrutementRepository extends JpaRepository<DemandeRecrutement, Long> {
    List<DemandeRecrutement> findByStatut(String statut);
    Optional<DemandeRecrutement> findByBesoinId(Long besoinId);
}