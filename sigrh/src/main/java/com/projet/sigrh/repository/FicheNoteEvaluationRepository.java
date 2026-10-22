package com.projet.sigrh.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.FicheNoteEvaluation;
import java.util.Optional;

public interface FicheNoteEvaluationRepository extends JpaRepository<FicheNoteEvaluation, Integer> {
    boolean existsBySeanceId(Integer seanceId);

    Optional<FicheNoteEvaluation> findBySeanceId(Integer seanceId);
}
