package com.projet.sigrh.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.web.csrf.CsrfToken;
import com.projet.sigrh.controller.LoginController;
import com.projet.sigrh.repository.CandidatRepository;

@SpringBootTest(properties = {"spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="sigrh.bloc4.integration", matches="true")
@Transactional
class NavigationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired CandidatRepository candidats;

    @Test void loginPersonnaliseEtRessourcesSontAccessiblesSansSession() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("method=\"post\"")));
        for (String path : List.of("/css/recrutement.css", "/js/application.js", "/images/icons.svg", "/fonts/InterVariable.woff2")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test void mauvaisMotDePasseConserveIdentifiantMaisAucunSecret() throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).param("username", "b4_rh").param("password", "secret-errone"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertEquals("b4_rh", session.getAttribute(LoginController.LAST_USERNAME));
        mvc.perform(get("/login").param("error", "").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("incorrect")))
                .andExpect(content().string(containsString("value=\"b4_rh\"")))
                .andExpect(content().string(not(containsString("secret-errone"))));
    }

    @Test void connexionValideDeconnexionEtMessage() throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).param("username", "b4_rh").param("password", "Bloc4Test2026!"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("b4_rh")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("Vos espaces RH")));
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        mvc.perform(get("/login").param("logout", "")).andExpect(content().string(containsString("déconnecté")));
    }

    @Test void connexionExigeUnJetonEtLesChampsVidesSontRefuses() throws Exception {
        mvc.perform(post("/login").param("username", "b4_rh").param("password", "Bloc4Test2026!"))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
        mvc.perform(post("/login").with(csrf()).param("username", "").param("password", ""))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test void profilCandidatAbsentEstDeconnecteProprement() throws Exception {
        db.update("""
                INSERT INTO utilisateur(username,password,id_role)
                SELECT 'profil-incomplet',u.password,r.id FROM utilisateur u CROSS JOIN role_user r
                WHERE u.username='b4_rh' AND r.role_name='CANDIDAT'
                """);
        mvc.perform(post("/login").with(csrf()).param("username", "profil-incomplet").param("password", "Bloc4Test2026!"))
                .andExpect(redirectedUrl("/login?profile")).andExpect(unauthenticated());
        mvc.perform(get("/login").param("profile", "")).andExpect(content().string(containsString("doit être complété")));
    }

    @Test void menuCommunEtListesRendentTousLesEcransRh() throws Exception {
        for (String path : List.of("/", "/demandes", "/rh/offres", "/rh/candidatures", "/rh/propositions", "/rh/evaluations",
                "/personnel", "/personnel/contrats", "/personnel/affectations", "/personnel/integration",
                "/personnel/catalogues", "/personnel/taches", "/personnel/historique", "/modules/besoins",
                "/modules/recrutement", "/modules/embauche", "/modules/personnel")) {
            mvc.perform(get(path).with(user("b4_rh").authorities(() -> "ROLE_RH"))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"app-sidebar\"")))
                    .andExpect(content().string(containsString("id=\"main-content\"")))
                    .andExpect(content().string(containsString("Besoins en personnel")))
                    .andExpect(content().string(containsString("Recrutement")))
                    .andExpect(content().string(containsString("Embauche")));
        }
    }

    @Test void managerDecouvreLesModulesSansVoirLesDonneesDesAutres() throws Exception {
        mvc.perform(get("/modules/recrutement").with(user("b4_it").authorities(() -> "ROLE_MANAGER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Accès : RH ou Direction")))
                .andExpect(content().string(not(containsString("salaire_mensuel"))))
                .andExpect(content().string(not(containsString("B4N002"))));
        mvc.perform(get("/rh/candidatures").with(user("b4_it").authorities(() -> "ROLE_MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/personnel/contrats").with(user("b4_it").authorities(() -> "ROLE_MANAGER")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("Rija"))));
        mvc.perform(get("/personnel/historique").with(user("b4_paie").authorities(() -> "ROLE_FINANCE"))).andExpect(status().isForbidden());
    }

    @Test void candidatConserveSesEcransEtLaNavigationCommune() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute("candidatConnecte", candidats.findByEmail("candidat1@mail.com").orElseThrow());
        for (String path : List.of("/", "/candidat/offre-emploi", "/candidat/propositions", "/modules/personnel")) {
            mvc.perform(get(path).session(session).with(user("candidat1@mail.com").authorities(() -> "CANDIDAT")))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Mon espace candidat")));
        }
        mvc.perform(get("/rh/offres").with(user("candidat1@mail.com").authorities(() -> "CANDIDAT"))).andExpect(status().isForbidden());
    }

    @Test void fetchDemandesUtiliseLeJetonExposeParLaPage() throws Exception {
        var result = mvc.perform(get("/demandes").with(user("b4_it").authorities(() -> "ROLE_MANAGER")))
                .andExpect(status().isOk()).andReturn();
        var token = (CsrfToken) result.getRequest().getAttribute("_csrf");
        int dept = db.queryForObject("SELECT id_departement FROM employe WHERE id='B4IT001'", Integer.class);
        int poste = db.queryForObject("SELECT id FROM poste WHERE id_departement=? ORDER BY id LIMIT 1", Integer.class, dept);
        String payload = "{\"departementId\":"+dept+",\"typeMotif\":\"NOUVEAU_POSTE\",\"motif\":\"Validation du formulaire commun\",\"lignes\":[{\"posteId\":"+poste+",\"effectif\":1,\"budgetMensuelUnitaire\":800000}]}";
        mvc.perform(post("/api/recrutement/demandes").session((MockHttpSession)result.getRequest().getSession(false))
                .with(user("b4_it").authorities(() -> "ROLE_MANAGER")).header(token.getHeaderName(), token.getToken())
                .contentType("application/json").content(payload)).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/recrutement/demandes").with(user("b4_it").authorities(() -> "ROLE_MANAGER"))
                .contentType("application/json").content(payload)).andExpect(status().isForbidden());
    }
}
