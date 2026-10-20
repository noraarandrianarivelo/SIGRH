package com.projet.sigrh.controller;

import com.projet.sigrh.dto.DemandeRecrutementDTO;
import com.projet.sigrh.model.*;
import com.projet.sigrh.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/recrutement")
public class DemandeRecrutementController {

    @Autowired
    private UtilisateurRepository utilisateurRepository;
    
    @Autowired
    private PosteRepository posteRepository;
    
    @Autowired
    private DepartementRepository departementRepository;
    
    @Autowired
    private BesoinRepository besoinRepository;

    @Autowired
    private BesoinPosteRepository besoinPosteRepository;
    
    @Autowired
    private DemandeRecrutementRepository demandeRecrutementRepository;

    /**
     * RG : Création d'une demande de recrutement par le Manager (Statut initial : BROUILLON)
     */
    @PostMapping("/creer")
    public ResponseEntity<?> creerDemandeParManager(@RequestBody DemandeRecrutementDTO dto, Principal principal) {
        // 1. Identification de l'utilisateur connecté
        Utilisateur manager = utilisateurRepository.findByUsername(principal.getName());
        if (manager == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Utilisateur non authentifié.");
        }
        
        // 2. Vérification du rôle Manager ou Direction
        if (!manager.getRole().equals("ROLE_MANAGER") && !manager.getRole().equals("ROLE_DIRECTION")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Seul un manager ou la direction peut initier une demande.");
        }

        // 3. Vérification du département du manager (sauf si Direction)
        Long deptIdDemandeur = manager.getEmploye() != null && manager.getEmploye().getDepartement() != null 
                ? manager.getEmploye().getDepartement().getId() : null;
                
        if (!manager.getRole().equals("ROLE_DIRECTION") && (deptIdDemandeur == null || !deptIdDemandeur.equals(dto.getDepartementId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Erreur : Un manager ne peut créer une demande que pour son propre département.");
        }

        // 4. Traitement du poste (Poste Existant vs Création)
        Poste poste = null;
        String libellePropose = null;
        
        if ("CREATION".equals(dto.getTypeBesoin())) {
            libellePropose = dto.getLibelleNouveauPoste();
        } else {
            poste = posteRepository.findById(dto.getPosteId())
                    .orElseThrow(() -> new RuntimeException("Poste non trouvé"));
        }

        // 5. Enregistrement du Besoin (table besoin)
        Besoin besoin = new Besoin();
        besoin.setDepartement(departementRepository.findById(dto.getDepartementId()).orElseThrow());
        besoin.setTypeMotif(dto.getTypeMotif()); // REMPLACEMENT ou NOUVEAU_POSTE, etc.
        besoin.setMotif(dto.getMotif());
        besoinRepository.save(besoin);

        // 6. Enregistrement de BesoinPoste (contient l'effectif et le budget selon le script SQL)
        BesoinPoste besoinPoste = new BesoinPoste();
        besoinPoste.setBesoin(besoin);
        besoinPoste.setPoste(poste);
        besoinPoste.setLibellePostePropose(libellePropose);
        besoinPoste.setEffectif(dto.getEffectif());
        besoinPoste.setBudgetMensuelUnitaire(dto.getBudgetMensuelUnitaire());
        besoinPosteRepository.save(besoinPoste);

        // 7. Création de la Demande au statut BROUILLON
        DemandeRecrutement demande = new DemandeRecrutement();
        demande.setDemandeur(manager.getEmploye());
        demande.setBesoin(besoin);
        demande.setDateArriveeSouhaitee(dto.getDateArriveeSouhaitee());
        demande.setStatut("BROUILLON");
        demandeRecrutementRepository.save(demande);

        return ResponseEntity.ok(demande);
    }

    /**
     * RG : Soumission de la demande par le Manager (Brouillon -> ATTENTE_RH)
     */
    @PutMapping("/demandes/{id}/soumettre")
    public ResponseEntity<?> soumettreDemande(@PathVariable Long id, Principal principal) {
        DemandeRecrutement demande = demandeRecrutementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée"));

        // Vérifier que la demande est bien un BROUILLON ou A_CORRIGER
        if (!"BROUILLON".equals(demande.getStatut()) && !"A_CORRIGER".equals(demande.getStatut())) {
            return ResponseEntity.badRequest().body("Seule une demande au statut BROUILLON ou A_CORRIGER peut être soumise.");
        }

        // Passage au statut de validation RH
        demande.setStatut("ATTENTE_RH");
        demandeRecrutementRepository.save(demande);

        return ResponseEntity.ok(demande);
    }

    /**
     * Récupération de la liste de toutes les demandes pour affichage dans le tableau de bord
     */
    @GetMapping("/demandes")
    public ResponseEntity<List<DemandeRecrutement>> listerDemandes() {
        List<DemandeRecrutement> demandes = demandeRecrutementRepository.findAll();
        return ResponseEntity.ok(demandes);
    }

/**
     * RG : Validation ou action de la part du profil RH (ATTENTE_RH -> ATTENTE_FINANCE / A_CORRIGER / REJETEE)
     */
    @PutMapping("/demandes/{id}/valider")
    public ResponseEntity<?> validerDemandeParRh(@PathVariable Long id, @RequestParam String decision, Principal principal) {
        Utilisateur user = utilisateurRepository.findByUsername(principal.getName());
        if (user == null || (!"ROLE_RH".equals(user.getRole()) && !"ROLE_DIRECTION".equals(user.getRole()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Accès non autorisé.");
        }

        DemandeRecrutement demande = demandeRecrutementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée"));

        if (!"ATTENTE_RH".equals(demande.getStatut())) {
            return ResponseEntity.badRequest().body("Cette demande n'est pas en attente de validation RH.");
        }

        // Application du workflow selon la décision
        if ("APPROUVEE".equals(decision)) {
            demande.setStatut("ATTENTE_FINANCE"); // Passage à l'étape suivante (Finance)
        } else if ("A_CORRIGER".equals(decision)) {
            demande.setStatut("A_CORRIGER"); // Renvoyé au Manager pour modification
        } else if ("REJETEE".equals(decision)) {
            demande.setStatut("REJETEE"); // Clôturé / Rejeté
        }

        demandeRecrutementRepository.save(demande);
        return ResponseEntity.ok(demande);
    }
}