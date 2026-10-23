package com.projet.sigrh.service;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projet.sigrh.dto.personnel.RealisationForm;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.service.PersonnelAccess.Acteur;
import static com.projet.sigrh.service.PersonnelService.*;

@Service
@Transactional(readOnly = true)
public class OnboardingService {
    public static final List<String> ETAPES=List.of("SIGNATURE_CONTRAT","BADGE","COMPTE","MATERIEL","FORMATION","INSCRIPTION_PAIE");
    public static final List<String> STATUTS=List.of("A_FAIRE","EN_COURS","BLOQUEE","TERMINEE");
    private final JdbcTemplate db;
    private final PersonnelService personnel;
    private final MouvementRhService audit;
    public OnboardingService(JdbcTemplate db,PersonnelService personnel,MouvementRhService audit) {
        this.db=db; this.personnel=personnel; this.audit=audit;
    }
    public List<Map<String,Object>> suivi(Acteur a,String employe) {
        a.lire(employe);
        return db.queryForList("SELECT s.*,c.date_debut FROM suivi_onboarding s JOIN contrat c ON c.id=s.id_contrat WHERE s.id_employe=? ORDER BY c.date_debut DESC",employe);
    }
    public List<Map<String,Object>> etapes(Acteur a,String employe) {
        a.lire(employe);
        return db.queryForList("SELECT * FROM suivi_onboarding_etape WHERE id_employe=? ORDER BY id_contrat,etape",employe);
    }
    public List<Map<String,Object>> tachesEmploye(Acteur a,String employe) {
        a.lire(employe);
        return db.queryForList("""
            SELECT t.*,concat(e.prenom,' ',e.nom) AS responsable FROM onboarding_tache t
            JOIN contrat c ON c.id=t.id_contrat JOIN employe e ON e.id=t.id_responsable
            WHERE c.id_employe=? ORDER BY t.id_contrat,t.etape,t.id
            """,employe);
    }
    public List<Map<String,Object>> mesTaches(Acteur a,String statut) {
        if(!statut.isBlank() && !STATUTS.contains(statut)) throw new BusinessException("Statut de tâche inconnu.");
        return db.queryForList("""
            SELECT t.id,t.etape,t.libelle,t.statut,t.id_contrat,concat(e.prenom,' ',e.nom) AS salarie
            FROM onboarding_tache t JOIN contrat c ON c.id=t.id_contrat JOIN employe e ON e.id=c.id_employe
            WHERE t.id_responsable=? AND (?='' OR t.statut=?) ORDER BY c.date_debut,t.id
            """,a.employe(),statut,statut);
    }
    public Map<String,Object> tache(Acteur a,int id) {
        var t=personnel.ligne("""
            SELECT t.*,c.id_employe,c.date_debut,c.date_creation,concat(e.prenom,' ',e.nom) AS salarie
            FROM onboarding_tache t JOIN contrat c ON c.id=t.id_contrat JOIN employe e ON e.id=c.id_employe WHERE t.id=?
            """,id);
        if(!a.employe().equals(t.get("id_responsable"))) PersonnelAccess.refus();
        return t;
    }
    public Map<String,Object> suiviTache(Acteur a,int id) {
        var t=tache(a,id);
        return personnel.ligne("SELECT * FROM suivi_onboarding WHERE id_contrat=?",t.get("id_contrat"));
    }
    public List<Map<String,Object>> responsables(Acteur a) {
        a.exigerRh();
        return db.queryForList("SELECT e.id,concat(e.prenom,' ',e.nom) AS nom,r.role_name FROM employe e JOIN utilisateur u ON u.id_employee=e.id JOIN role_user r ON r.id=u.id_role ORDER BY e.nom");
    }
    private void verifierResponsable(String id,String etape) {
        var r=personnel.ligne("SELECT r.role_name FROM utilisateur u JOIN role_user r ON r.id=u.id_role WHERE u.id_employee=?",id);
        String role=texte(r,"role_name");
        if("SIGNATURE_CONTRAT".equals(etape) && !List.of("ROLE_RH","ROLE_DIRECTION").contains(role)) throw new BusinessException("La signature nécessite un responsable RH ou Direction.");
        if("INSCRIPTION_PAIE".equals(etape) && !List.of("ROLE_RH","ROLE_FINANCE").contains(role)) throw new BusinessException("La paie nécessite un responsable RH ou Finance.");
    }
    @Transactional
    public void creer(Acteur a,int contrat,String etape,String libelle,String responsable) {
        a.exigerRh(); if(!ETAPES.contains(etape)) throw new BusinessException("Étape inconnue.");
        texteRequis(libelle,200,"Résultat attendu");
        var c=personnel.contratInterne(contrat); String employe=texte(c,"id_employe");
        personnel.verrouillerEmploye(employe);
        if(personnel.entree(contrat)!=contrat) throw new BusinessException("Les tâches doivent rester sur le contrat d'entrée, jamais sur un renouvellement.");
        verifierResponsable(responsable,etape);
        int id=db.queryForObject("INSERT INTO onboarding_tache(id_contrat,etape,libelle,id_assignateur,id_responsable) VALUES (?,?,?,?,?) RETURNING id",Integer.class,contrat,etape,libelle.trim(),a.employe(),responsable);
        audit.tracer(a,employe,"onboarding_tache",id,null);
    }
    @Transactional
    public void reassigner(Acteur a,int id,String responsable,String libelle) {
        a.exigerRh(); texteRequis(libelle,200,"Résultat attendu");
        var t=verrouiller(id); verifierResponsable(responsable,texte(t,"etape"));
        String avant=audit.snapshot("onboarding_tache",id);
        db.update("UPDATE onboarding_tache SET id_responsable=?,libelle=? WHERE id=?",responsable,libelle.trim(),id);
        audit.tracer(a,texte(t,"id_employe"),"onboarding_tache",id,avant);
    }
    private Map<String,Object> verrouiller(int id) {
        var ref=personnel.ligne("SELECT c.id_employe FROM onboarding_tache t JOIN contrat c ON c.id=t.id_contrat WHERE t.id=?",id);
        personnel.verrouillerEmploye(texte(ref,"id_employe"));
        return personnel.ligne("""
            SELECT t.*,c.id_employe,c.date_creation,c.date_debut FROM onboarding_tache t
            JOIN contrat c ON c.id=t.id_contrat WHERE t.id=? FOR UPDATE OF t
            """,id);
    }
    private void responsable(Acteur a,Map<String,Object> t) {
        if(!a.employe().equals(t.get("id_responsable"))) PersonnelAccess.refus();
    }
    @Transactional
    public void changerStatut(Acteur a,int id,String statut) {
        var t=verrouiller(id); responsable(a,t);
        if(!STATUTS.contains(statut) || "TERMINEE".equals(statut)) throw new BusinessException("Utilisez le formulaire de réalisation pour terminer une tâche.");
        String ancien=texte(t,"statut"); if(ancien.equals(statut)) return;
        boolean permis=switch(ancien) {
            case "A_FAIRE" -> List.of("EN_COURS","BLOQUEE").contains(statut);
            case "EN_COURS" -> "BLOQUEE".equals(statut);
            case "BLOQUEE", "TERMINEE" -> List.of("EN_COURS","A_FAIRE").contains(statut);
            default -> false;
        };
        if(!permis) throw conflit("Cette transition de tâche n'est pas autorisée.");
        String avant=audit.snapshot("onboarding_tache",id);
        db.update("UPDATE onboarding_tache SET statut=?,date_completion=NULL WHERE id=?",statut,id);
        audit.tracer(a,texte(t,"id_employe"),"onboarding_tache",id,avant);
    }
    public boolean peutRealiser(Acteur a,Map<String,Object> t) {
        return switch(texte(t,"etape")) {
            case "SIGNATURE_CONTRAT" -> a.signature();
            case "INSCRIPTION_PAIE" -> a.paie();
            default -> true;
        };
    }
    public String tablePreuve(String etape) {
        return switch(etape) {
            case "SIGNATURE_CONTRAT" -> "contrat";
            case "BADGE" -> "badge_employe";
            case "COMPTE" -> "compte_employe";
            case "MATERIEL" -> "materiel_employe";
            case "FORMATION" -> "formation_employe";
            case "INSCRIPTION_PAIE" -> "inscription_paie";
            default -> throw new BusinessException("Étape inconnue.");
        };
    }
    /** Choix limités aux résultats de cette étape et de ce salarié, sans salaire. */
    public List<Map<String,Object>> preuves(Acteur a,int tache) {
        var t=tache(a,tache); String etape=texte(t,"etape");
        if(!peutRealiser(a,t)) return List.of();
        if("SIGNATURE_CONTRAT".equals(etape)) return db.queryForList("SELECT id,date_signature,statut FROM contrat WHERE id=?",t.get("id_contrat"));
        return switch(etape) {
            case "BADGE" -> db.queryForList("SELECT id,numero_badge AS libelle,date_remise AS date,date_restitution FROM badge_employe WHERE id_employe=? ORDER BY id DESC",t.get("id_employe"));
            case "COMPTE" -> db.queryForList("SELECT id,systeme||' / '||identifiant AS libelle,date_activation AS date FROM compte_employe WHERE id_employe=? ORDER BY id DESC",t.get("id_employe"));
            case "MATERIEL" -> db.queryForList("SELECT a.id,m.nom||' (#'||m.id||')' AS libelle,date_reception AS date,date_restitution FROM materiel_employe a JOIN materiel m ON m.id=a.id_materiel WHERE id_employe=? ORDER BY a.id DESC",t.get("id_employe"));
            case "FORMATION" -> db.queryForList("SELECT a.id,f.nom AS libelle,date_completion AS date FROM formation_employe a JOIN formation f ON f.id=a.id_formation WHERE id_employe=? ORDER BY a.id DESC",t.get("id_employe"));
            default -> db.queryForList("SELECT id,'Inscription confirmée, prise en charge '||date_debut_prise_en_charge AS libelle,date_inscription AS date FROM inscription_paie WHERE id_employe=? ORDER BY id DESC",t.get("id_employe"));
        };
    }
    @Transactional
    public void terminer(Acteur a,int id,RealisationForm f) {
        var t=verrouiller(id); responsable(a,t);
        if(!peutRealiser(a,t)) PersonnelAccess.refus();
        if("TERMINEE".equals(t.get("statut"))) return; // rejeu : aucun nouveau fait ni trace
        if(f==null || !f.confirme()) throw new BusinessException("Confirmez que le résultat sélectionné correspond exactement à la tâche.");
        String table=tablePreuve(texte(t,"etape"));
        int preuve;
        if("contrat".equals(table)) {
            int contrat=nombre(t,"id_contrat");
            var c=personnel.contratInterne(contrat);
            if(!List.of("SIGNE","TERMINE").contains(c.get("statut")) || c.get("date_signature")==null) {
                personnel.signer(a,contrat,f.date());
            } else if(f.date()!=null && !f.date().equals(date(c,"date_signature"))) {
                throw new BusinessException("La date fournie diffère de la signature déjà enregistrée.");
            }
            c=personnel.contratInterne(contrat);
            if(date(c,"date_signature").isAfter(personnel.aujourdHui())
                    || (c.get("date_fin")!=null && date(c,"date_signature").isAfter(date(c,"date_fin")))) {
                throw new BusinessException("La signature enregistrée est incohérente avec la date actuelle ou la fin du contrat.");
            }
            preuve=contrat;
        } else if(f.existant()!=null) {
            preuve=verifierPreuveExistante(a,t,table,f);
        } else {
            preuve=creerPreuve(a,t,table,f);
        }
        String avant=audit.snapshot("onboarding_tache",id);
        db.update("UPDATE onboarding_tache SET statut='TERMINEE',date_completion=CURRENT_TIMESTAMP WHERE id=?",id);
        audit.tracer(a,texte(t,"id_employe"),"onboarding_tache",id,avant,table,preuve);
    }
    private int verifierPreuveExistante(Acteur a,Map<String,Object> t,String table,RealisationForm f) {
        // table issue du switch fermé ci-dessus ; aucune table ne vient du navigateur.
        var r=personnel.ligne("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",f.existant());
        if(!t.get("id_employe").equals(r.get("id_employe"))) throw new BusinessException("Ce résultat appartient à un autre salarié.");
        String dateCol=switch(table) {
            case "badge_employe" -> "date_remise";
            case "compte_employe" -> "date_activation";
            case "materiel_employe" -> "date_reception";
            case "formation_employe" -> "date_completion";
            default -> "date_inscription";
        };
        if("formation_employe".equals(table) && r.get("date_completion")==null) {
            verifierDate(t,f.date());
            String avant=audit.snapshot(table,f.existant());
            db.update("UPDATE formation_employe SET date_completion=? WHERE id=?",f.date(),f.existant());
            audit.tracer(a,texte(t,"id_employe"),table,f.existant(),avant);
            r.put("date_completion",f.date());
        }
        verifierDate(t,date(r,dateCol));
        if(r.containsKey("date_restitution") && r.get("date_restitution")!=null) throw new BusinessException("Cette attribution a été restituée. Choisissez la réalisation de ce parcours.");
        if("inscription_paie".equals(table) && date(r,"date_debut_prise_en_charge").isBefore(date(t,"date_debut"))) throw new BusinessException("Cette prise en charge précède l'entrée du parcours. Une réembauche doit être confirmée explicitement par la paie.");
        // Une même preuve ne valide pas deux tâches distinctes (ex. deux matériels).
        var usages=db.queryForList("""
            SELECT id FROM mouvement_rh WHERE table_concernee='onboarding_tache' AND id_ligne<>?
            AND valeurs_apres->'_preuve'->>'table'=? AND valeurs_apres->'_preuve'->>'id'=?
            """,t.get("id").toString(),table,f.existant().toString());
        if(!usages.isEmpty()) throw conflit("Cette réalisation a déjà servi de preuve à une autre tâche. Sélectionnez le résultat précis attendu.");
        return f.existant();
    }
    private int creerPreuve(Acteur a,Map<String,Object> t,String table,RealisationForm f) {
        String employe=texte(t,"id_employe"); verifierDate(t,f.date());
        int id;
        switch(table) {
            case "badge_employe" -> {
                texteRequis(f.numero(),50,"Numéro de badge");
                // Verrou transactionnel de la clé naturelle, même si aucune ligne n'existe encore.
                db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "badge:"+f.numero().trim());
                if(!db.queryForList("SELECT id FROM badge_employe WHERE numero_badge=? AND (date_restitution IS NULL OR date_restitution>=?)",f.numero().trim(),f.date()).isEmpty()) throw conflit("Ce badge est déjà attribué sur cette période.");
                id=db.queryForObject("INSERT INTO badge_employe(id_employe,numero_badge,date_remise) VALUES (?,?,?) RETURNING id",Integer.class,employe,f.numero().trim(),f.date());
            }
            case "compte_employe" -> {
                texteRequis(f.systeme(),100,"Système"); texteRequis(f.identifiant(),150,"Identifiant");
                id=db.queryForObject("INSERT INTO compte_employe(id_employe,systeme,identifiant,date_activation) VALUES (?,?,?,?) RETURNING id",Integer.class,employe,f.systeme().trim(),f.identifiant().trim(),f.date());
            }
            case "materiel_employe" -> {
                if(f.catalogue()==null) throw new BusinessException("Choisissez l'objet physique à remettre.");
                personnel.ligne("SELECT id FROM materiel WHERE id=? FOR UPDATE",f.catalogue());
                if(!db.queryForList("SELECT id FROM materiel_employe WHERE id_materiel=? AND (date_restitution IS NULL OR date_restitution>=?)",f.catalogue(),f.date()).isEmpty()) throw conflit("Ce matériel est déjà attribué sur cette période.");
                id=db.queryForObject("INSERT INTO materiel_employe(id_employe,id_materiel,date_reception) VALUES (?,?,?) RETURNING id",Integer.class,employe,f.catalogue(),f.date());
            }
            case "formation_employe" -> {
                if(f.catalogue()==null) throw new BusinessException("Choisissez la formation réellement terminée.");
                personnel.ligne("SELECT id FROM formation WHERE id=?",f.catalogue());
                id=db.queryForObject("INSERT INTO formation_employe(id_employe,id_formation,date_completion) VALUES (?,?,?) RETURNING id",Integer.class,employe,f.catalogue(),f.date());
            }
            case "inscription_paie" -> {
                if(f.priseEnCharge()==null || f.priseEnCharge().isBefore(date(t,"date_debut"))) throw new BusinessException("La prise en charge doit commencer à l'entrée de ce parcours ou après.");
                // Un dossier déjà confirmé est réutilisé explicitement ; jamais dupliqué au renouvellement.
                if(!db.queryForList("SELECT id FROM inscription_paie WHERE id_employe=? AND date_debut_prise_en_charge>=?",employe,date(t,"date_debut")).isEmpty()) throw conflit("Une inscription est déjà confirmée pour ce parcours. Sélectionnez-la comme preuve existante.");
                id=db.queryForObject("INSERT INTO inscription_paie(id_employe,date_inscription,date_debut_prise_en_charge) VALUES (?,?,?) RETURNING id",Integer.class,employe,f.date(),f.priseEnCharge());
            }
            default -> throw new BusinessException("Réalisation inconnue.");
        }
        audit.tracer(a,employe,table,id,null); return id;
    }
    private void verifierDate(Map<String,Object> t,LocalDate d) {
        if(d==null || d.isAfter(personnel.aujourdHui())) throw new BusinessException("Une date de réalisation effective, non future, est obligatoire.");
        if(d.isBefore(date(t,"date_creation"))) throw new BusinessException("La réalisation précède la préparation du contrat d'entrée de ce parcours.");
        var prochain=db.queryForList("SELECT date_creation FROM contrat WHERE id_employe=? AND id_contrat_precedent IS NULL AND date_debut>? ORDER BY date_debut LIMIT 1",t.get("id_employe"),date(t,"date_debut"));
        if(!prochain.isEmpty() && !d.isBefore(date(prochain.get(0),"date_creation"))) throw new BusinessException("Cette réalisation concerne une période de réembauche ultérieure.");
    }
}
