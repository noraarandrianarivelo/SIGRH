package com.projet.sigrh.controller;

import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @GetMapping("/api/auth/current-user")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Non connecté");
        }
        
        Utilisateur utilisateur = utilisateurRepository.findByUsername(principal.getName());
        if (utilisateur == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur introuvable");
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("username", utilisateur.getUsername());
        response.put("role", utilisateur.getRole()); // Fait appel à la méthode getRole() de l'entité
        response.put("nom", utilisateur.getEmploye() != null ? utilisateur.getEmploye().getNom() : utilisateur.getUsername());
        
        return ResponseEntity.ok(response);
    }
}