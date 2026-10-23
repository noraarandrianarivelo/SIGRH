package com.projet.sigrh.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.security.Roles;

/** Registre commun au menu, à l'accueil, aux présentations et à la recherche d'écran. */
@Service
public class NavigationService {
    public record Item(String key, String label, String description, String path, String href,
            String icon, String permission, boolean available, boolean active) { }
    public record Group(String key, String label, String description, String icon, String overview,
            String number, List<Item> items) { }
    public record Context(List<Group> groups, String section, String title, String activeGroup,
            String username, String name, String firstName, String initials, String roleLabel,
            boolean candidate) { }

    public Context construire(Utilisateur u, String path) {
        String role = u.getRole();
        boolean rh = Roles.RH.equals(role);
        boolean recrutement = rh || Roles.DIRECTION.equals(role);
        boolean candidat = Roles.CANDIDAT.equals(role);
        boolean salarie = u.getEmploye() != null;
        boolean demandes = salarie && List.of(Roles.MANAGER, Roles.RH, Roles.FINANCE, Roles.DIRECTION).contains(role);
        String active = activeKey(path);
        List<Group> groups = new ArrayList<>();

        groups.add(new Group("accueil", "Vue d’ensemble", "Vos espaces et vos actions, au même endroit.", "home", "/", "", List.of(
                item("accueil", "Tableau de bord", "Retrouver tous les espaces de SIGRH", "/", "home", "Tout compte connecté", true, active, "accueil"))));

        if (candidat) {
            groups.add(new Group("candidat", "Mon espace candidat", "Consulter les opportunités et répondre à vos propositions.", "user", "/candidat/offre-emploi", "", List.of(
                    item("candidat-offres", "Offres disponibles", "Consulter les offres et déposer une candidature", "/candidat/offre-emploi", "briefcase", "Candidat", true, active, "candidat"),
                    item("candidat-propositions", "Mes propositions", "Consulter et répondre aux propositions reçues", "/candidat/propositions", "signature", "Candidat", true, active, "candidat"))));
        }

        groups.add(new Group("besoins", "Besoins en personnel", "Exprimer un besoin et suivre les validations jusqu’à l’ouverture du recrutement.", "clipboard", "/modules/besoins", "01", List.of(
                item("demandes", "Demandes & validations", "Créer, soumettre et valider les besoins en personnel", "/demandes", "clipboard", "Manager, RH, Finance ou Direction", demandes, active, "besoins"))));

        groups.add(new Group("recrutement", "Recrutement", "Retrouver les offres, les candidatures et les évaluations dans le même espace.", "briefcase", "/modules/recrutement", "02", List.of(
                item("offres", "Offres d’emploi", "Ouvrir une offre et consulter les candidatures associées", "/rh/offres", "briefcase", "RH ou Direction", recrutement, active, "recrutement"),
                item("candidatures", "Candidatures", "Consulter les dossiers et prendre une décision", "/rh/candidatures", "people", "RH ou Direction", recrutement, active, "recrutement"),
                item("evaluations", "Évaluations", "Planifier les séances et enregistrer les résultats", "/rh/evaluations", "calendar", "RH ou Direction", recrutement, active, "recrutement"))));

        groups.add(new Group("embauche", "Embauche", "Passer de la décision de recrutement à une proposition et au premier contrat.", "signature", "/modules/embauche", "03", List.of(
                item("propositions", "Propositions d’embauche", "Suivre les propositions envoyées et les réponses des candidats", "/rh/propositions", "signature", "RH ou Direction", recrutement, active, "embauche"))));

        groups.add(new Group("personnel", "Personnel & intégration", "Accompagner la vie du salarié, ses affectations et son intégration.", "people", "/modules/personnel", "04", List.of(
                item("dossiers", "Dossiers salariés", "Consulter les dossiers de votre périmètre", "/personnel", "people", "RH ou salarié sur son propre dossier", salarie, active, "personnel"),
                item("contrats", "Contrats", "Retrouver les contrats, les renouvellements et les documents", "/personnel/contrats", "document", "RH ou salarié sur ses propres contrats", salarie, active, "personnel"),
                item("affectations", "Affectations", "Suivre les postes, les départements et les lieux de travail", "/personnel/affectations", "layers", "RH ou salarié sur ses propres affectations", salarie, active, "personnel"),
                item("integration", "Suivi d’intégration", "Consulter l’avancement des six étapes d’intégration", "/personnel/integration", "progress", "RH ou salarié sur son propre suivi", salarie, active, "personnel"),
                item("taches", "Mes tâches", "Traiter les tâches dont vous êtes le responsable actuel", "/personnel/taches", "check", "Salarié responsable d’une tâche", salarie, active, "personnel"),
                item("catalogues", "Catalogues & ressources", "Gérer les matériels et les formations de référence", "/personnel/catalogues", "box", "RH", rh, active, "personnel"),
                item("historique", "Historique RH", "Consulter les mouvements et leurs états avant et après", "/personnel/historique", "history", "RH", rh, active, "personnel"))));

        Group currentGroup = groups.stream().filter(g -> g.items().stream().anyMatch(Item::active)
                || path.equals(g.overview())).findFirst().orElse(groups.get(0));
        String title = currentGroup.items().stream().filter(Item::active).map(Item::label).findFirst().orElse(currentGroup.label());
        String first = salarie && u.getEmploye().getPrenom() != null ? u.getEmploye().getPrenom() : u.getUsername();
        String name = salarie ? u.getEmploye().getNomComplet() : u.getUsername();
        String initials = initials(name);
        return new Context(List.copyOf(groups), currentGroup.label(), title, currentGroup.key(),
                u.getUsername(), name, first, initials, roleLabel(role), candidat);
    }

    private Item item(String key, String label, String description, String path, String icon,
            String permission, boolean available, String active, String group) {
        String href = available ? path : "/modules/" + group + "#" + key;
        return new Item(key, label, description, path, href, icon, permission, available, key.equals(active));
    }

    private String activeKey(String path) {
        if (path.equals("/")) return "accueil";
        if (path.startsWith("/candidat/propositions")) return "candidat-propositions";
        if (path.startsWith("/candidat/")) return "candidat-offres";
        if (path.startsWith("/demandes")) return "demandes";
        if (path.startsWith("/rh/propositions") || path.matches("/rh/candidatures/[^/]+/proposition.*")) return "propositions";
        if (path.startsWith("/rh/candidatures")) return "candidatures";
        if (path.startsWith("/rh/offres")) return "offres";
        if (path.startsWith("/rh/evaluations")) return "evaluations";
        if (path.startsWith("/personnel/taches")) return "taches";
        if (path.startsWith("/personnel/catalogues")) return "catalogues";
        if (path.startsWith("/personnel/historique")) return "historique";
        if (path.startsWith("/personnel/contrats") || path.matches("/personnel/employes/[^/]+/contrat.*")) return "contrats";
        if (path.startsWith("/personnel/affectations")) return "affectations";
        if (path.startsWith("/personnel/integration")) return "integration";
        if (path.startsWith("/personnel")) return "dossiers";
        return "";
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        return (parts[0].substring(0, 1) + (parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "")).toUpperCase(Locale.ROOT);
    }

    private String roleLabel(String role) {
        return switch (role) {
            case Roles.RH -> "Ressources humaines";
            case Roles.MANAGER -> "Manager";
            case Roles.FINANCE -> "Finance";
            case Roles.DIRECTION -> "Direction";
            case Roles.CANDIDAT -> "Candidat";
            default -> "Salarié";
        };
    }
}
