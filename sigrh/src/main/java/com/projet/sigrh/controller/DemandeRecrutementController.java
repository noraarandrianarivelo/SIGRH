package com.projet.sigrh.controller;

import com.projet.sigrh.dto.CreerDemandeRequest;
import com.projet.sigrh.model.DemandeRecrutement;
import com.projet.sigrh.service.DemandeRecrutementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recrutement/demandes")
@CrossOrigin(origins = "*")
public class DemandeRecrutementController {

    @Autowired
    private DemandeRecrutementService service;

    @GetMapping
        public ResponseEntity<List<DemandeRecrutement>> listerToutes() {
            return ResponseEntity.ok(service.listerToutes()); 
        }
    @GetMapping("/statut/{statut}")
    public ResponseEntity<List<DemandeRecrutement>> listerParStatut(@PathVariable String statut) {
        return ResponseEntity.ok(service.listerParStatut(statut));
    }

    /**
     * 1. Création d'une nouvelle demande (Brouillon)
     */
    @PostMapping("/creer")
    public ResponseEntity<DemandeRecrutement> creerDemande(@RequestBody CreerDemandeRequest request) {
        DemandeRecrutement nouvelleDemande = service.creerDemande(
                request.getBesoin(),
                request.getBesoinPoste(),
                request.getIdDemandeur(),
                request.getDateArriveeSouhaitee(),
                request.getCircuit(),
                request.isValidationFinance()
        );
        return ResponseEntity.ok(nouvelleDemande);
    }

    /**
     * 2. Soumission de la demande par le Manager (Passe à ATTENTE_RH)
     */
    @PutMapping("/{id}/soumettre")
    public ResponseEntity<DemandeRecrutement> soumettreDemande(@PathVariable Long id) {
        return ResponseEntity.ok(service.soumettreDemande(id));
    }

    /**
     * 3. Traitement de la validation (RH, Finance, Direction)
     */
    @PostMapping("/{id}/valider")
    public ResponseEntity<DemandeRecrutement> validerDemande(
            @PathVariable Long id,
            @RequestParam String idValidateur,
            @RequestParam String etape,      // RH, FINANCE, DIRECTION
            @RequestParam String decision,   // APPROUVEE, REJETEE, A_CORRIGER
            @RequestParam(required = false) String commentaire) {

        DemandeRecrutement resultat = service.traiterValidation(id, idValidateur, etape, decision, commentaire);
        return ResponseEntity.ok(resultat);
    }
}