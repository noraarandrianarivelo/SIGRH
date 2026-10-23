package com.projet.sigrh.service;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
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
        else genererContrat(a,contrat);
    }
    @Transactional
    public int signerEtGenerer(Acteur a,int contrat,java.time.LocalDate date) {
        personnel.signer(a,contrat,date);
        return genererContrat(a,contrat);
    }
    private int genererContrat(Acteur a,int contrat) {
        var c=personnel.ligne("SELECT c.*,e.nom,e.prenom,p.libelle AS poste FROM contrat c JOIN employe e ON e.id=c.id_employe JOIN poste p ON p.id=c.id_poste WHERE c.id=?",contrat);
        byte[] pdf=pdf(c);
        Path temporaire=null;
        try {
            Files.createDirectories(stockage);
            temporaire=Files.createTempFile(stockage,".contrat-genere-",".tmp");
            Files.write(temporaire,pdf);
            String reference=UUID.randomUUID()+".pdf";
            Path destination=stockage.resolve(reference);
            Files.move(temporaire,destination,StandardCopyOption.ATOMIC_MOVE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status!=STATUS_COMMITTED) try { Files.deleteIfExists(destination); } catch(IOException e) { org.slf4j.LoggerFactory.getLogger(DocumentContratService.class).error("Compensation du document impossible : {}",destination,e); }
                }
            });
            int id=db.queryForObject("INSERT INTO document_contrat(id_contrat,type_document,reference_fichier) VALUES (?,'CONTRAT_SIGNE',?) RETURNING id",Integer.class,contrat,reference);
            audit.tracer(a,texte(c,"id_employe"),"document_contrat",id,null);
            return id;
        } catch(IOException e) { throw new BusinessException("La génération du contrat PDF a échoué.",HttpStatus.SERVICE_UNAVAILABLE); }
        finally { if(temporaire!=null) try { Files.deleteIfExists(temporaire); } catch(IOException ignored) { } }
    }
    public int dernierDocument(Acteur a,int contrat) {
        autoriser(a,contrat,"CONTRAT_SIGNE",false);
        Integer id=db.query("SELECT id FROM document_contrat WHERE id_contrat=? AND type_document='CONTRAT_SIGNE' ORDER BY id DESC LIMIT 1",
                rs->rs.next()?rs.getInt(1):null,contrat);
        if(id==null) throw new BusinessException("Aucun PDF signé n'est disponible.",HttpStatus.NOT_FOUND);
        return id;
    }
    private byte[] pdf(Map<String,Object> c) {
        List<String> lines=new ArrayList<>();
        lines.add("CONTRAT DE TRAVAIL"); lines.add("");
        lines.add("Employé : "+Objects.toString(c.get("prenom"),"")+" "+c.get("nom"));
        lines.add("Poste : "+c.get("poste")); lines.add("Type : "+c.get("type_contrat"));
        lines.add("Début : "+c.get("date_debut")); lines.add("Fin : "+Objects.toString(c.get("date_fin"),"Non définie"));
        lines.add("Date de signature : "+c.get("date_signature")); lines.add("Salaire mensuel : "+c.get("salaire_mensuel")+" MGA");
        lines.add("Horaires : "+Objects.toString(c.get("horaires"),"")); lines.add("Lieu : "+Objects.toString(c.get("lieu_travail"),""));
        lines.add(""); lines.add("Conditions :");
        for(String line:Objects.toString(c.get("conditions"),"Aucune condition supplémentaire.").split("\\R",-1)) lines.add(line);
        lines.add(""); lines.add("Signature employeur : ____________________"); lines.add("Signature salarié : ____________________");
        List<String> commands=new ArrayList<>(); int y=790;
        for(String line:lines) { if(y<45) break; commands.add("BT /F1 11 Tf 50 "+y+" Td ("+escapePdf(line)+") Tj ET"); y-=18; }
        byte[] stream=String.join("\n",commands).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        List<byte[]> objects=new ArrayList<>();
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"));
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        ByteArrayOutputStream content=new ByteArrayOutputStream();
        try { content.write(bytes("<< /Length "+stream.length+" >>\nstream\n")); content.write(stream); content.write(bytes("\nendstream")); } catch(IOException impossible) { throw new IllegalStateException(impossible); }
        objects.add(content.toByteArray());
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try {
            out.write(bytes("%PDF-1.4\n%âãÏÓ\n")); List<Integer> offsets=new ArrayList<>();
            for(int i=0;i<objects.size();i++){ offsets.add(out.size()); out.write(bytes((i+1)+" 0 obj\n")); out.write(objects.get(i)); out.write(bytes("\nendobj\n")); }
            int xref=out.size(); out.write(bytes("xref\n0 6\n0000000000 65535 f \n"));
            for(int offset:offsets) out.write(bytes(String.format(Locale.ROOT,"%010d 00000 n \n",offset)));
            out.write(bytes("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF"));
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
        return out.toByteArray();
    }
    private static byte[] bytes(String value) { return value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1); }
    private static String escapePdf(String value) { return value.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]","?").replace("\\","\\\\").replace("(","\\(").replace(")","\\)"); }
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
