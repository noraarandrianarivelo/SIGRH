package com.projet.sigrh.repository;

import com.projet.sigrh.model.HistoriqueValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoriqueValidationRepository extends JpaRepository<HistoriqueValidation, Long> {

    List<HistoriqueValidation> findByIdDemandeOrderByDateValidationAsc(Long idDemande);
}