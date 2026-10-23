package com.projet.sigrh.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.projet.sigrh.model.*;

class NavigationServiceTests {
    final NavigationService service = new NavigationService();

    Utilisateur utilisateur(String role, boolean salarie) {
        var u = new Utilisateur();
        u.setUsername("collaborateur");
        var r = new RoleUser();
        r.setRoleName(role);
        u.setRoleUser(r);
        if (salarie) {
            var e = new Employe();
            e.setId("EMP-T");
            e.setNom("Rakoto");
            e.setPrenom("Ravo");
            u.setEmploye(e);
        }
        return u;
    }

    @Test void managerDecouvreTousLesBlocsSansRecevoirLeursDroits() {
        var nav = service.construire(utilisateur("ROLE_MANAGER", true), "/modules/recrutement");
        assertTrue(nav.groups().stream().map(NavigationService.Group::key).toList()
                .containsAll(List.of("besoins", "recrutement", "embauche", "personnel")));
        var offres = nav.groups().stream().flatMap(g -> g.items().stream()).filter(i -> i.key().equals("offres")).findFirst().orElseThrow();
        assertFalse(offres.available());
        assertEquals("/modules/recrutement#offres", offres.href());
        assertEquals("recrutement", nav.activeGroup());
    }

    @Test void rhRetrouveLesEcransEtLeContexteDesFormulaires() {
        var nav = service.construire(utilisateur("ROLE_RH", true), "/rh/candidatures/42/proposition");
        assertEquals("embauche", nav.activeGroup());
        assertEquals("Propositions d’embauche", nav.title());
        assertTrue(nav.groups().stream().flatMap(g -> g.items().stream()).allMatch(NavigationService.Item::available));
        assertEquals("RR", nav.initials());
    }

    @Test void directionNeRecoitPasLesCataloguesNiLeJournalRh() {
        var nav = service.construire(utilisateur("ROLE_DIRECTION", true), "/personnel/taches/42");
        assertEquals("Mes tâches", nav.title());
        assertTrue(nav.groups().stream().flatMap(g -> g.items().stream())
                .filter(i -> List.of("catalogues", "historique").contains(i.key())).noneMatch(NavigationService.Item::available));
    }

    @Test void candidatDisposeDeSonEspaceEtDesGuidesSansAccesInterne() {
        var nav = service.construire(utilisateur("CANDIDAT", false), "/candidat/propositions");
        assertTrue(nav.candidate());
        assertEquals("Mon espace candidat", nav.section());
        assertTrue(nav.groups().stream().filter(g -> g.key().equals("candidat")).flatMap(g -> g.items().stream()).allMatch(NavigationService.Item::available));
        assertTrue(nav.groups().stream().filter(g -> !g.number().isBlank()).flatMap(g -> g.items().stream()).noneMatch(NavigationService.Item::available));
    }
}
