package com.projet.sigrh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.Affectation;

public interface AffectationRepository extends JpaRepository<Affectation, Integer> {
    Optional<Affectation> findByContratId(Integer contratId);

    boolean existsByEmployeIdAndDateFinIsNull(String employeId);
}
