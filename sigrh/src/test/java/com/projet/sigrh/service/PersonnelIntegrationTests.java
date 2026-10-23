package com.projet.sigrh.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static com.projet.sigrh.service.PersonnelService.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.projet.sigrh.dto.personnel.*;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.service.PersonnelAccess.Acteur;

/** Exécution explicite seulement, sur une base ISOLÉE préchargée avec les trois scripts. */
@SpringBootTest(properties={"spring.jpa.show-sql=false","sigrh.contrat.upload-dir=${java.io.tmpdir}/sigrh-bloc4-test-documents"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="sigrh.bloc4.integration",matches="true")
@Transactional
class PersonnelIntegrationTests {
    @TestConfiguration
    static class Temps {
        @Bean @Primary Clock horlogeTest() { return Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"),ZoneId.of("Indian/Antananarivo")); }
    }
    @Autowired PersonnelService personnel;
    @Autowired OnboardingService onboarding;
    @Autowired RessourcePersonnelService ressources;
    @Autowired DocumentContratService documents;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean MouvementRhService journal;
    final Acteur rh=new Acteur("B4RH001","ROLE_RH");
    final Acteur it=new Acteur("B4IT001","ROLE_MANAGER");
    final Acteur finance=new Acteur("B4PAY001","ROLE_FINANCE");
    final Acteur formation=new Acteur("B4FOR001","ROLE_MANAGER");

    int entree(String employe) { return db.queryForObject("SELECT id FROM contrat WHERE id_employe=? AND id_contrat_precedent IS NULL",Integer.class,employe); }
    int tache(String employe,String etape) { return db.queryForObject("SELECT t.id FROM onboarding_tache t JOIN contrat c ON c.id=t.id_contrat WHERE c.id_employe=? AND t.etape=? ORDER BY t.id DESC LIMIT 1",Integer.class,employe,etape); }
    RealisationForm resultat(Integer existant,Integer catalogue,LocalDate date) { return new RealisationForm(existant,catalogue,null,null,null,date,null,true); }
    int compte(String emp) { return db.queryForObject("SELECT id FROM compte_employe WHERE id_employe=? ORDER BY id LIMIT 1",Integer.class,emp); }
    int poste() { return db.queryForObject("SELECT id FROM poste ORDER BY id LIMIT 1",Integer.class); }

    @Test void scenariosEtDatesInclusives() {
        assertEquals("TERMINE",onboarding.suivi(rh,"B4N001").get(0).get("statut"));
        assertEquals("BLOQUE",onboarding.suivi(rh,"B4N002").get(0).get("statut"));
        assertEquals(3L,onboarding.suivi(rh,"B4N002").get(0).get("nombre_etapes_terminees"));
        assertEquals("A_FAIRE",onboarding.suivi(rh,"B4N003").get(0).get("statut"));
        var soa=personnel.contrats(rh,"B4N001");
        assertEquals(1,soa.stream().filter(c->"En vigueur".equals(c.get("situation"))).count());
        var c=new HashMap<>(soa.get(0)); c.put("date_debut",LocalDate.of(2026,11,1));
        assertEquals("Futur signé",situation(c,LocalDate.of(2026,10,22)));
        c.put("date_debut",LocalDate.of(2026,10,1));c.put("date_fin",LocalDate.of(2026,10,22));
        assertEquals("En vigueur",situation(c,LocalDate.of(2026,10,22)));
        assertEquals("Future",personnel.affectations(rh,"B4N003").get(0).get("situation"));
    }
    @Test void renouvellementConserveAncienEtParcoursSansProposition() {
        int ancien=entree("B4N002"); var c=personnel.contrat(rh,ancien);
        String snapshot=db.queryForObject("SELECT to_jsonb(c)::text FROM contrat c WHERE id=?",String.class,ancien);
        LocalDate debut=date(c,"date_fin").plusDays(1);
        int nouveau=personnel.enregistrerContrat(rh,"B4N002",new ContratForm(null,ancien,poste(),"CDI",debut,null,new BigDecimal("1300000.00"),"40h","Annexe","Conditions renouvelées"));
        assertEquals(ancien,personnel.entree(nouveau));
        assertNull(personnel.contrat(rh,nouveau).get("id_proposition"));
        assertEquals(snapshot,db.queryForObject("SELECT to_jsonb(c)::text FROM contrat c WHERE id=?",String.class,ancien));
        assertEquals(1,onboarding.suivi(rh,"B4N002").size());
        assertThrows(BusinessException.class,()->onboarding.creer(rh,nouveau,"COMPTE","Autre parcours interdit","B4IT001"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM inscription_paie WHERE id_employe='B4N002'",Integer.class));
    }
    @Test void validationsContratEtCycles() {
        var debut=LocalDate.of(2027,1,1);
        assertThrows(BusinessException.class,()->personnel.enregistrerContrat(rh,"B4N002",new ContratForm(null,entree("B4N001"),poste(),"CDI",debut,null,BigDecimal.ONE,null,null,null)));
        assertThrows(BusinessException.class,()->personnel.enregistrerContrat(rh,"B4N002",new ContratForm(null,null,poste(),"CDD",debut,debut.minusDays(1),BigDecimal.ONE,null,null,null)));
        assertThrows(BusinessException.class,()->personnel.enregistrerContrat(rh,"B4N002",new ContratForm(null,null,poste(),"CDI",debut,null,BigDecimal.valueOf(-1),null,null,null)));
        assertThrows(BusinessException.class,()->personnel.signer(rh,entree("B4N003"),null));
        int racine=entree("B4N001");int dernier=db.queryForObject("SELECT id FROM contrat WHERE id_employe='B4N001' AND id_contrat_precedent IS NOT NULL",Integer.class);
        db.update("UPDATE contrat SET id_contrat_precedent=? WHERE id=?",dernier,racine);
        assertThrows(BusinessException.class,()->personnel.entree(dernier));
    }
    @Test void mobiliteCreePeriodeEtSynchroniseDepartement() {
        int avant=db.queryForObject("SELECT id FROM affectation WHERE id_employe='B4N002' AND date_fin IS NULL",Integer.class);
        int cible=db.queryForObject("SELECT id FROM poste WHERE libelle LIKE '%Chef de projet%' LIMIT 1",Integer.class);
        personnel.changerAffectation(rh,"B4N002",cible,entree("B4N002"),"Projets","Mobilité test");
        assertEquals(LocalDate.of(2026,10,21),db.queryForObject("SELECT date_fin FROM affectation WHERE id=?",LocalDate.class,avant));
        assertEquals(db.queryForObject("SELECT id_departement FROM poste WHERE id=?",Integer.class,cible),db.queryForObject("SELECT id_departement FROM employe WHERE id='B4N002'",Integer.class));
        assertEquals(3,db.queryForObject("SELECT count(*) FROM mouvement_rh WHERE id_employe_concerne='B4N002' AND date_mouvement>CURRENT_TIMESTAMP-INTERVAL '1 second' AND table_concernee IN ('affectation','employe')",Integer.class));
        assertThrows(BusinessException.class,()->personnel.changerAffectation(rh,"B4N002",poste(),null,"","Deuxième changement même jour"));
        assertThrows(BusinessException.class,()->personnel.changerAffectation(rh,"B4N003",poste(),entree("B4N002"),"","Mauvais contrat"));
    }
    @Test void categorieManquanteEtDeuxMateriels() {
        int root=entree("B4N003");
        // Fixture temporaire pour un employé sans checklist.
        String emp="TX"+UUID.randomUUID().toString().substring(0,8);
        db.update("INSERT INTO employe(id,nom) VALUES (?,'Test')",emp);
        int contrat=personnel.enregistrerContrat(rh,emp,new ContratForm(null,null,poste(),"CDI",LocalDate.of(2026,11,1),null,BigDecimal.ONE,null,null,null));
        assertEquals("A_CONFIGURER",onboarding.suivi(rh,emp).get(0).get("statut"));
        assertEquals(6,onboarding.etapes(rh,emp).size());
        int materiel=db.queryForObject("SELECT id FROM materiel WHERE nom LIKE '%Telephone inventaire 004%'",Integer.class);
        int t=tache("B4N002","MATERIEL");
        assertEquals("BLOQUEE",onboarding.tache(it,t).get("statut"));
        onboarding.terminer(it,t,resultat(null,materiel,LocalDate.of(2026,10,22)));
        var etape=onboarding.etapes(rh,"B4N002").stream().filter(e->"MATERIEL".equals(e.get("etape"))).findFirst().orElseThrow();
        assertEquals("TERMINEE",etape.get("statut"));
        assertEquals(4L,onboarding.suivi(rh,"B4N002").get(0).get("nombre_etapes_terminees"));
        int nombre=db.queryForObject("SELECT count(*) FROM materiel_employe",Integer.class);
        int traces=db.queryForObject("SELECT count(*) FROM mouvement_rh",Integer.class);
        onboarding.terminer(it,t,resultat(null,materiel,LocalDate.of(2026,10,22)));
        assertEquals(nombre,db.queryForObject("SELECT count(*) FROM materiel_employe",Integer.class));
        assertEquals(traces,db.queryForObject("SELECT count(*) FROM mouvement_rh",Integer.class));
        onboarding.changerStatut(it,t,"EN_COURS");
        assertNull(onboarding.tache(it,t).get("date_completion"));
        assertEquals("EN_COURS",onboarding.etapes(rh,"B4N002").stream().filter(e->"MATERIEL".equals(e.get("etape"))).findFirst().orElseThrow().get("statut"));
        assertTrue(db.queryForObject("SELECT EXISTS(SELECT 1 FROM mouvement_rh WHERE table_concernee='onboarding_tache' AND id_ligne=? AND jsonb_exists(valeurs_apres,'_preuve'))",Boolean.class,t+""));
        assertNotEquals(root,contrat);
    }
    @Test void responsableEtPreuvePrecise() {
        int t=tache("B4N003","COMPTE");
        assertThrows(BusinessException.class,()->onboarding.terminer(rh,t,resultat(null,null,LocalDate.of(2026,10,22))));
        assertThrows(BusinessException.class,()->onboarding.terminer(it,t,resultat(compte("B4N002"),null,null)));
        onboarding.reassigner(rh,t,"B4FOR001","Accès ERP corrigé");
        assertThrows(BusinessException.class,()->onboarding.terminer(it,t,resultat(null,null,LocalDate.of(2026,10,22))));
        assertEquals("B4RH001",onboarding.tache(formation,t).get("id_assignateur"));
        assertThrows(BusinessException.class,()->personnel.contrats(it,"B4N002"));
        assertThrows(BusinessException.class,()->personnel.changerAffectation(finance,"B4N002",poste(),null,"","Interdit"));
    }
    @Test void formationInscriteNeSuffitPasEtDerniereTacheTermineParcours() {
        int inscription=db.queryForObject("SELECT id FROM formation_employe WHERE id_employe='B4N002'",Integer.class);
        int t=tache("B4N002","FORMATION");
        assertThrows(BusinessException.class,()->onboarding.terminer(formation,t,resultat(inscription,null,null)));
        onboarding.terminer(formation,t,resultat(inscription,null,LocalDate.of(2026,10,22)));
        assertEquals(LocalDate.of(2026,10,22),db.queryForObject("SELECT date_completion FROM formation_employe WHERE id=?",LocalDate.class,inscription));
        // Sur le parcours déjà terminé, réouvrir puis terminer la dernière tâche recalcule les deux vues.
        int dernier=tache("B4N001","COMPTE");
        onboarding.changerStatut(it,dernier,"EN_COURS");
        assertEquals("EN_COURS",onboarding.suivi(rh,"B4N001").get(0).get("statut"));
        int preuve=db.queryForObject("SELECT id FROM compte_employe WHERE id_employe='B4N001' AND systeme='ERP'",Integer.class);
        onboarding.terminer(it,dernier,resultat(preuve,null,null));
        assertEquals("TERMINE",onboarding.suivi(rh,"B4N001").get(0).get("statut"));
    }
    @Test void ressourcesIndisponiblesEtRestitutionHistorique() {
        int t=tache("B4N003","MATERIEL");
        int pris=db.queryForObject("SELECT id_materiel FROM materiel_employe WHERE id_employe='B4N002' AND date_restitution IS NULL LIMIT 1",Integer.class);
        assertThrows(BusinessException.class,()->onboarding.terminer(it,t,resultat(null,pris,LocalDate.of(2026,10,22))));
        int attribution=db.queryForObject("SELECT id FROM materiel_employe WHERE id_employe='B4N001' AND date_restitution IS NULL LIMIT 1",Integer.class);
        assertThrows(BusinessException.class,()->ressources.restituer(rh,"B4N001","materiel",attribution,LocalDate.of(2025,1,1)));
        ressources.restituer(rh,"B4N001","materiel",attribution,LocalDate.of(2026,10,22));
        assertEquals("TERMINE",onboarding.suivi(rh,"B4N001").get(0).get("statut"));
    }
    @Test void documentsStockageEtDroits() throws Exception {
        int c=entree("B4N002");
        assertTrue(documents.documents(rh,c).stream().noneMatch(d->Boolean.TRUE.equals(d.get("disponible"))));
        int fictif=nombre(documents.documents(rh,c).get(0),"id");
        assertThrows(BusinessException.class,()->documents.telecharger(rh,fictif));
        assertThrows(BusinessException.class,()->documents.telecharger(it,fictif));
        var mauvais=new MockMultipartFile("fichier","contrat.pdf","application/pdf","pas un PDF".getBytes());
        assertThrows(BusinessException.class,()->documents.ajouter(rh,c,"AUTRE",mauvais));
        var fichier=new MockMultipartFile("fichier","../../contrat.pdf","application/pdf","%PDF-1.4\nfixture\n%%EOF".getBytes());
        documents.ajouter(rh,c,"CONTRAT_SIGNE",fichier);
        int id=nombre(documents.documents(rh,c).get(0),"id");
        assertTrue(Files.isRegularFile(documents.telecharger(rh,id)));
        assertFalse(documents.documents(rh,c).get(0).containsKey("reference_fichier"));
        assertEquals(3,documents.documents(rh,c).size());
    }
    @Test void renduDesEcransEtAccesDirects() throws Exception {
        mvc.perform(get("/personnel").with(user("b4_rh").authorities(()->"ROLE_RH"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("B4N002")));
        for(String emp:List.of("B4N001","B4N002","B4N003")) mvc.perform(get("/personnel/employes/"+emp).with(user("b4_rh").authorities(()->"ROLE_RH"))).andExpect(status().isOk());
        mvc.perform(get("/personnel/employes/B4N002/contrat").param("precedent",entree("B4N002")+"").with(user("b4_rh").authorities(()->"ROLE_RH"))).andExpect(status().isOk());
        mvc.perform(get("/personnel/catalogues").with(user("b4_rh").authorities(()->"ROLE_RH"))).andExpect(status().isOk());
        mvc.perform(get("/personnel/historique").with(user("b4_rh").authorities(()->"ROLE_RH"))).andExpect(status().isOk());
        mvc.perform(get("/personnel/taches").with(user("b4_it").authorities(()->"ROLE_MANAGER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("salaire_mensuel"))));
        mvc.perform(get("/personnel/taches/"+tache("B4N002","MATERIEL")).with(user("b4_it").authorities(()->"ROLE_MANAGER"))).andExpect(status().isOk());
        mvc.perform(get("/personnel/employes/B4N002").with(user("b4_it").authorities(()->"ROLE_MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/personnel/historique").with(user("b4_paie").authorities(()->"ROLE_FINANCE"))).andExpect(status().isForbidden());
        mvc.perform(post("/personnel/taches/"+tache("B4N002","MATERIEL")+"/statut").param("statut","EN_COURS").with(user("b4_it").authorities(()->"ROLE_MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/personnel/taches/"+tache("B4N002","MATERIEL")+"/statut").with(csrf()).param("statut","EN_COURS").param("id_acteur","B4RH001").with(user("b4_it").authorities(()->"ROLE_MANAGER"))).andExpect(status().is3xxRedirection());
        assertEquals("B4IT001",db.queryForObject("SELECT id_acteur FROM mouvement_rh WHERE table_concernee='onboarding_tache' AND id_ligne=? ORDER BY id DESC LIMIT 1",String.class,tache("B4N002","MATERIEL")+""));
        mvc.perform(post("/personnel/employes/B4N003/taches").with(csrf()).param("contrat",entree("B4N003")+"")
                .param("etape","COMPTE").param("libelle","ERP test acteur").param("responsable","B4IT001")
                .param("id_assignateur","B4DIR001").with(user("b4_rh").authorities(()->"ROLE_RH"))).andExpect(status().is3xxRedirection());
        assertEquals("B4RH001",db.queryForObject("SELECT id_assignateur FROM onboarding_tache WHERE libelle='ERP test acteur'",String.class));
        mvc.perform(post("/login").with(csrf()).param("username","b4_it").param("password","Bloc4Test2026!")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test void signatureDirectionSurSaTacheSansAccesAuxAutresContrats() throws Exception {
        int c=entree("B4N003"),t=tache("B4N003","SIGNATURE_CONTRAT");
        onboarding.reassigner(rh,t,"B4DIR001","Signer le contrat d'entrée");
        var direction=new Acteur("B4DIR001","ROLE_DIRECTION");
        mvc.perform(get("/personnel/taches/"+t).with(user("b4_direction").authorities(()->"ROLE_DIRECTION"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("salaire_mensuel"))));
        onboarding.terminer(direction,t,resultat(null,null,LocalDate.of(2026,10,22)));
        assertEquals("SIGNE",personnel.contrat(rh,c).get("statut"));
        assertEquals("TERMINEE",onboarding.tache(direction,t).get("statut"));
        assertThrows(BusinessException.class,()->personnel.contrats(direction,"B4N003"));
        int autre=nombre(documents.documents(rh,entree("B4N002")).get(0),"id");
        assertThrows(BusinessException.class,()->documents.telecharger(direction,autre));
    }

    @Test void referenceSortantDuStockageRefusee() {
        int c=entree("B4N002");
        int id=db.queryForObject("INSERT INTO document_contrat(id_contrat,type_document,reference_fichier) VALUES (?,'AUTRE','../../../../etc/passwd') RETURNING id",Integer.class,c);
        assertThrows(BusinessException.class,()->documents.telecharger(rh,id));
        assertTrue(documents.documents(rh,c).stream().filter(d->nombre(d,"id")==id).noneMatch(d->Boolean.TRUE.equals(d.get("disponible"))));
    }

    @Test void formulairesRenouvellementEtConfirmationPaie() throws Exception {
        int root=entree("B4N002");
        LocalDate debut=date(personnel.contrat(rh,root),"date_fin").plusDays(1);
        mvc.perform(post("/personnel/employes/B4N002/contrat").with(csrf())
                .with(user("b4_rh").authorities(()->"ROLE_RH"))
                .param("precedent",root+"").param("poste",poste()+"").param("type","CDI")
                .param("debut",debut.toString()).param("salaire","1400000.00").param("fin","").param("id",""))
                .andExpect(status().is3xxRedirection());
        int renouvellement=db.queryForObject("SELECT id FROM contrat WHERE id_contrat_precedent=?",Integer.class,root);
        assertEquals(root,personnel.entree(renouvellement));
        int t=tache("B4N002","INSCRIPTION_PAIE");
        mvc.perform(multipart("/personnel/taches/"+t+"/realisation").with(csrf())
                .with(user("b4_paie").authorities(()->"ROLE_FINANCE"))
                .param("date","2026-10-22").param("priseEnCharge","2026-10-15").param("confirme","true"))
                .andExpect(status().is3xxRedirection());
        assertEquals("TERMINEE",onboarding.tache(finance,t).get("statut"));
        int inscription=db.queryForObject("SELECT id FROM inscription_paie WHERE id_employe='B4N002'",Integer.class);
        onboarding.changerStatut(finance,t,"EN_COURS");
        onboarding.terminer(finance,t,resultat(inscription,null,null));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM inscription_paie WHERE id_employe='B4N002'",Integer.class));
        assertEquals("B4PAY001",db.queryForObject("SELECT id_acteur FROM mouvement_rh WHERE table_concernee='inscription_paie' AND id_ligne=?",String.class,inscription+""));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void echecDuJournalAnnuleLaMobilite() {
        String avant=journal.snapshot("employe","B4N002");
        var affectations=db.queryForList("SELECT * FROM affectation WHERE id_employe='B4N002' ORDER BY id");
        int traces=db.queryForObject("SELECT count(*) FROM mouvement_rh",Integer.class);
        doThrow(new BusinessException("Échec simulé du journal")).when(cibleJournal()).tracer(eq(rh),eq("B4N002"),eq("affectation"),any(),any());
        try {
            assertThrows(BusinessException.class,()->personnel.changerAffectation(rh,"B4N002",poste(),entree("B4N002"),"Test","Échec simulé"));
            assertEquals(avant,journal.snapshot("employe","B4N002"));
            assertEquals(affectations,db.queryForList("SELECT * FROM affectation WHERE id_employe='B4N002' ORDER BY id"));
            assertEquals(traces,db.queryForObject("SELECT count(*) FROM mouvement_rh",Integer.class));
        } finally { reset(cibleJournal()); }
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void fichierEtSignatureAnnulesSiAuditDuDocumentEchoue() throws Exception {
        int c=entree("B4N003");
        Path dir=Path.of(System.getProperty("java.io.tmpdir"),"sigrh-bloc4-test-documents");
        Files.createDirectories(dir);
        Set<Path> avant; try(var files=Files.list(dir)) { avant=files.collect(java.util.stream.Collectors.toSet()); }
        doThrow(new BusinessException("Échec simulé du journal")).when(cibleJournal()).tracer(eq(rh),eq("B4N003"),eq("document_contrat"),any(),any());
        try {
            assertThrows(BusinessException.class,()->documents.signerAvecDocument(rh,c,LocalDate.of(2026,10,22),new MockMultipartFile("fichier","signe.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes())));
            assertEquals("BROUILLON",personnel.contrat(rh,c).get("statut"));
            assertNull(personnel.contrat(rh,c).get("date_signature"));
            try(var files=Files.list(dir)) { assertEquals(avant,files.collect(java.util.stream.Collectors.toSet())); }
        } finally { reset(cibleJournal()); }
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void deuxRenouvellementsConcurrentsUnSeulEstValide() throws Exception {
        String emp=nouvelEmploye();
        int ancien=db.queryForObject("INSERT INTO contrat(id_employe,id_poste,type_contrat,date_debut,date_fin,date_signature,salaire_mensuel,statut) VALUES (?,?,'CDD','2026-01-01','2026-09-30','2025-12-30',1,'TERMINE') RETURNING id",Integer.class,emp,poste());
        var f=new ContratForm(null,ancien,poste(),"CDI",LocalDate.of(2026,10,1),null,BigDecimal.TEN,null,null,null);
        var resultats=concurrents(()->personnel.enregistrerContrat(rh,emp,f),()->personnel.enregistrerContrat(rh,emp,f));
        assertEquals(1,resultats.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM contrat WHERE id_contrat_precedent=?",Integer.class,ancien));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void deuxMobilitesConcurrentesUneSeuleEstValidee() throws Exception {
        String emp=nouvelEmploye();
        db.update("INSERT INTO affectation(id_employe,id_poste,date_debut) VALUES (?,?,'2026-10-01')",emp,poste());
        var resultats=concurrents(()->{personnel.changerAffectation(rh,emp,poste(),null,"A","Mobilité A");return 1;},
                ()->{personnel.changerAffectation(rh,emp,poste(),null,"B","Mobilité B");return 1;});
        assertEquals(1,resultats.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM affectation WHERE id_employe=? AND date_fin IS NULL",Integer.class,emp));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void deuxRemisesConcurrentesDuMemeObjet() throws Exception {
        int objet=db.queryForObject("INSERT INTO materiel(nom) VALUES ('Objet concurrence test') RETURNING id",Integer.class);
        String e1=nouvelEmploye(),e2=nouvelEmploye();
        int t1=nouvelleTache(e1,"MATERIEL"),t2=nouvelleTache(e2,"MATERIEL");
        var resultats=concurrents(()->{onboarding.terminer(it,t1,resultat(null,objet,LocalDate.of(2026,10,22)));return 1;},
                ()->{onboarding.terminer(it,t2,resultat(null,objet,LocalDate.of(2026,10,22)));return 1;});
        assertEquals(1,resultats.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM materiel_employe WHERE id_materiel=?",Integer.class,objet));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM onboarding_tache WHERE id IN (?,?) AND statut='TERMINEE'",Integer.class,t1,t2));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void deuxRemisesConcurrentesDuMemeBadge() throws Exception {
        String badge="TEST-"+UUID.randomUUID();
        String e1=nouvelEmploye(),e2=nouvelEmploye();
        int t1=nouvelleTache(e1,"BADGE"),t2=nouvelleTache(e2,"BADGE");
        var f=new RealisationForm(null,null,badge,null,null,LocalDate.of(2026,10,22),null,true);
        var resultats=concurrents(()->{onboarding.terminer(it,t1,f);return 1;},()->{onboarding.terminer(it,t2,f);return 1;});
        assertEquals(1,resultats.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM badge_employe WHERE numero_badge=?",Integer.class,badge));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void reassignationConcurrenteAvecCompletion() throws Exception {
        String emp=nouvelEmploye();int id=nouvelleTache(emp,"COMPTE");
        var f=new RealisationForm(null,null,null,"ERP",emp,LocalDate.of(2026,10,22),null,true);
        var resultats=concurrents(()->{onboarding.reassigner(rh,id,"B4FOR001","ERP explicite");return 1;},
                ()->{onboarding.terminer(it,id,f);return 1;});
        var t=onboarding.tache(formation,id);
        assertEquals("B4FOR001",t.get("id_responsable"));
        int comptes=db.queryForObject("SELECT count(*) FROM compte_employe WHERE id_employe=?",Integer.class,emp);
        assertEquals(resultats.get(1)?1:0,comptes);
        assertEquals(resultats.get(1)?"TERMINEE":"A_FAIRE",t.get("statut"));
        assertThrows(BusinessException.class,()->onboarding.terminer(it,id,f));
    }

    @Test void salarieConsulteSeulementSonDossierSansRoleManager() throws Exception {
        var comptes=db.queryForList("SELECT u.username,r.role_name FROM utilisateur u JOIN role_user r ON r.id=u.id_role WHERE u.id_employee='B4N001'");
        String login;
        String roleNom;
        if(comptes.isEmpty()) {
            int role=db.queryForObject("INSERT INTO role_user(role_name) VALUES ('ROLE_SALARIE_TEST') RETURNING id",Integer.class);
            db.update("INSERT INTO utilisateur(username,password,id_role,id_employee) SELECT 'test_soa',password,?,'B4N001' FROM utilisateur WHERE username='b4_rh'",role);
            login="test_soa";
            roleNom="ROLE_SALARIE_TEST";
        } else {
            login=(String)comptes.get(0).get("username");
            roleNom=(String)comptes.get(0).get("role_name");
        }
        mvc.perform(get("/personnel/employes/B4N001").with(user(login).authorities(()->roleNom))).andExpect(status().isOk());
        mvc.perform(get("/personnel/employes/B4N002").with(user(login).authorities(()->roleNom))).andExpect(status().isForbidden());
        int doc=nombre(documents.documents(rh,entree("B4N002")).get(0),"id");
        mvc.perform(get("/personnel/documents/"+doc).with(user(login).authorities(()->roleNom))).andExpect(status().isForbidden());
        mvc.perform(post("/personnel/employes/B4N001/affectation").with(csrf()).param("poste",poste()+"").param("motif","Interdit").param("lieu","").with(user(login).authorities(()->roleNom))).andExpect(status().isForbidden());
    }

    MouvementRhService cibleJournal() { return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(journal); }

    String nouvelEmploye() {
        String id="T"+UUID.randomUUID().toString().replace("-","").substring(0,9);
        db.update("INSERT INTO employe(id,nom) VALUES (?,'Test isolé')",id);return id;
    }
    int nouvelleTache(String emp,String etape) {
        int c=db.queryForObject("INSERT INTO contrat(id_employe,id_poste,type_contrat,date_debut,date_creation,salaire_mensuel) VALUES (?,?,'CDI','2026-10-22','2026-10-20',1) RETURNING id",Integer.class,emp,poste());
        onboarding.creer(rh,c,etape,"Résultat de test précis","B4IT001");
        return db.queryForObject("SELECT id FROM onboarding_tache WHERE id_contrat=?",Integer.class,c);
    }
    List<Boolean> concurrents(Supplier<Integer> a,Supplier<Integer> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var depart=new CountDownLatch(1);
        try {
            List<Future<Boolean>> resultats=new ArrayList<>();
            for(var action:List.of(a,b)) resultats.add(pool.submit(()->{depart.await();try {action.get();return true;}catch(BusinessException e) {return false;}}));
            depart.countDown();return List.of(resultats.get(0).get(15,TimeUnit.SECONDS),resultats.get(1).get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
