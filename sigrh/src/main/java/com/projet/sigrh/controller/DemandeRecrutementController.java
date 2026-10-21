package com.projet.sigrh.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.projet.sigrh.dto.DemandeRequest;
import com.projet.sigrh.dto.DemandeResponse;
import com.projet.sigrh.service.DemandeRecrutementService;
import com.projet.sigrh.service.UtilisateurService;

/** Côté Manager : créer, modifier, soumettre, consulter. La logique est dans le service. */
@RestController
@RequestMapping("/api/recrutement/demandes")
public class DemandeRecrutementController {

    @Autowired
    private DemandeRecrutementService demandeService;
    @Autowired
    private UtilisateurService utilisateurService;

    @GetMapping
    public List<DemandeResponse> lister(Principal principal) {
        return demandeService.lister(utilisateurService.getConnecte(principal));
    }

    @GetMapping("/{id}")
    public DemandeResponse detail(@PathVariable Long id, Principal principal) {
        return demandeService.detail(id, utilisateurService.getConnecte(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DemandeResponse creer(@RequestBody DemandeRequest request, Principal principal) {
        return demandeService.creer(request, utilisateurService.getConnecte(principal));
    }

    @PutMapping("/{id}")
    public DemandeResponse modifier(@PathVariable Long id, @RequestBody DemandeRequest request, Principal principal) {
        return demandeService.modifier(id, request, utilisateurService.getConnecte(principal));
    }

    @PutMapping("/{id}/soumettre") 
    public DemandeResponse soumettre(@PathVariable Long id, Principal principal) {
        return demandeService.soumettre(id, utilisateurService.getConnecte(principal));
    }
}