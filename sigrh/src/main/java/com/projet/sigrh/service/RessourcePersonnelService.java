package com.projet.sigrh.service;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.service.PersonnelAccess.Acteur;
import static com.projet.sigrh.service.PersonnelService.*;

@Service
@Transactional(readOnly = true)
public class RessourcePersonnelService {
    private final JdbcTemplate db;
    private final PersonnelService personnel;
    private final MouvementRhService audit;
    public RessourcePersonnelService(JdbcTemplate db,PersonnelService personnel,MouvementRhService audit) {
        this.db=db; this.personnel=personnel; this.audit=audit;
    }
    public List<Map<String,Object>> catalogue(String type) {
        verifierCatalogue(type);
        return db.queryForList("SELECT id,nom FROM "+type+" ORDER BY nom,id");
    }
    @Transactional
    public void enregistrerCatalogue(Acteur a,String type,Integer id,String nom) {
        a.exigerRh(); verifierCatalogue(type); texteRequis(nom,150,"Nom");
        String avant=null;
        if(id==null) id=db.queryForObject("INSERT INTO "+type+"(nom) VALUES (?) RETURNING id",Integer.class,nom.trim());
        else {
            personnel.ligne("SELECT id FROM "+type+" WHERE id=? FOR UPDATE",id);
            avant=audit.snapshot(type,id); db.update("UPDATE "+type+" SET nom=? WHERE id=?",nom.trim(),id);
        }
        audit.tracer(a,null,type,id,avant);
    }
    private void verifierCatalogue(String type) {
        if(!List.of("materiel","formation").contains(type)) throw new BusinessException("Catalogue inconnu.");
    }
    public Map<String,List<Map<String,Object>>> ressources(Acteur a,String employe) {
        a.lire(employe);
        Map<String,List<Map<String,Object>>> r=new LinkedHashMap<>();
        r.put("Comptes activés",db.queryForList("SELECT id,systeme||' / '||identifiant AS libelle,date_activation AS date FROM compte_employe WHERE id_employe=? ORDER BY date_activation DESC",employe));
        var badges=db.queryForList("SELECT id,numero_badge AS libelle,date_remise AS date,date_restitution FROM badge_employe WHERE id_employe=? ORDER BY date_remise DESC",employe);
        badges.forEach(b->b.put("type","badge")); r.put("Badges",badges);
        var materiels=db.queryForList("SELECT a.id,m.nom||' (#'||m.id||')' AS libelle,date_reception AS date,date_restitution FROM materiel_employe a JOIN materiel m ON m.id=a.id_materiel WHERE id_employe=? ORDER BY date_reception DESC",employe);
        materiels.forEach(m->m.put("type","materiel")); r.put("Matériels",materiels);
        r.put("Formations (date vide : non terminée)",db.queryForList("SELECT a.id,f.nom AS libelle,date_completion AS date FROM formation_employe a JOIN formation f ON f.id=a.id_formation WHERE id_employe=? ORDER BY a.id DESC",employe));
        r.put("Inscriptions paie confirmées",db.queryForList("SELECT id,'Prise en charge dès le '||date_debut_prise_en_charge AS libelle,date_inscription AS date FROM inscription_paie WHERE id_employe=? ORDER BY date_inscription DESC",employe));
        return r;
    }
    @Transactional
    public void restituer(Acteur a,String employe,String type,int id,LocalDate date) {
        a.exigerRh(); personnel.verrouillerEmploye(employe);
        String table=switch(type) {
            case "badge" -> "badge_employe";
            case "materiel" -> "materiel_employe";
            default -> throw new BusinessException("Attribution inconnue.");
        };
        var r=personnel.ligne("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id);
        if(!employe.equals(r.get("id_employe"))) throw new BusinessException("L'attribution appartient à un autre salarié.");
        if(date==null || date.isAfter(personnel.aujourdHui()) || date.isBefore(date(r,"badge".equals(type)?"date_remise":"date_reception"))) throw new BusinessException("La restitution doit suivre la remise et ne peut pas être future.");
        if(r.get("date_restitution")!=null) {
            if(date.equals(date(r,"date_restitution"))) return;
            throw conflit("La restitution est déjà enregistrée.");
        }
        String avant=audit.snapshot(table,id);
        db.update("UPDATE "+table+" SET date_restitution=? WHERE id=?",date,id);
        audit.tracer(a,employe,table,id,avant);
        // L'onboarding exprime une réalisation passée et reste terminé.
    }
}
