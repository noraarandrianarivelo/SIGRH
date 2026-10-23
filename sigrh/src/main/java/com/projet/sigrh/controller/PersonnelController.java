package com.projet.sigrh.controller;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.projet.sigrh.dto.personnel.*;
import com.projet.sigrh.service.*;
import static com.projet.sigrh.service.PersonnelService.*;

@Controller
@RequestMapping("/personnel")
public class PersonnelController {
    private final PersonnelAccess access;
    private final PersonnelService personnel;
    private final OnboardingService onboarding;
    private final RessourcePersonnelService ressources;
    private final DocumentContratService documents;
    private final MouvementRhService audit;
    public PersonnelController(PersonnelAccess access,PersonnelService personnel,OnboardingService onboarding,
            RessourcePersonnelService ressources,DocumentContratService documents,MouvementRhService audit) {
        this.access=access; this.personnel=personnel; this.onboarding=onboarding;
        this.ressources=ressources; this.documents=documents; this.audit=audit;
    }
    @ModelAttribute
    void contexte(Principal p,Model m) {
        var a=access.acteur(p); m.addAttribute("acteur",a); m.addAttribute("aujourdhui",personnel.aujourdHui());
    }
    @GetMapping
    String employes(Principal p,Model m) {
        m.addAttribute("employes",personnel.employes(access.acteur(p))); return "personnel/employes";
    }
    @GetMapping("/employes/{id}")
    String fiche(@PathVariable String id,Principal p,Model m) {
        var a=access.acteur(p);
        m.addAttribute("employe",personnel.employe(a,id));
        var contrats=personnel.contrats(a,id); m.addAttribute("contrats",contrats);
        Map<Integer,Object> docs=new HashMap<>(); for(var c:contrats) docs.put(nombre(c,"id"),documents.documents(a,nombre(c,"id")));
        m.addAttribute("documents",docs);
        m.addAttribute("affectations",personnel.affectations(a,id));
        m.addAttribute("suivis",onboarding.suivi(a,id)); m.addAttribute("etapes",onboarding.etapes(a,id));
        m.addAttribute("taches",onboarding.tachesEmploye(a,id));
        m.addAttribute("ressources",ressources.ressources(a,id));
        m.addAttribute("categories",OnboardingService.ETAPES);
        if(a.rh()) { m.addAttribute("postes",personnel.postes()); m.addAttribute("responsables",onboarding.responsables(a)); }
        return "personnel/fiche";
    }
    @GetMapping("/employes/{id}/contrat")
    String contratForm(@PathVariable String id,@RequestParam(required=false) Integer modifier,
            @RequestParam(required=false) Integer precedent,Principal p,Model m) {
        var a=access.acteur(p); a.exigerRh(); m.addAttribute("employe",personnel.employe(a,id));
        ContratForm f=new ContratForm(null,null,null,"CDI",personnel.aujourdHui(),null,null,"","","");
        if(modifier!=null || precedent!=null) {
            var c=personnel.contrat(a,modifier!=null?modifier:precedent);
            if(!id.equals(c.get("id_employe"))) throw new com.projet.sigrh.exception.BusinessException("Le contrat n'appartient pas à ce salarié.");
            if(modifier!=null && !"BROUILLON".equals(c.get("statut"))) throw new com.projet.sigrh.exception.BusinessException("Seul un brouillon peut être modifié.");
            f=new ContratForm(modifier,modifier!=null?(Integer)c.get("id_contrat_precedent"):precedent,nombre(c,"id_poste"),texte(c,"type_contrat"),
                    modifier!=null?date(c,"date_debut"):c.get("date_fin")!=null?date(c,"date_fin").plusDays(1):personnel.aujourdHui(),
                    modifier!=null?date(c,"date_fin"):null,(java.math.BigDecimal)c.get("salaire_mensuel"),texte(c,"horaires"),texte(c,"lieu_travail"),texte(c,"conditions"));
        }
        m.addAttribute("form",f); m.addAttribute("postes",personnel.postes()); return "personnel/contrat-form";
    }
    @PostMapping("/employes/{employe}/contrat")
    String contrat(@PathVariable String employe,@ModelAttribute ContratForm form,Principal p,RedirectAttributes flash) {
        personnel.enregistrerContrat(access.acteur(p),employe,form); return retour(employe,flash,"Le brouillon du contrat a été enregistré.");
    }
    @PostMapping("/contrats/{id}/signature")
    String signature(@PathVariable int id,@RequestParam LocalDate date, @RequestParam(required=false) MultipartFile fichier,Principal p,RedirectAttributes flash) {
        var a=access.acteur(p); a.exigerRh();
        documents.signerAvecDocument(a,id,date,fichier);
        int document=documents.dernierDocument(a,id);
        return "redirect:/personnel/documents/auto/"+id;
    }
    @PostMapping("/contrats/{id}/documents")
    String ajouterDocument(@PathVariable int id,@RequestParam String type,@RequestParam MultipartFile fichier,Principal p,RedirectAttributes flash) {
        var a=access.acteur(p); a.exigerRh(); documents.ajouter(a,id,type,fichier);
        return retour(texte(personnel.contrat(a,id),"id_employe"),flash,"Le document a été ajouté sans remplacer les pièces précédentes.");
    }
    @GetMapping("/documents/{id}")
    ResponseEntity<Resource> telecharger(@PathVariable int id,Principal p) throws IOException {
        var fichier=documents.telecharger(access.acteur(p),id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(java.nio.file.Files.size(fichier))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("contrat-document-"+id+".pdf").build().toString())
                .header("X-Content-Type-Options","nosniff").body(new FileSystemResource(fichier));
    }
    @GetMapping("/documents/auto/{id}")
    String telechargerPuisRetour(@PathVariable int id,Principal p,Model model) {
        var a=access.acteur(p); var contrat=personnel.contrat(a,id);
        model.addAttribute("documentId",documents.dernierDocument(a,id));
        model.addAttribute("retour","/personnel/employes/"+texte(contrat,"id_employe"));
        return "personnel/telechargement-contrat";
    }
    @PostMapping("/employes/{id}/affectation")
    String affectation(@PathVariable String id,@RequestParam int poste,@RequestParam(required=false) Integer contrat,
            @RequestParam String lieu,@RequestParam String motif,Principal p,RedirectAttributes flash) {
        personnel.changerAffectation(access.acteur(p),id,poste,contrat,lieu,motif); return retour(id,flash,"L'affectation a été changée et le département actuel synchronisé.");
    }
    @PostMapping("/employes/{id}/taches")
    String creerTache(@PathVariable String id,@RequestParam int contrat,@RequestParam String etape,
            @RequestParam String libelle,@RequestParam String responsable,Principal p,RedirectAttributes flash) {
        var a=access.acteur(p);
        if(!id.equals(personnel.contrat(a,contrat).get("id_employe"))) throw new com.projet.sigrh.exception.BusinessException("Parcours d'un autre salarié.");
        onboarding.creer(a,contrat,etape,libelle,responsable); return retour(id,flash,"La tâche a été attribuée.");
    }
    @PostMapping("/employes/{id}/taches/{tache}/attribution")
    String reassigner(@PathVariable String id,@PathVariable int tache,@RequestParam String responsable,
            @RequestParam String libelle,Principal p,RedirectAttributes flash) {
        // Le service vérifie l'objet ; l'URL de retour n'est pas une autorisation.
        onboarding.reassigner(access.acteur(p),tache,responsable,libelle); return retour(id,flash,"L'attribution et le libellé ont été enregistrés.");
    }
    @GetMapping("/taches")
    String mesTaches(@RequestParam(defaultValue="") String statut,Principal p,Model m) {
        m.addAttribute("taches",onboarding.mesTaches(access.acteur(p),statut)); m.addAttribute("statut",statut);
        m.addAttribute("statuts",OnboardingService.STATUTS); return "personnel/taches";
    }
    @GetMapping("/taches/{id}")
    String tache(@PathVariable int id,Principal p,Model m) {
        var a=access.acteur(p); var t=onboarding.tache(a,id);
        m.addAttribute("tache",t); m.addAttribute("preuves",onboarding.preuves(a,id));
        m.addAttribute("suivi",onboarding.suiviTache(a,id));
        m.addAttribute("peutRealiser",onboarding.peutRealiser(a,t));
        if("MATERIEL".equals(t.get("etape"))) m.addAttribute("catalogue",ressources.catalogue("materiel"));
        if("FORMATION".equals(t.get("etape"))) m.addAttribute("catalogue",ressources.catalogue("formation"));
        if("SIGNATURE_CONTRAT".equals(t.get("etape")) && a.signature()) m.addAttribute("documents",documents.documents(a,nombre(t,"id_contrat")));
        return "personnel/tache";
    }
    @PostMapping("/taches/{id}/statut")
    String statut(@PathVariable int id,@RequestParam String statut,Principal p,RedirectAttributes flash) {
        onboarding.changerStatut(access.acteur(p),id,statut); flash.addFlashAttribute("succes","L'état de la tâche et le suivi ont été actualisés."); return "redirect:/personnel/taches/"+id;
    }
    @PostMapping("/taches/{id}/realisation")
    String realiser(@PathVariable int id,@ModelAttribute RealisationForm form,@RequestParam(required=false) MultipartFile fichier,Principal p,RedirectAttributes flash) {
        documents.terminerAvecDocument(access.acteur(p),id,form,fichier);
        flash.addFlashAttribute("succes","La réalisation a été vérifiée. Le suivi est recalculé à partir des tâches."); return "redirect:/personnel/taches/"+id;
    }
    @PostMapping("/employes/{id}/restitution/{type}/{attribution}")
    String restituer(@PathVariable String id,@PathVariable String type,@PathVariable int attribution,@RequestParam LocalDate date,Principal p,RedirectAttributes flash) {
        ressources.restituer(access.acteur(p),id,type,attribution,date); return retour(id,flash,"La restitution est enregistrée ; l'intégration historique est conservée.");
    }
    @GetMapping("/catalogues")
    String catalogues(Principal p,Model m) {
        access.acteur(p).exigerRh(); m.addAttribute("materiel",ressources.catalogue("materiel")); m.addAttribute("formation",ressources.catalogue("formation")); return "personnel/catalogues";
    }
    @PostMapping("/catalogues/{type}")
    String catalogue(@PathVariable String type,@RequestParam(required=false) Integer id,@RequestParam String nom,Principal p,RedirectAttributes flash) {
        ressources.enregistrerCatalogue(access.acteur(p),type,id,nom); flash.addFlashAttribute("succes","Le catalogue a été enregistré."); return "redirect:/personnel/catalogues";
    }
    @GetMapping("/historique")
    String historique(@RequestParam(defaultValue="") String employe,@RequestParam(defaultValue="") String objet,
            @RequestParam(defaultValue="") String action,@RequestParam(defaultValue="0") int page,Principal p,Model m) {
        m.addAttribute("mouvements",audit.lister(access.acteur(p),employe,objet,action,page));
        m.addAttribute("employeFiltre",employe); m.addAttribute("objet",objet); m.addAttribute("action",action); m.addAttribute("page",Math.max(0,page));
        return "personnel/historique";
    }
    private String retour(String id,RedirectAttributes flash,String message) {
        flash.addFlashAttribute("succes",message); return "redirect:/personnel/employes/"+id;
    }
}
