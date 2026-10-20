package com.projet.sigrh.service;

import com.projet.sigrh.model.*;
import com.projet.sigrh.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DemandeRecrutementService {

    @Autowired
    private DemandeRecrutementRepository demandeRepository;

    @Autowired
    private BesoinRepository besoinRepository;

    @Autowired
    private BesoinPosteRepository besoinPosteRepository;

    @Autowired
    private HistoriqueValidationRepository historiqueRepository;

    @Autowired
    private EmployeRepository employeRepository;

    public List<DemandeRecrutement> listerToutes() {
        return demandeRepository.findAll();
    }

    public List<DemandeRecrutement> listerParStatut(String statut) {
        return demandeRepository.findByStatut(statut);
    }

    /**
     * Étape 1 : Création de la demande de recrutement (Statut initial : BROUILLON)
     */
    @Transactional
    public DemandeRecrutement creerDemande(Besoin besoin, BesoinPoste besoinPoste, String idDemandeur, LocalDate dateArriveeSouhaitee, String circuit, boolean validationFinance) {
        // 1. Sauvegarde du besoin
        Besoin savedBesoin = besoinRepository.save(besoin);

        // 2. Association du poste au besoin
        besoinPoste.setBesoin(savedBesoin);
        besoinPosteRepository.save(besoinPoste);

        // 3. Récupération du demandeur
        Employe demandeur = employeRepository.findById(idDemandeur)
                .orElseThrow(() -> new RuntimeException("Demandeur introuvable avec l'ID : " + idDemandeur));

        // 4. Création de la demande de recrutement
        DemandeRecrutement demande = new DemandeRecrutement();
        demande.setBesoin(savedBesoin);
        demande.setDemandeur(demandeur);
        demande.setDateArriveeSouhaitee(dateArriveeSouhaitee);
        demande.setCircuit(circuit != null ? circuit : "STANDARD");
        demande.setValidationFinance(validationFinance);
        demande.setStatut("BROUILLON");

        return demandeRepository.save(demande);
    }

    /**
     * Étape 2 : Soumission de la demande par le manager (Passe de BROUILLON / A_CORRIGER à ATTENTE_RH)
     */
    @Transactional
    public DemandeRecrutement soumettreDemande(Long idDemande) {
        DemandeRecrutement demande = demandeRepository.findById(idDemande)
                .orElseThrow(() -> new RuntimeException("Demande de recrutement introuvable"));

        if (!"BROUILLON".equals(demande.getStatut()) && !"A_CORRIGER".equals(demande.getStatut())) {
            throw new RuntimeException("La demande ne peut pas être soumise depuis l'état : " + demande.getStatut());
        }

        demande.setStatut("ATTENTE_RH");
        return demandeRepository.save(demande);
    }

    /**
     * Étape 3 : Traitement de la validation (RH, Finance ou Direction) avec traçabilité dans l'historique
     */
    @Transactional
    public DemandeRecrutement traiterValidation(Long idDemande, String idValidateur, String etape, String decision, String commentaire) {
        DemandeRecrutement demande = demandeRepository.findById(idDemande)
                .orElseThrow(() -> new RuntimeException("Demande introuvable"));

        Employe validateur = employeRepository.findById(idValidateur)
                .orElseThrow(() -> new RuntimeException("Validateur introuvable"));

        // Règle : Motif obligatoire en cas de rejet ou de demande de correction
        if (!"APPROUVEE".equals(decision) && (commentaire == null || commentaire.trim().isEmpty())) {
            throw new RuntimeException("Un commentaire/motif est obligatoire en cas de rejet ou de correction.");
        }

        // Enregistrement de la traçabilité dans l'historique
        HistoriqueValidation histo = new HistoriqueValidation();
        histo.setIdDemande(demande.getId());
        histo.setValidateur(validateur);
        histo.setEtape(etape);
        histo.setDecision(decision);
        histo.setCommentaire(commentaire);
        histo.setDateValidation(LocalDateTime.now());
        historiqueRepository.save(histo);

        // Gestion du workflow et des transitions d'états
        if ("APPROUVEE".equals(decision)) {
            if ("RH".equals(etape)) {
                if (demande.isValidationFinance()) {
                    demande.setStatut("ATTENTE_FINANCE");
                } else {
                    demande.setStatut("ATTENTE_DIRECTION");
                }
            } else if ("FINANCE".equals(etape)) {
                demande.setStatut("ATTENTE_DIRECTION");
            } else if ("DIRECTION".equals(etape)) {
                demande.setStatut("VALIDEE"); // Prêt pour le Bloc 2 (Offre d'emploi)
            }
        } else if ("REJETEE".equals(decision)) {
            demande.setStatut("REJETEE");
        } else if ("A_CORRIGER".equals(decision)) {
            demande.setStatut("A_CORRIGER");
        }

        return demandeRepository.save(demande);
    }
}