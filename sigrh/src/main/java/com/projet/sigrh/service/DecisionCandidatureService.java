package com.projet.sigrh.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet.sigrh.dto.DecisionCandidatureRequest;
import com.projet.sigrh.dto.EvaluationCandidatureView;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.DecisionCandidature;
import com.projet.sigrh.model.HistoriqueDecisionCandidature;
import com.projet.sigrh.model.SeanceEvaluation;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.repository.CandidatureRepository;
import com.projet.sigrh.repository.DecisionCandidatureRepository;
import com.projet.sigrh.repository.FicheNoteEvaluationRepository;
import com.projet.sigrh.repository.HistoriqueDecisionCandidatureRepository;
import com.projet.sigrh.repository.SeanceEvaluationRepository;
import com.projet.sigrh.security.Roles;

@Service
public class DecisionCandidatureService {
    private static final String RETENUE = "RETENUE";
    private static final String REJETEE = "REJETEE";
    private static final String RESERVE = "RESERVE";

    private final CandidatureRepository candidatureRepository;
    private final DecisionCandidatureRepository decisionRepository;
    private final HistoriqueDecisionCandidatureRepository historiqueRepository;
    private final SeanceEvaluationRepository seanceRepository;
    private final FicheNoteEvaluationRepository ficheRepository;

    public DecisionCandidatureService(CandidatureRepository candidatureRepository,
            DecisionCandidatureRepository decisionRepository,
            HistoriqueDecisionCandidatureRepository historiqueRepository,
            SeanceEvaluationRepository seanceRepository,
            FicheNoteEvaluationRepository ficheRepository) {
        this.candidatureRepository = candidatureRepository;
        this.decisionRepository = decisionRepository;
        this.historiqueRepository = historiqueRepository;
        this.seanceRepository = seanceRepository;
        this.ficheRepository = ficheRepository;
    }

    @Transactional(readOnly = true)
    public List<EvaluationCandidatureView> comparer(Integer offreId) {
        return candidatureRepository.findByOffreId(offreId).stream()
                .map(candidature -> new EvaluationCandidatureView(candidature,
                        seanceRepository.findByCandidatureId(candidature.id).stream()
                                .map(this::note)
                                .toList()))
                .toList();
    }

    @Transactional
    public void decider(Integer candidatureId, DecisionCandidatureRequest request, Utilisateur utilisateur) {
        verifierDirection(utilisateur);
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new BusinessException("Candidature introuvable", HttpStatus.NOT_FOUND));

        String decisionLabel = request == null ? null : normaliser(request.decision());
        if (!RETENUE.equals(decisionLabel) && !REJETEE.equals(decisionLabel)
                && !RESERVE.equals(decisionLabel)) {
            throw new BusinessException("La décision doit être RESERVE, RETENUE ou REJETEE.");
        }
        if (request == null || request.motif() == null || request.motif().isBlank()) {
            throw new BusinessException("Le motif de la décision est obligatoire.");
        }
        if (RETENUE.equals(decisionLabel) && !RETENUE.equals(candidature.statut)) {
            verifierCapacite(candidature, RETENUE, "retenus");
        }
        if (RESERVE.equals(decisionLabel) && !RESERVE.equals(candidature.statut)) {
            verifierCapacite(candidature, RESERVE, "en réserve");
        }

        DecisionCandidature decision = decisionRepository.findByLabel(decisionLabel)
                .orElseGet(() -> {
                    DecisionCandidature nouvelle = new DecisionCandidature();
                    nouvelle.label = decisionLabel;
                    return decisionRepository.save(nouvelle);
                });

        HistoriqueDecisionCandidature historique = new HistoriqueDecisionCandidature();
        historique.candidature = candidature;
        historique.decideur = utilisateur.getEmploye();
        historique.decision = decision;
        historique.dateDecision = LocalDateTime.now();
        historique.motif = request.motif().trim();
        historiqueRepository.save(historique);

        candidature.statut = decisionLabel;
        candidatureRepository.save(candidature);
    }

    private void verifierCapacite(Candidature candidature, String statut, String libelle) {
        Integer effectif = candidature.offre.besoinPoste.getEffectif();
        if (effectif == null || effectif < 1) {
            throw new BusinessException("L'effectif demandé pour cette offre est invalide.");
        }
        long nombre = candidatureRepository.countByOffreIdAndStatut(candidature.offre.id, statut);
        if (nombre >= effectif) {
            throw new BusinessException("Le nombre de candidats " + libelle
                    + " atteint déjà l'effectif demandé (" + effectif + ").");
        }
    }

    private EvaluationCandidatureView.NoteView note(SeanceEvaluation seance) {
        var fiche = ficheRepository.findBySeanceId(seance.id).orElse(null);
        return new EvaluationCandidatureView.NoteView(seance.dateEntretien, seance.dateTest,
                fiche == null ? null : fiche.noteEntretien,
                fiche == null ? null : fiche.noteTest,
                fiche == null ? null : fiche.avis,
                fiche == null ? null : fiche.commentaire);
    }

    private void verifierDirection(Utilisateur utilisateur) {
        if (utilisateur == null || !Roles.DIRECTION.equals(utilisateur.getRole())) {
            throw new BusinessException("Seule la Direction peut décider du candidat retenu.", HttpStatus.FORBIDDEN);
        }
        if (utilisateur.getEmploye() == null) {
            throw new BusinessException("Le compte de Direction n'est lié à aucun employé.", HttpStatus.FORBIDDEN);
        }
    }

    private String normaliser(String decision) {
        return decision == null ? null : decision.trim().toUpperCase();
    }
}
