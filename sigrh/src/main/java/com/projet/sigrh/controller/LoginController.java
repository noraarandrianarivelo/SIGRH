package com.projet.sigrh.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Le contrôleur affiche le formulaire ; le filtre Spring Security traite son POST. */
@Controller
public class LoginController {
    public static final String LAST_USERNAME = "sigrh.login.username";
    public static final String ERROR_KIND = "sigrh.login.error";

    @GetMapping("/login")
    public String login(Authentication authentication, HttpServletRequest request, Model model,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String profile) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }

        HttpSession session = request.getSession(false);
        String username = session == null ? "" : (String) session.getAttribute(LAST_USERNAME);
        String kind = session == null ? null : (String) session.getAttribute(ERROR_KIND);
        model.addAttribute("username", username == null ? "" : username);

        if (error != null) {
            model.addAttribute("loginError", "service".equals(kind)
                    ? "Le service de connexion est momentanément indisponible. Réessayez dans quelques instants."
                    : "L’identifiant ou le mot de passe est incorrect. Vérifiez votre saisie et réessayez.");
        } else if (profile != null) {
            model.addAttribute("loginError", "Votre accès candidat doit être complété. Contactez le service RH.");
        }
        if (logout != null) {
            model.addAttribute("loginNotice", "Vous avez été déconnecté. À bientôt sur SIGRH.");
        } else if (expired != null) {
            model.addAttribute("loginNotice", "Votre session a expiré. Connectez-vous à nouveau pour continuer.");
        }
        if (session != null) {
            session.removeAttribute(ERROR_KIND);
            session.removeAttribute(LAST_USERNAME);
        }
        return "auth/login";
    }
}
