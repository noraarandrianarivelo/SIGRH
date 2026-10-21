package com.projet.sigrh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.projet.sigrh.model.HistoriqueValidation;

@Repository
public interface HistoriqueValidationRepository extends JpaRepository<HistoriqueValidation, Long> {

    List<HistoriqueValidation> findByIdDemandeOrderByDateValidationAsc(Long idDemande);
}