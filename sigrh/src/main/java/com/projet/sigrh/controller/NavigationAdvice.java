package com.projet.sigrh.controller;

import java.security.Principal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.projet.sigrh.controller.RH.RHController;
import com.projet.sigrh.controller.Candidat.CandidatController;
import com.projet.sigrh.service.NavigationService;
import com.projet.sigrh.service.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice(assignableTypes = {
    PortalController.class, PersonnelController.class, RHController.class, CandidatController.class
})
public class NavigationAdvice {
    private final NavigationService navigation;
    private final UtilisateurService utilisateurs;

    public NavigationAdvice(NavigationService navigation, UtilisateurService utilisateurs) {
        this.navigation = navigation;
        this.utilisateurs = utilisateurs;
    }

    @ModelAttribute
    public void navigation(Principal principal, HttpServletRequest request, Model model) {
        if (principal != null) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            model.addAttribute("navigation", navigation.construire(utilisateurs.getConnecte(principal), path));
        }
    }
}
