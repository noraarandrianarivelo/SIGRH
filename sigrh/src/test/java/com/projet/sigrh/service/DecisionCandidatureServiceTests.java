package com.projet.sigrh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.projet.sigrh.dto.DecisionCandidatureRequest;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.BesoinPoste;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.DecisionCandidature;
import com.projet.sigrh.model.Employe;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.model.RoleUser;
import com.projet.sigrh.repository.CandidatureRepository;
import com.projet.sigrh.repository.DecisionCandidatureRepository;
import com.projet.sigrh.repository.FicheNoteEvaluationRepository;
import com.projet.sigrh.repository.HistoriqueDecisionCandidatureRepository;
import com.projet.sigrh.repository.SeanceEvaluationRepository;

class DecisionCandidatureServiceTests {

    @Test
    void autoriseAutantDeCandidatsQueLeffectifDemande() {
        CandidatureRepository candidatureRepository = mock(CandidatureRepository.class);
        Candidature candidature = candidature(12, 5);
        when(candidatureRepository.findById(12)).thenReturn(Optional.of(candidature));
        when(candidatureRepository.countByOffreIdAndStatut(7, "RETENUE")).thenReturn(4L);
        DecisionCandidatureRepository decisionRepository = decisionRepository();
        when(decisionRepository.findByLabel("RETENUE")).thenReturn(Optional.of(decision("RETENUE")));

        assertDoesNotThrow(() -> service(candidatureRepository, decisionRepository).decider(12,
                new DecisionCandidatureRequest("RETENUE", "Profil retenu"), direction()));
    }

    @Test
    void refuseUneCandidatureQuandLeffectifEstAtteint() {
        CandidatureRepository candidatureRepository = mock(CandidatureRepository.class);
        Candidature candidature = candidature(12, 5);
        when(candidatureRepository.findById(12)).thenReturn(Optional.of(candidature));
        when(candidatureRepository.countByOffreIdAndStatut(7, "RETENUE")).thenReturn(5L);

        assertThrows(BusinessException.class, () -> service(candidatureRepository, decisionRepository()).decider(12,
                new DecisionCandidatureRequest("RETENUE", "Profil retenu"), direction()));
    }

    @Test
    void autoriseLaMiseEnReserveAvantLaDecisionFinale() {
        CandidatureRepository candidatureRepository = mock(CandidatureRepository.class);
        Candidature candidature = candidature(12, 5);
        when(candidatureRepository.findById(12)).thenReturn(Optional.of(candidature));

        assertDoesNotThrow(() -> service(candidatureRepository, decisionRepository()).decider(12,
                new DecisionCandidatureRequest("RESERVE", "Décision finale à compléter"), direction()));
    }

    private DecisionCandidatureService service(CandidatureRepository candidatureRepository,
            DecisionCandidatureRepository decisionRepository) {
        return new DecisionCandidatureService(candidatureRepository, decisionRepository,
                mock(HistoriqueDecisionCandidatureRepository.class), mock(SeanceEvaluationRepository.class),
                mock(FicheNoteEvaluationRepository.class));
    }

    private DecisionCandidatureRepository decisionRepository() {
        DecisionCandidatureRepository repository = mock(DecisionCandidatureRepository.class);
        when(repository.save(any(DecisionCandidature.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    private Candidature candidature(Integer id, int effectif) {
        Candidature candidature = new Candidature();
        candidature.id = id;
        OffreEmploi offre = new OffreEmploi();
        offre.id = 7;
        BesoinPoste besoinPoste = new BesoinPoste();
        besoinPoste.setEffectif(effectif);
        offre.besoinPoste = besoinPoste;
        candidature.offre = offre;
        return candidature;
    }

    private Utilisateur direction() {
        Utilisateur utilisateur = new Utilisateur();
        RoleUser role = new RoleUser();
        role.setRoleName("ROLE_DIRECTION");
        utilisateur.setRoleUser(role);
        utilisateur.setEmploye(new Employe());
        return utilisateur;
    }

    private DecisionCandidature decision(String label) {
        DecisionCandidature decision = new DecisionCandidature();
        decision.label = label;
        return decision;
    }
}
