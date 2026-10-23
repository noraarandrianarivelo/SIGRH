package com.projet.sigrh.service;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.projet.sigrh.exception.BusinessException;

/** Identité issue exclusivement du compte authentifié, jamais du formulaire. */
@Service
public class PersonnelAccess {
    private final UtilisateurService utilisateurs;
    public PersonnelAccess(UtilisateurService utilisateurs) { this.utilisateurs = utilisateurs; }

    public record Acteur(String employe, String role) {
        public boolean rh() { return "ROLE_RH".equals(role); }
        public boolean signature() { return rh() || "ROLE_DIRECTION".equals(role); }
        public boolean paie() { return rh() || "ROLE_FINANCE".equals(role); }
        public void exigerRh() { if (!rh()) refus(); }
        public void lire(String salarie) { if (!rh() && !employe.equals(salarie)) refus(); }
    }

    public Acteur acteur(Principal principal) {
        var compte = utilisateurs.getConnecte(principal);
        if (compte.getEmploye() == null) {
            throw new BusinessException("Ce compte n'est lié à aucun employé. Faites préparer votre accès salarié.", HttpStatus.FORBIDDEN);
        }
        return new Acteur(compte.getEmploye().getId(), compte.getRole());
    }

    public static void refus() {
        throw new BusinessException("Vous n'êtes pas autorisé à effectuer cette opération.", HttpStatus.FORBIDDEN);
    }
}
