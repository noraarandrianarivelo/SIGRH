package com.projet.sigrh.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.Employe;
import com.projet.sigrh.model.SeanceEvaluation;
import com.projet.sigrh.model.TestRecrutement;
import com.projet.sigrh.repository.EmployeRepository;
import com.projet.sigrh.repository.SeanceEvaluationRepository;
import com.projet.sigrh.repository.TestRecrutementRepository;

@Service
public class SeanceEvaluationService {
    private static final Set<String> STATUTS = Set.of("PLANIFIEE", "TERMINEE", "ANNULEE", "ABSENT");

    private final SeanceEvaluationRepository repository;
    private final EmployeRepository employeRepository;
    private final TestRecrutementRepository testRepository;

    public SeanceEvaluationService(SeanceEvaluationRepository repository,
            EmployeRepository employeRepository, TestRecrutementRepository testRepository) {
        this.repository = repository;
        this.employeRepository = employeRepository;
        this.testRepository = testRepository;
    }

    public List<Employe> listerEvaluateurs() {
        return employeRepository.findAll();
    }

    public List<TestRecrutement> listerTests() {
        return testRepository.findAll();
    }

    public List<SeanceEvaluation> findAll() {
        return repository.findAll();
    }

    public Optional<SeanceEvaluation> findById(Integer id) {
        return repository.findById(id);
    }

    public SeanceEvaluation creer(Candidature candidature, String evaluateurId, Integer testId,
            LocalDateTime dateEntretien, LocalDateTime dateTest,
            String lieuEntretien, String lieuTest, String statut) {
        if (dateEntretien == null && dateTest == null) {
            throw new BusinessException("Une date d'entretien ou de test est obligatoire");
        }
        if ((testId == null) != (dateTest == null)) {
            throw new BusinessException("Le test et sa date doivent être renseignés ensemble");
        }
        if (!STATUTS.contains(statut)) {
            throw new BusinessException("Statut de séance invalide");
        }

        Employe evaluateur = employeRepository.findById(evaluateurId)
                .orElseThrow(() -> new BusinessException("Évaluateur introuvable"));
        TestRecrutement test = testId == null ? null : testRepository.findById(testId)
                .orElseThrow(() -> new BusinessException("Test de recrutement introuvable"));

        SeanceEvaluation seance = new SeanceEvaluation();
        seance.candidature = candidature;
        seance.evaluateur = evaluateur;
        seance.test = test;
        seance.dateEntretien = dateEntretien;
        seance.dateTest = dateTest;
        seance.lieuEntretien = lieuEntretien;
        seance.lieuTest = lieuTest;
        seance.statut = statut;
        return repository.save(seance);
    }
}
