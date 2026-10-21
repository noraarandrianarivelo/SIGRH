package com.projet.sigrh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import com.projet.sigrh.model.SeanceEvaluation;

public interface SeanceEvaluationRepository extends JpaRepository<SeanceEvaluation, Integer> {
    List<SeanceEvaluation> findByCandidatureId(Integer candidatureId);
}
