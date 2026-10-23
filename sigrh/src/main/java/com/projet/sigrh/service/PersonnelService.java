package com.projet.sigrh.service;

import java.time.Clock;
import java.time.LocalDate;
import java.sql.Date;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projet.sigrh.dto.personnel.ContratForm;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.service.PersonnelAccess.Acteur;

@Service
@Transactional(readOnly = true)
public class PersonnelService {
    private final JdbcTemplate db;
    private final MouvementRhService audit;
    private final Clock clock;
    public PersonnelService(JdbcTemplate db, MouvementRhService audit, Clock clock) {
        this.db=db; this.audit=audit; this.clock=clock;
    }
    public LocalDate aujourdHui() { return LocalDate.now(clock); }

    public List<Map<String,Object>> employes(Acteur acteur) {
        return db.queryForList("SELECT id,nom,prenom FROM employe WHERE (? OR id=?) ORDER BY nom,prenom", acteur.rh(),acteur.employe());
    }
    public Map<String,Object> employe(Acteur acteur, String id) {
        acteur.lire(id);
        return ligne("SELECT * FROM employe WHERE id=?", id);
    }
    public List<Map<String,Object>> postes() {
        return db.queryForList("SELECT p.*,d.libelle AS departement FROM poste p JOIN departement d ON d.id=p.id_departement ORDER BY d.libelle,p.libelle");
    }
    public List<Map<String,Object>> contrats(Acteur acteur, String employe) {
        acteur.lire(employe);
        var contrats = db.queryForList("SELECT c.*,p.libelle AS poste FROM contrat c JOIN poste p ON p.id=c.id_poste WHERE id_employe=? ORDER BY date_debut DESC,c.id DESC", employe);
        long actuels=0;
        for (var c: contrats) {
            c.put("situation", situation(c,aujourdHui()));
            if ("En vigueur".equals(c.get("situation"))) actuels++;
        }
        if (actuels>1) for(var c:contrats) if("En vigueur".equals(c.get("situation"))) c.put("situation","Conflit : plusieurs contrats en vigueur");
        return contrats;
    }
    public static String situation(Map<String,Object> c, LocalDate jour) {
        if ("BROUILLON".equals(c.get("statut"))) return "Brouillon / préparé";
        if ("ANNULE".equals(c.get("statut"))) return "Annulé";
        if ("SIGNE".equals(c.get("statut")) && c.get("date_signature")==null) return "Incohérence : signature sans date";
        if ("SIGNE".equals(c.get("statut")) && date(c,"date_debut").isAfter(jour)) return "Futur signé";
        if ("SIGNE".equals(c.get("statut")) && !date(c,"date_debut").isAfter(jour)
                && (c.get("date_fin")==null || !date(c,"date_fin").isBefore(jour))) return "En vigueur";
        return "Historique";
    }
    public Map<String,Object> contrat(Acteur acteur, int id) {
        var c=contratInterne(id); acteur.lire(texte(c,"id_employe")); return c;
    }
    Map<String,Object> contratInterne(int id) { return ligne("SELECT * FROM contrat WHERE id=?",id); }

    /** Verrou stable commun : employé -> contrat -> tâche -> ressource. */
    void verrouillerEmploye(String id) { ligne("SELECT id FROM employe WHERE id=? FOR UPDATE",id); }
    public int entree(int contrat) {
        Set<Integer> vus=new HashSet<>();
        var c=contratInterne(contrat); String employe=texte(c,"id_employe");
        while (true) {
            int id=nombre(c,"id");
            if (!vus.add(id)) throw conflit("La chaîne des contrats contient un cycle.");
            if(!employe.equals(texte(c,"id_employe"))) throw conflit("Le contrat précédent appartient à un autre employé.");
            if(c.get("id_contrat_precedent")==null) return id;
            c=contratInterne(nombre(c,"id_contrat_precedent"));
        }
    }

