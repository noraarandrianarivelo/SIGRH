package com.projet.sigrh.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.service.PersonnelAccess.Acteur;
import static com.projet.sigrh.service.PersonnelService.*;

@Service
@Transactional(readOnly = true)
public class DocumentContratService {
    private static final long MAX=10*1024*1024;
    private final JdbcTemplate db;
    private final PersonnelService personnel;
    private final MouvementRhService audit;
    private final OnboardingService onboarding;
    private final Path stockage;
    public DocumentContratService(JdbcTemplate db,PersonnelService personnel,MouvementRhService audit,OnboardingService onboarding,
            @Value("${sigrh.contrat.upload-dir:uploads/contrats}") String stockage) {
        this.db=db; this.personnel=personnel; this.audit=audit; this.onboarding=onboarding; this.stockage=Path.of(stockage).toAbsolutePath().normalize();
    }
    @Transactional
    public void signerAvecDocument(Acteur a,int contrat,java.time.LocalDate date,MultipartFile fichier) {
        personnel.signer(a,contrat,date);
        if(fichier!=null && !fichier.isEmpty()) ajouter(a,contrat,"CONTRAT_SIGNE",fichier);
    }
    @Transactional
    public void terminerAvecDocument(Acteur a,int id,com.projet.sigrh.dto.personnel.RealisationForm f,MultipartFile fichier) {
        var t=onboarding.tache(a,id);
        // Même verrou stable que la complétion, avant de décider si un fichier est nécessaire.
        personnel.verrouillerEmploye(texte(t,"id_employe"));
        t=onboarding.tache(a,id);
        boolean terminee="TERMINEE".equals(t.get("statut"));
        onboarding.terminer(a,id,f);
        if(!terminee && fichier!=null && !fichier.isEmpty()) {
            if(!"SIGNATURE_CONTRAT".equals(t.get("etape"))) throw new BusinessException("Un document signé ne peut accompagner qu'une tâche de signature.");
            ajouter(a,nombre(t,"id_contrat"),"CONTRAT_SIGNE",fichier);
        }
    }
    private void autoriser(Acteur a,int contrat,String type,boolean ecriture) {
        var c=personnel.contratInterne(contrat);
        if(a.rh()) return;
        if(!ecriture && a.employe().equals(c.get("id_employe"))) return;
        if(a.signature() && (type==null || List.of("CONTRAT_PDF","CONTRAT_SIGNE").contains(type)) && !db.queryForList("SELECT id FROM onboarding_tache WHERE id_contrat=? AND etape='SIGNATURE_CONTRAT' AND id_responsable=?",contrat,a.employe()).isEmpty()) return;
        PersonnelAccess.refus();
    }
    public List<Map<String,Object>> documents(Acteur a,int contrat) {
        autoriser(a,contrat,null,false);
        var c=personnel.contratInterne(contrat);
        boolean complet=a.rh() || a.employe().equals(c.get("id_employe"));
        var rows=db.queryForList("SELECT id,id_contrat,type_document,date_ajout,reference_fichier FROM document_contrat WHERE id_contrat=? AND (? OR type_document IN ('CONTRAT_PDF','CONTRAT_SIGNE')) ORDER BY date_ajout DESC,id DESC",contrat,complet);
        for(var r:rows) {
            r.put("disponible",chemin(texte(r,"reference_fichier")).isPresent());
            r.remove("reference_fichier"); // aucun chemin de stockage envoyé au navigateur
        }
        return rows;
    }
    @Transactional
    public void ajouter(Acteur a,int contrat,String type,MultipartFile fichier) {
        if(!List.of("CONTRAT_PDF","CONTRAT_SIGNE","PIECE_IDENTITE","AUTRE").contains(type)) throw new BusinessException("Type de document inconnu.");
        autoriser(a,contrat,type,true);
        if(fichier==null || fichier.isEmpty() || fichier.getSize()>MAX) throw new BusinessException("Choisissez un PDF de 10 Mio maximum.");
        personnel.verrouillerEmploye(texte(personnel.contratInterne(contrat),"id_employe"));
        Path temporaire=null;
        try {
            Files.createDirectories(stockage);
            temporaire=Files.createTempFile(stockage,".contrat-",".tmp");
            fichier.transferTo(temporaire);
            try(var in=Files.newInputStream(temporaire)) {
                if(!Arrays.equals(in.readNBytes(5),new byte[]{'%', 'P','D','F','-'})) throw new BusinessException("Le fichier doit être un document PDF valide.");
            }
            String reference=UUID.randomUUID()+".pdf";
            Path destination=stockage.resolve(reference);
            Files.move(temporaire,destination,StandardCopyOption.ATOMIC_MOVE);
            // Le fichier existe avant l'insertion. Toute annulation DB le supprime.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status!=STATUS_COMMITTED) {
                        try { Files.deleteIfExists(destination); }
                        catch(IOException e) { org.slf4j.LoggerFactory.getLogger(DocumentContratService.class).error("Compensation du document impossible : {}",destination,e); }
                    }
                }
            });
            int id=db.queryForObject("INSERT INTO document_contrat(id_contrat,type_document,reference_fichier) VALUES (?,?,?) RETURNING id",Integer.class,contrat,type,reference);
            audit.tracer(a,texte(personnel.contratInterne(contrat),"id_employe"),"document_contrat",id,null);
        } catch(IOException e) {
            throw new BusinessException("Le stockage du document est indisponible. Réessayez plus tard.",HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            if(temporaire!=null) try { Files.deleteIfExists(temporaire); } catch(IOException ignored) { }
        }
    }
    public Path telecharger(Acteur a,int id) {
        var d=personnel.ligne("SELECT * FROM document_contrat WHERE id=?",id);
        autoriser(a,nombre(d,"id_contrat"),texte(d,"type_document"),false);
        return chemin(texte(d,"reference_fichier")).orElseThrow(()->new BusinessException("Ce document n'est pas disponible dans le stockage (les pièces du jeu de démonstration sont fictives).",HttpStatus.NOT_FOUND));
    }
    private Optional<Path> chemin(String reference) {
        // Références historiques relatives autorisées uniquement à l'intérieur du stockage.
        try {
            Path p=stockage.resolve(reference).normalize();
            if(!p.startsWith(stockage) || !Files.isRegularFile(p)) return Optional.empty();
            Path reel=p.toRealPath();
            if(!reel.startsWith(stockage.toRealPath())) return Optional.empty();
            return Optional.of(reel);
        } catch(IOException | InvalidPathException e) { return Optional.empty(); }
    }
}
