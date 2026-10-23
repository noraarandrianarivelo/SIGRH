package com.projet.sigrh.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.security.Roles;
import com.projet.sigrh.service.PersonnelAccess.Acteur;

@Service
@Transactional(readOnly = true)
public class PortalService {
    public record Indicator(String label, long value, String detail, String icon, String href) { }
    public record Column(String key, String label) { }
    private final JdbcTemplate db;
    private final DemandeRecrutementService demandes;
    private final Clock clock;

    public PortalService(JdbcTemplate db, DemandeRecrutementService demandes, Clock clock) {
        this.db = db;
        this.demandes = demandes;
        this.clock = clock;
    }

    public String dateDuJour() {
        return LocalDate.now(clock).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH));
    }

    public List<Indicator> indicateurs(Utilisateur u) {
        List<Indicator> result = new ArrayList<>();
        if (Roles.CANDIDAT.equals(u.getRole())) {
            result.add(new Indicator("Offres disponibles", count("SELECT COUNT(*) FROM offre_emploi WHERE statut='OUVERT'"),
                    "Opportunités ouvertes aux candidatures", "briefcase", "/candidat/offre-emploi"));
            result.add(new Indicator("Propositions en attente", count("""
                    SELECT COUNT(*) FROM proposition_embauche p JOIN candidature ca ON ca.id=p.id_candidature
                    JOIN candidat c ON c.id=ca.id_candidat WHERE c.email=? AND p.statut='EN_ATTENTE'
                    """, u.getUsername()), "Réponses à transmettre au service RH", "signature", "/candidat/propositions"));
            return result;
        }
        if (u.getEmploye() != null) {
            result.add(new Indicator("Mes tâches à traiter", count("SELECT COUNT(*) FROM onboarding_tache WHERE id_responsable=? AND statut<>'TERMINEE'", u.getEmploye().getId()),
                    "Tâches d’intégration qui vous sont attribuées", "check", "/personnel/taches"));
            if (List.of(Roles.MANAGER, Roles.RH, Roles.FINANCE, Roles.DIRECTION).contains(u.getRole())) {
                var visibles = demandes.lister(u);
                long aValider = visibles.stream().filter(d -> !d.decisionsPossibles().isEmpty()).count();
                result.add(new Indicator("Demandes à valider", aValider, "Décisions disponibles dans votre périmètre", "clipboard", "/demandes"));
            }
        }
        if (List.of(Roles.RH, Roles.DIRECTION).contains(u.getRole())) {
            result.add(new Indicator("Offres ouvertes", count("SELECT COUNT(*) FROM offre_emploi WHERE statut='OUVERT'"),
                    "Recrutements actuellement ouverts", "briefcase", "/rh/offres"));
        }
        if (Roles.RH.equals(u.getRole())) {
            result.add(new Indicator("Intégrations en cours", count("SELECT COUNT(*) FROM suivi_onboarding WHERE statut<>'TERMINE'"),
                    "Parcours dont les étapes restent à compléter", "progress", "/personnel/integration"));
        }
        return result;
    }

    public List<Map<String, Object>> candidatures(Utilisateur u) {
        recrutement(u);
        var rows = db.queryForList("""
                SELECT ca.id, concat_ws(' ',c.prenom,c.nom) AS candidat, o.titre AS offre,
                    ca.statut, ca.date_candidature AS date
                FROM candidature ca JOIN candidat c ON c.id=ca.id_candidat
                JOIN offre_emploi o ON o.id=ca.id_offre ORDER BY ca.date_candidature DESC,ca.id DESC
                """);
        rows.forEach(r -> r.put("href", "/rh/candidatures/" + r.get("id")));
        return rows;
    }

    public List<Map<String, Object>> propositions(Utilisateur u) {
        recrutement(u);
        var rows = db.queryForList("""
                SELECT p.id, concat_ws(' ',c.prenom,c.nom) AS candidat, o.titre AS offre,
                    p.statut, p.date_envoi AS date, p.date_debut_prevue AS debut, p.salaire_mensuel AS salaire,
                    ca.id AS candidature
                FROM proposition_embauche p JOIN candidature ca ON ca.id=p.id_candidature
                JOIN candidat c ON c.id=ca.id_candidat JOIN offre_emploi o ON o.id=ca.id_offre
                ORDER BY p.date_envoi DESC,p.id DESC
                """);
        rows.forEach(r -> r.put("href", "/rh/candidatures/" + r.get("candidature")));
        return rows;
    }

    public List<Map<String, Object>> contrats(Acteur a) {
        var rows = db.queryForList("""
                SELECT c.*, concat_ws(' ',e.prenom,e.nom) AS salarie, p.libelle AS poste
                FROM contrat c JOIN employe e ON e.id=c.id_employe JOIN poste p ON p.id=c.id_poste
                WHERE (? OR c.id_employe=?) ORDER BY c.date_debut DESC,c.id DESC
                """, a.rh(), a.employe());
        Map<String, Long> courants = new HashMap<>();
        for (var row : rows) {
            row.put("situation", PersonnelService.situation(row, LocalDate.now(clock)));
            row.put("href", "/personnel/employes/" + row.get("id_employe") + "#contrat-" + row.get("id"));
            if ("En vigueur".equals(row.get("situation"))) {
                courants.merge((String) row.get("id_employe"), 1L, Long::sum);
            }
        }
        rows.stream().filter(r -> "En vigueur".equals(r.get("situation"))
                && courants.getOrDefault(r.get("id_employe"), 0L) > 1)
                .forEach(r -> r.put("situation", "Conflit : plusieurs contrats en vigueur"));
        return rows;
    }

    public List<Map<String, Object>> affectations(Acteur a) {
        var rows = db.queryForList("""
                SELECT af.*, concat_ws(' ',e.prenom,e.nom) AS salarie,
                    p.libelle AS poste,d.libelle AS departement
                FROM affectation af JOIN employe e ON e.id=af.id_employe
                JOIN poste p ON p.id=af.id_poste JOIN departement d ON d.id=p.id_departement
                WHERE (? OR af.id_employe=?) ORDER BY af.date_debut DESC,af.id DESC
                """, a.rh(), a.employe());
        LocalDate today = LocalDate.now(clock);
        for (var row : rows) {
            LocalDate debut = PersonnelService.date(row, "date_debut"), fin = PersonnelService.date(row, "date_fin");
            row.put("situation", debut.isAfter(today) ? "Future" : fin == null || !fin.isBefore(today) ? "Actuelle" : "Historique");
            row.put("href", "/personnel/employes/" + row.get("id_employe") + "#affectations");
        }
        return rows;
    }

    public List<Map<String, Object>> integrations(Acteur a) {
        var rows = db.queryForList("""
                SELECT s.*, concat_ws(' ',e.prenom,e.nom) AS salarie,c.date_debut AS date,
                    s.nombre_etapes_terminees||' / '||s.nombre_etapes AS progression
                FROM suivi_onboarding s JOIN employe e ON e.id=s.id_employe
                JOIN contrat c ON c.id=s.id_contrat WHERE (? OR s.id_employe=?) ORDER BY c.date_debut DESC
                """, a.rh(), a.employe());
        rows.forEach(r -> r.put("href", "/personnel/employes/" + r.get("id_employe") + "#integration"));
        return rows;
    }

    private long count(String sql, Object... args) {
        Long value = db.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private void recrutement(Utilisateur u) {
        if (!List.of(Roles.RH, Roles.DIRECTION).contains(u.getRole())) {
            throw new BusinessException("Cet écran est réservé aux RH et à la Direction.", HttpStatus.FORBIDDEN);
        }
    }
}
