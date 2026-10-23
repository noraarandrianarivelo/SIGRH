package com.projet.sigrh.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.service.*;
import com.projet.sigrh.service.PortalService.Column;
import com.projet.sigrh.security.Roles;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class PortalController {
    private final UtilisateurService utilisateurs;
    private final NavigationService navigation;
    private final PortalService portal;
    private final PersonnelAccess access;

    public PortalController(UtilisateurService utilisateurs, NavigationService navigation,
            PortalService portal, PersonnelAccess access) {
        this.utilisateurs = utilisateurs;
        this.navigation = navigation;
        this.portal = portal;
        this.access = access;
    }

    @GetMapping("/")
    public String accueil(Principal principal, Model model) {
        var u = utilisateurs.getConnecte(principal);
        model.addAttribute("indicateurs", portal.indicateurs(u));
        model.addAttribute("dateDuJour", portal.dateDuJour());
        return "portal/accueil";
    }

    @GetMapping("/index.html")
    public String ancienAccueil() {
        return "redirect:/demandes";
    }

    @GetMapping("/demandes")
    public String demandes(Principal principal) {
        var u = utilisateurs.getConnecte(principal);
        if (u.getEmploye() == null || !List.of(Roles.MANAGER, Roles.RH, Roles.FINANCE, Roles.DIRECTION).contains(u.getRole())) {
            return "redirect:/modules/besoins";
        }
        return "portal/demandes";
    }

    @GetMapping("/modules/{module}")
    public String module(@PathVariable String module, Principal principal, Model model) {
        var context = navigation.construire(utilisateurs.getConnecte(principal), "/modules/" + module);
        var groupe = context.groups().stream().filter(g -> g.key().equals(module)
                && !List.of("accueil", "candidat").contains(g.key())).findFirst()
                .orElseThrow(() -> new BusinessException("Cet espace est introuvable.", HttpStatus.NOT_FOUND));
        model.addAttribute("module", groupe);
        return "portal/module";
    }

    @GetMapping("/rh/candidatures")
    public String candidatures(Principal principal, Model model) {
        return table(model, "Candidatures", "Tous les dossiers de candidature, regroupés par offre et par état.",
                "Consulter le dossier", portal.candidatures(utilisateurs.getConnecte(principal)),
                List.of(new Column("candidat", "Candidat"), new Column("offre", "Offre"), new Column("date", "Dépôt"), new Column("statut", "État")));
    }

    @GetMapping("/rh/propositions")
    public String propositions(Principal principal, Model model) {
        return table(model, "Propositions d’embauche", "Suivez les propositions envoyées, les réponses et les conditions proposées.",
                "Consulter la candidature", portal.propositions(utilisateurs.getConnecte(principal)),
                List.of(new Column("candidat", "Candidat"), new Column("offre", "Offre"), new Column("debut", "Début prévu"), new Column("salaire", "Salaire / MGA"), new Column("statut", "État")));
    }

    @GetMapping("/personnel/contrats")
    public String contrats(Principal principal, Model model) {
        return table(model, "Contrats", "Contrats actuels, préparés et historiques de votre périmètre. Ouvrez le dossier pour consulter les pièces et gérer les renouvellements.",
                "Ouvrir le dossier", portal.contrats(access.acteur(principal)),
                List.of(new Column("salarie", "Salarié"), new Column("type_contrat", "Type"), new Column("date_debut", "Début"), new Column("date_fin", "Fin prévue"), new Column("situation", "Situation")));
    }

    @GetMapping("/personnel/affectations")
    public String affectations(Principal principal, Model model) {
        return table(model, "Affectations", "Retrouvez les postes, les départements et l’historique des mobilités de votre périmètre.",
                "Ouvrir le dossier", portal.affectations(access.acteur(principal)),
                List.of(new Column("salarie", "Salarié"), new Column("poste", "Poste"), new Column("departement", "Département"), new Column("lieu", "Lieu"), new Column("situation", "Situation")));
    }

    @GetMapping("/personnel/integration")
    public String integration(Principal principal, Model model) {
        return table(model, "Suivi d’intégration", "Chaque parcours conserve son contrat d’entrée. La progression est calculée à partir des tâches réalisées.",
                "Voir le parcours", portal.integrations(access.acteur(principal)),
                List.of(new Column("salarie", "Salarié"), new Column("date", "Entrée"), new Column("progression", "Étapes terminées"), new Column("statut", "État du parcours")));
    }

    @RequestMapping("/acces-refuse")
    public String accesRefuse(HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        return "portal/acces-refuse";
    }

    private String table(Model model, String titre, String description, String action,
            List<Map<String, Object>> lignes, List<Column> colonnes) {
        model.addAttribute("titre", titre);
        model.addAttribute("description", description);
        model.addAttribute("action", action);
        model.addAttribute("lignes", lignes);
        model.addAttribute("colonnes", colonnes);
        return "portal/liste";
    }
}