    @Transactional
    public int enregistrerContrat(Acteur acteur,String employe,ContratForm f) {
        acteur.exigerRh(); verrouillerEmploye(employe);
        if(f==null || f.poste()==null || f.debut()==null || f.salaire()==null) throw new BusinessException("Poste, date de début et salaire sont obligatoires.");
        if(f.salaire().signum()<0 || f.salaire().scale()>2 || f.salaire().precision()-f.salaire().scale()>12) throw new BusinessException("Le salaire doit être positif ou nul, avec au plus deux décimales (MGA).");
        texteRequis(f.type(),30,"Type de contrat"); optionnel(f.horaires(),150); optionnel(f.lieu(),150); optionnel(f.conditions(),20000);
        if(f.fin()!=null && f.fin().isBefore(f.debut())) throw new BusinessException("La fin du contrat doit être postérieure ou égale au début.");
        if("CDD".equalsIgnoreCase(f.type().trim()) && f.fin()==null) throw new BusinessException("La date de fin du CDD est obligatoire.");
        ligne("SELECT id FROM poste WHERE id=?",f.poste());
        String avant=null;
        if(f.id()!=null) {
            var c=ligne("SELECT * FROM contrat WHERE id=? FOR UPDATE",f.id());
            if(!employe.equals(c.get("id_employe"))) throw new BusinessException("Ce contrat appartient à un autre employé.");
            if(!"BROUILLON".equals(c.get("statut"))) throw conflit("Seul un brouillon peut être modifié.");
            if(!Objects.equals(f.precedent(),c.get("id_contrat_precedent"))) throw new BusinessException("Le précédent d'un brouillon existant ne peut pas être changé.");
            if(c.get("id_proposition")!=null) {
                var p=ligne("SELECT statut FROM proposition_embauche WHERE id=?",c.get("id_proposition"));
                if(!"ACCEPTEE".equals(p.get("statut"))) throw conflit("La proposition d'origine doit être acceptée.");
            }
            avant=audit.snapshot("contrat",f.id());
        }
        verifierPrecedent(employe,f.id(),f.precedent(),f.debut());
        verifierPeriodes(employe,f.id(),f.debut(),f.fin());
        int id;
        if(f.id()==null) {
            id=db.queryForObject("""
                INSERT INTO contrat(id_employe,id_contrat_precedent,id_poste,type_contrat,date_debut,date_fin,salaire_mensuel,horaires,lieu_travail,conditions,date_creation)
                VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """,Integer.class,employe,f.precedent(),f.poste(),f.type().trim(),f.debut(),f.fin(),f.salaire(),f.horaires(),f.lieu(),f.conditions(),aujourdHui());
        } else {
            id=f.id();
            db.update("UPDATE contrat SET id_poste=?,type_contrat=?,date_debut=?,date_fin=?,salaire_mensuel=?,horaires=?,lieu_travail=?,conditions=? WHERE id=?",
                    f.poste(),f.type().trim(),f.debut(),f.fin(),f.salaire(),f.horaires(),f.lieu(),f.conditions(),id);
        }
        audit.tracer(acteur,employe,"contrat",id,avant); return id;
    }
    private void verifierPrecedent(String employe,Integer id,Integer precedent,LocalDate debut) {
        if(precedent==null) return;
        if(precedent.equals(id)) throw new BusinessException("Un contrat ne peut pas être son propre précédent.");
        entree(precedent);
        var p=contratInterne(precedent);
        if(!employe.equals(p.get("id_employe"))) throw new BusinessException("Le précédent doit appartenir au même employé.");
        if(!List.of("SIGNE","TERMINE").contains(p.get("statut")) || p.get("date_signature")==null) throw conflit("Le précédent doit avoir été signé.");
        if(p.get("date_fin")==null || !date(p,"date_fin").isBefore(debut)) throw conflit("Le renouvellement doit commencer après la fin naturelle du précédent. Un avenant ou une rupture anticipée n'est pas couvert ici.");
        if(!db.queryForList("SELECT id FROM contrat WHERE id_contrat_precedent=? AND statut<>'ANNULE' AND (CAST(? AS integer) IS NULL OR id<>?)",precedent,id,id).isEmpty())
            throw conflit("Un contrat successeur existe déjà.");
    }
    private void verifierPeriodes(String employe,Integer id,LocalDate debut,LocalDate fin) {
        if(!db.queryForList("""
                SELECT id FROM contrat WHERE id_employe=? AND statut<>'ANNULE' AND (CAST(? AS integer) IS NULL OR id<>?)
                AND date_debut<=COALESCE(CAST(? AS date),'infinity'::date) AND COALESCE(date_fin,'infinity'::date)>=?
                """,employe,id,id,fin,debut).isEmpty()) throw conflit("La période chevauche un autre contrat, y compris un brouillon préparé.");
    }
    @Transactional
    public void signer(Acteur acteur,int id,LocalDate signature) {
        if(!acteur.signature()) PersonnelAccess.refus();
        var initial=contratInterne(id); String employe=texte(initial,"id_employe");
        verrouillerEmploye(employe);
        var c=ligne("SELECT * FROM contrat WHERE id=? FOR UPDATE",id);
        if(signature==null || signature.isAfter(aujourdHui())) throw new BusinessException("La date de signature réelle est obligatoire et ne peut pas être future.");
        if("SIGNE".equals(c.get("statut")) && signature.equals(date(c,"date_signature"))) return;
        if(!"BROUILLON".equals(c.get("statut"))) throw conflit("Seul un contrat brouillon peut être signé.");
        if(c.get("salaire_mensuel")==null || ((java.math.BigDecimal)c.get("salaire_mensuel")).signum()<0) throw new BusinessException("Salaire invalide.");
        if("CDD".equalsIgnoreCase(texte(c,"type_contrat").trim()) && c.get("date_fin")==null) throw new BusinessException("La date de fin du CDD est obligatoire avant signature.");
        if(c.get("date_fin")!=null && signature.isAfter(date(c,"date_fin"))) throw new BusinessException("La signature est postérieure à la fin du contrat.");
        if(c.get("id_proposition")!=null && !"ACCEPTEE".equals(ligne("SELECT statut FROM proposition_embauche WHERE id=?",c.get("id_proposition")).get("statut"))) throw conflit("La proposition doit être acceptée.");
        verifierPrecedent(employe,id,(Integer)c.get("id_contrat_precedent"),date(c,"date_debut"));
        verifierPeriodes(employe,id,date(c,"date_debut"),date(c,"date_fin"));
        String avant=audit.snapshot("contrat",id);
        db.update("UPDATE contrat SET date_signature=?,statut='SIGNE' WHERE id=?",signature,id);
        audit.tracer(acteur,employe,"contrat",id,avant);
        if(c.get("id_contrat_precedent")!=null) {
            var p=contratInterne(nombre(c,"id_contrat_precedent"));
            if("SIGNE".equals(p.get("statut")) && date(p,"date_fin").isBefore(aujourdHui())) {
                String a=audit.snapshot("contrat",p.get("id"));
                db.update("UPDATE contrat SET statut='TERMINE' WHERE id=?",p.get("id"));
                audit.tracer(acteur,employe,"contrat",p.get("id"),a);
            }
        }
    }
    public List<Map<String,Object>> affectations(Acteur acteur,String employe) {
        acteur.lire(employe);
        var rows=db.queryForList("""
            SELECT a.*,p.libelle AS poste,d.libelle AS departement FROM affectation a
            JOIN poste p ON p.id=a.id_poste JOIN departement d ON d.id=p.id_departement
            WHERE a.id_employe=? ORDER BY date_debut DESC,a.id DESC
            """,employe);
        for(var a:rows) a.put("situation",date(a,"date_debut").isAfter(aujourdHui())?"Future":
                a.get("date_fin")==null || !date(a,"date_fin").isBefore(aujourdHui())?"Actuelle":"Historique");
        long actuelles=rows.stream().filter(a->"Actuelle".equals(a.get("situation"))).count();
        if(actuelles>1) rows.stream().filter(a->"Actuelle".equals(a.get("situation"))).forEach(a->a.put("situation","Conflit : plusieurs affectations actuelles"));
        return rows;
    }
    @Transactional
    public void changerAffectation(Acteur acteur,String employe,int poste,Integer contrat,String lieu,String motif) {
        acteur.exigerRh(); verrouillerEmploye(employe); LocalDate jour=aujourdHui();
        optionnel(lieu,150); texteRequis(motif,2000,"Motif");
        var cible=ligne("SELECT * FROM poste WHERE id=?",poste);
        if(contrat!=null && !employe.equals(contratInterne(contrat).get("id_employe"))) throw new BusinessException("Le contrat d'origine doit appartenir au salarié.");
        var concernees=db.queryForList("SELECT * FROM affectation WHERE id_employe=? AND (date_fin IS NULL OR date_fin>=?) ORDER BY id FOR UPDATE",employe,jour);
        if(concernees.size()>1 || (!concernees.isEmpty() && date(concernees.get(0),"date_debut").isAfter(jour))) throw conflit("Une affectation future ou des périodes en conflit empêchent ce changement immédiat.");
        if(!concernees.isEmpty()) {
            var a=concernees.get(0);
            if(!date(a,"date_debut").isBefore(jour)) throw conflit("Une affectation commence déjà aujourd'hui. Sa correction exige une opération explicite ; aucune période négative n'a été créée.");
            String avant=audit.snapshot("affectation",a.get("id"));
            db.update("UPDATE affectation SET date_fin=? WHERE id=?",jour.minusDays(1),a.get("id"));
            audit.tracer(acteur,employe,"affectation",a.get("id"),avant);
        }
        int id=db.queryForObject("INSERT INTO affectation(id_employe,id_poste,id_contrat,lieu,date_debut,motif) VALUES (?,?,?,?,?,?) RETURNING id",Integer.class,employe,poste,contrat,lieu,jour,motif.trim());
        audit.tracer(acteur,employe,"affectation",id,null);
        String avant=audit.snapshot("employe",employe);
        db.update("UPDATE employe SET id_departement=? WHERE id=?",cible.get("id_departement"),employe);
        audit.tracer(acteur,employe,"employe",employe,avant);
    }

    Map<String,Object> ligne(String sql,Object... args) {
        var rows=db.queryForList(sql,args);
        if(rows.isEmpty()) throw new BusinessException("L'élément demandé est introuvable.",HttpStatus.NOT_FOUND);
        return rows.get(0);
    }
    public static String texte(Map<String,Object> r,String k) { return Objects.toString(r.get(k),""); }
    public static int nombre(Map<String,Object> r,String k) { return ((Number)r.get(k)).intValue(); }
    public static LocalDate date(Map<String,Object> r,String k) {
        Object v=r.get(k); return v==null?null:v instanceof Date d?d.toLocalDate(): (LocalDate)v;
    }
    static void texteRequis(String s,int max,String nom) {
        if(s==null || s.isBlank() || s.length()>max) throw new BusinessException(nom+" est obligatoire ("+max+" caractères maximum).");
    }
    static void optionnel(String s,int max) { if(s!=null && s.length()>max) throw new BusinessException("Le texte dépasse "+max+" caractères."); }
    static BusinessException conflit(String s) { return new BusinessException(s,HttpStatus.CONFLICT); }
}
