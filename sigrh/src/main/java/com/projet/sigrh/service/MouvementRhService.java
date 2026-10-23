package com.projet.sigrh.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.projet.sigrh.exception.BusinessException;
import org.springframework.http.HttpStatus;
import com.projet.sigrh.service.PersonnelAccess.Acteur;

/** Snapshots de lignes contrôlées : aucune relation ORM ni donnée d'authentification. */
@Service
public class MouvementRhService {
    private static final Set<String> TABLES = Set.of("contrat", "affectation", "document_contrat", "employe",
            "onboarding_tache", "compte_employe", "badge_employe", "materiel", "materiel_employe",
            "formation", "formation_employe", "inscription_paie");
    private final JdbcTemplate db;
    public MouvementRhService(JdbcTemplate db) { this.db = db; }

    public String snapshot(String table, Object id) {
        verifierTable(table);
        return db.queryForObject("SELECT to_jsonb(t)::text FROM " + table + " t WHERE id::text = ?", String.class, id.toString());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void tracer(Acteur acteur, String employe, String table, Object id, String avant) {
        tracer(acteur, employe, table, id, avant, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void tracer(Acteur acteur, String employe, String table, Object id, String avant, String preuveTable, Object preuveId) {
        verifierTable(table);
        String apres = snapshot(table, id);
        if (avant != null && avant.equals(apres) && preuveTable == null) return;
        if (preuveTable != null) verifierTable(preuveTable);
        db.update("""
            INSERT INTO mouvement_rh(id_employe_concerne,id_acteur,table_concernee,id_ligne,action,valeurs_avant,valeurs_apres)
            VALUES (?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb) ||
                CASE WHEN CAST(? AS text) IS NULL THEN '{}'::jsonb ELSE
                jsonb_build_object('_preuve',jsonb_build_object('table',CAST(? AS text),'id',CAST(? AS text))) END)
            """, employe, acteur.employe(), table, id.toString(), avant == null ? "CREATION" : "MODIFICATION",
                avant, apres, preuveTable, preuveTable, preuveId == null ? null : preuveId.toString());
    }

    public List<Map<String,Object>> lister(Acteur acteur, String employe, String table, String action, int page) {
        acteur.exigerRh();
        if (table != null && !table.isBlank()) verifierTable(table);
        return db.queryForList("""
            SELECT m.*, concat(e.prenom,' ',e.nom) AS acteur_nom,
              jsonb_pretty(valeurs_avant) AS avant_lisible,jsonb_pretty(valeurs_apres) AS apres_lisible
            FROM mouvement_rh m LEFT JOIN employe e ON e.id=m.id_acteur
            WHERE (?='' OR m.id_employe_concerne=?) AND (?='' OR table_concernee=?) AND (?='' OR action=?)
            ORDER BY date_mouvement DESC,m.id DESC LIMIT 100 OFFSET ?
            """, employe, employe, table, table, action, action, Math.max(0, page)*100);
    }

    private void verifierTable(String table) {
        if (!TABLES.contains(table)) throw new BusinessException("Objet d'historique inconnu.", HttpStatus.BAD_REQUEST);
    }
}
