package com.projet.sigrh.controller;

import com.projet.sigrh.dto.DecisionRequest;
import com.projet.sigrh.dto.DemandeResponse;
import com.projet.sigrh.service.UtilisateurService;
import com.projet.sigrh.service.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/** Côté RH / Finance / Direction : une décision par appel. */
@RestController
@RequestMapping("/api/recrutement/demandes")
public class ValidationController {

    @Autowired
    private ValidationService validationService;
    @Autowired
    private UtilisateurService utilisateurService;

    @PostMapping("/{id}/decision")
    public DemandeResponse decider(@PathVariable Long id, @RequestBody DecisionRequest request, Principal principal) {
        return validationService.decider(id, request, utilisateurService.getConnecte(principal));
    }
}