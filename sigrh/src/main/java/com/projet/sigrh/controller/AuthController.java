package com.projet.sigrh.controller;

import com.projet.sigrh.model.Employe;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.service.UtilisateurService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private UtilisateurService utilisateurService;

    /** Qui est connecté ? Utilisé par la page pour afficher les bons blocs selon le rôle. */
    @GetMapping("/api/auth/current-user")
    public Map<String, Object> currentUser(Principal principal) {
        Utilisateur u = utilisateurService.getConnecte(principal);
        Employe e = u.getEmploye();

        Map<String, Object> response = new HashMap<>();
        response.put("username", u.getUsername());
        response.put("role", u.getRole());
        response.put("nom", e != null ? e.getNomComplet() : u.getUsername());
        response.put("departementId", e != null && e.getDepartement() != null ? e.getDepartement().getId() : null);
        return response;
    }
}