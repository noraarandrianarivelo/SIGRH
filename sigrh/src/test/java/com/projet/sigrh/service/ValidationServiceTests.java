package com.projet.sigrh.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.projet.sigrh.dto.DecisionRequest;
import com.projet.sigrh.model.Besoin;
import com.projet.sigrh.model.BesoinPoste;
import com.projet.sigrh.model.DemandeRecrutement;
import com.projet.sigrh.model.Employe;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.model.Poste;
import com.projet.sigrh.model.RoleUser;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.model.enums.DecisionValidation;
import com.projet.sigrh.model.enums.StatutDemande;
import com.projet.sigrh.repository.DemandeRecrutementRepository;
import com.projet.sigrh.repository.HistoriqueValidationRepository;
import com.projet.sigrh.repository.OffreEmploiRepository;
import com.projet.sigrh.security.Roles;

class ValidationServiceTests {

    @Test
    void approbationFinaleCreeUneOffreParNouveauBesoinPoste() {
        OffreEmploiRepository offreRepository = mock(OffreEmploiRepository.class);
        DemandeRecrutement demande = demande(StatutDemande.ATTENTE_DIRECTION);
        BesoinPoste ligneExistante = ligne(10L, "Développeur", null);
        BesoinPoste ligneProposee = ligne(11L, null, "Analyste");
        BesoinPoste ligneDejaCouverte = ligne(12L, null, "Architecte");
        demande.getBesoin().setLignes(new ArrayList<>(List.of(
                ligneExistante, ligneProposee, ligneDejaCouverte)));
        when(offreRepository.existsByBesoinPosteId(12L)).thenReturn(true);

        ValidationService service = service(demande, offreRepository);
        service.decider(3L, new DecisionRequest(DecisionValidation.APPROUVEE, null),
                utilisateur(Roles.DIRECTION));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<OffreEmploi>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(offreRepository).saveAll(captor.capture());
        List<OffreEmploi> offres = new ArrayList<>();
        captor.getValue().forEach(offres::add);
        assertEquals(StatutDemande.VALIDEE, demande.getStatut());
        assertEquals(2, offres.size());
        OffreEmploi offre = offres.get(0);
        assertSame(ligneExistante, offre.besoinPoste);
        assertEquals("Développeur", offre.titre);
        assertEquals("Besoin de renfort", offre.description);
        assertEquals(LocalDate.now(), offre.dateCreation);
        assertEquals(LocalDate.of(2026, 11, 15), offre.dateLimite);
        assertEquals("OUVERT", offre.statut);
        assertSame(ligneProposee, offres.get(1).besoinPoste);
        assertEquals("Analyste", offres.get(1).titre);
    }

    @Test
    void approbationIntermediaireNeCreePasEncoreDOffre() {
        OffreEmploiRepository offreRepository = mock(OffreEmploiRepository.class);
        DemandeRecrutement demande = demande(StatutDemande.ATTENTE_FINANCE);

        service(demande, offreRepository).decider(3L,
                new DecisionRequest(DecisionValidation.APPROUVEE, null), utilisateur(Roles.FINANCE));

        assertEquals(StatutDemande.ATTENTE_DIRECTION, demande.getStatut());
        verify(offreRepository, never()).saveAll(any());
    }

    private ValidationService service(DemandeRecrutement demande, OffreEmploiRepository offreRepository) {
        DemandeRecrutementService demandeService = mock(DemandeRecrutementService.class);
        DemandeRecrutementRepository demandeRepository = mock(DemandeRecrutementRepository.class);
        when(demandeService.trouver(3L)).thenReturn(demande);
        when(demandeRepository.save(demande)).thenReturn(demande);

        ValidationService service = new ValidationService();
        ReflectionTestUtils.setField(service, "demandeService", demandeService);
        ReflectionTestUtils.setField(service, "demandeRepository", demandeRepository);
        ReflectionTestUtils.setField(service, "historiqueRepository", mock(HistoriqueValidationRepository.class));
        ReflectionTestUtils.setField(service, "offreEmploiRepository", offreRepository);
        return service;
    }

    private DemandeRecrutement demande(StatutDemande statut) {
        Employe demandeur = new Employe();
        demandeur.setId("EMP001");
        Besoin besoin = new Besoin();
        besoin.setMotif("Besoin de renfort");

        DemandeRecrutement demande = new DemandeRecrutement();
        demande.setId(3L);
        demande.setDemandeur(demandeur);
        demande.setBesoin(besoin);
        demande.setDateArriveeSouhaitee(LocalDate.of(2026, 11, 15));
        demande.setStatut(statut);
        return demande;
    }

    private BesoinPoste ligne(Long id, String poste, String libellePropose) {
        BesoinPoste ligne = new BesoinPoste();
        ligne.setId(id);
        if (poste != null) {
            Poste p = new Poste();
            p.setLibelle(poste);
            ligne.setPoste(p);
        }
        ligne.setLibellePostePropose(libellePropose);
        return ligne;
    }

    private Utilisateur utilisateur(String role) {
        RoleUser roleUser = new RoleUser();
        roleUser.setRoleName(role);
        Employe employe = new Employe();
        employe.setId("VAL001");
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setRoleUser(roleUser);
        utilisateur.setEmploye(employe);
        return utilisateur;
    }
}
