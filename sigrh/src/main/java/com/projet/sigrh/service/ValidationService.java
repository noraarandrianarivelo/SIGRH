package com.projet.sigrh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet.sigrh.dto.DecisionRequest;
import com.projet.sigrh.dto.DemandeResponse;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Besoin;
import com.projet.sigrh.model.BesoinPoste;
import com.projet.sigrh.model.DemandeRecrutement;
import com.projet.sigrh.model.HistoriqueValidation;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.model.Poste;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.model.enums.DecisionValidation;
import com.projet.sigrh.model.enums.EtapeValidation;
import com.projet.sigrh.model.enums.StatutDemande;
import com.projet.sigrh.repository.DemandeRecrutementRepository;
import com.projet.sigrh.repository.HistoriqueValidationRepository;
import com.projet.sigrh.repository.OffreEmploiRepository;
import com.projet.sigrh.repository.PosteRepository;

/** Bloc 1 : décisions RH, Finance et Direction, avec une ligne d'historique par décision. */
@Service
public class ValidationService {

    @Autowired
    private DemandeRecrutementService demandeService;
    @Autowired
    private DemandeRecrutementRepository demandeRepository;
    @Autowired
    private HistoriqueValidationRepository historiqueRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private OffreEmploiRepository offreEmploiRepository;

    @Transactional
    public DemandeResponse decider(Long idDemande, DecisionRequest req, Utilisateur u) {
        DemandeRecrutement d = demandeService.trouver(idDemande);

        EtapeValidation etape = WorkflowDemande.etapeCourante(d.getStatut());
        if (etape == null) {
            throw new BusinessException("Cette demande n'est pas en attente de validation (statut " + d.getStatut() + ").");
        }
        if (!WorkflowDemande.peutDecider(d, u)) {
            throw new BusinessException("Votre rôle ne permet pas de traiter l'étape " + etape + ".", HttpStatus.FORBIDDEN);
        }
        if (u.getEmploye() == null) {
            throw new BusinessException("Ce compte n'est lié à aucun employé.", HttpStatus.FORBIDDEN);
        }

        DecisionValidation decision = req.decision();
        if (decision == null || !WorkflowDemande.decisionsPossibles(etape).contains(decision)) {
            throw new BusinessException("Décision non autorisée à l'étape " + etape + ".");
        }
        // Motif obligatoire pour un rejet ou une demande de correction.
        if (decision != DecisionValidation.APPROUVEE
                && (req.commentaire() == null || req.commentaire().isBlank())) {
            throw new BusinessException("Un motif est obligatoire pour rejeter ou demander une correction.");
        }

        // RG RH : budget conforme à la grille, puis création des postes proposés.
        if (etape == EtapeValidation.RH && decision == DecisionValidation.APPROUVEE) {
            controlerGrille(d, u);
            creerPostesProposes(d);
        }

        HistoriqueValidation h = new HistoriqueValidation();
        h.setIdDemande(d.getId());
        h.setValidateur(u.getEmploye());
        h.setEtape(etape);
        h.setDecision(decision);
        h.setDateValidation(LocalDateTime.now());
        h.setCommentaire(req.commentaire() == null ? null : req.commentaire().trim());
        historiqueRepository.save(h);

        d.setStatut(WorkflowDemande.statutApresDecision(d, etape, decision));
        DemandeRecrutement demandeEnregistree = demandeRepository.save(d);
        if (d.getStatut() == StatutDemande.VALIDEE) {
            // Le circuit DIRECTION_DIRECTE saute l'étape RH : les postes proposés
            // doivent donc être créés ici avant de générer les offres.
            creerPostesProposes(d);
            creerOffres(d);
        }
        return demandeService.toResponse(demandeEnregistree, u);
    }

    private void creerOffres(DemandeRecrutement d) {
        LocalDate dateCreation = LocalDate.now();
        List<OffreEmploi> offres = new ArrayList<>();
        for (BesoinPoste ligne : d.getBesoin().getLignes()) {
            if (offreEmploiRepository.existsByBesoinPosteId(ligne.getId())) {
                continue;
            }
            OffreEmploi offre = new OffreEmploi();
            offre.besoinPoste = ligne;
            offre.titre = ligne.getPoste() == null
                    ? ligne.getLibellePostePropose()
                    : ligne.getPoste().getLibelle();
            offre.description = d.getBesoin().getMotif();
            offre.dateCreation = dateCreation;
            offre.datePublication = dateCreation;
            offre.dateLimite = d.getDateArriveeSouhaitee();
            offre.statut = "OUVERT";
            offres.add(offre);
        }
        offreEmploiRepository.saveAll(offres);
    }

    /** Le budget unitaire de chaque poste existant doit rester entre salaire_min et salaire_max. */
    private void controlerGrille(DemandeRecrutement d, Utilisateur u) {
        for (DemandeResponse.Ligne l : demandeService.toResponse(d, u).lignes()) {
            if (!l.conforme()) {
                throw new BusinessException("Budget non conforme à la grille salariale pour « " + l.poste()
                        + " » : " + l.budgetMensuelUnitaire() + " Ar (grille " + l.grilleMin() + " à " + l.grilleMax()
                        + " Ar). Demandez une correction ou rejetez la demande.");
            }
        }
    }

    /** Les lignes « poste à créer » deviennent de vrais postes du département du besoin. */
    private void creerPostesProposes(DemandeRecrutement d) {
        Besoin besoin = d.getBesoin();
        if (besoin.getDepartement() == null) {
            throw new BusinessException("Le département du besoin est obligatoire pour créer les postes proposés.");
        }
        Set<Long> dejaUtilises = new HashSet<>();
        for (BesoinPoste l : besoin.getLignes()) {
            if (l.getPoste() != null) {
                dejaUtilises.add(l.getPoste().getId());
            }
        }
        
        for (BesoinPoste l : besoin.getLignes()) {
            for(int i = 0; i < l.getEffectif(); i++){

            if (l.getPoste() != null) {
                continue;
            }
            Poste poste = posteRepository
                    .findFirstByDepartementIdAndLibelleIgnoreCase(besoin.getDepartement().getId(), l.getLibellePostePropose())
                    .orElseGet(() -> {
                        Poste p = new Poste();
                        p.setDepartement(besoin.getDepartement());
                        p.setLibelle(l.getLibellePostePropose());
                        return posteRepository.save(p);
                    });
            if (!dejaUtilises.add(poste.getId())) {
                throw new BusinessException("Le poste « " + poste.getLibelle() + " » apparaît deux fois dans ce besoin.");
            }
            l.setPoste(poste);
            }
        }
    }
}
