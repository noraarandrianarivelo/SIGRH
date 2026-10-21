package com.projet.sigrh.controller.RH;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.Employe;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.model.SeanceEvaluation;
import com.projet.sigrh.model.TestRecrutement;
import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.OffreEmploiService;
import com.projet.sigrh.service.SeanceEvaluationService;

class RHControllerTests {

    @TempDir
    Path uploadDirectory;

    @Test
    void afficheLOffreEtSesCandidatures() {
        OffreEmploiService offreService = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        OffreEmploi offre = new OffreEmploi();
        List<Candidature> candidatures = List.of(new Candidature());
        ConcurrentModel model = new ConcurrentModel();
        when(offreService.findById(7)).thenReturn(Optional.of(offre));
        when(candidatureService.findByOffreId(7)).thenReturn(candidatures);

        String vue = controller(offreService, candidatureService).afficherOffre(7, model);

        assertEquals("RH/offre-detail", vue);
        assertSame(offre, model.get("offre"));
        assertSame(candidatures, model.get("candidatures"));
    }

    @Test
    void retourne404QuandLOffreNExistePas() {
        OffreEmploiService offreService = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        when(offreService.findById(7)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller(offreService, candidatureService).afficherOffre(7, new ConcurrentModel()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void afficheLeDetailDeLaCandidature() {
        OffreEmploiService offreService = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        Candidature candidature = new Candidature();
        candidature.cv = "cv.pdf";
        candidature.lettreMotivation = "lettre.docx";
        ConcurrentModel model = new ConcurrentModel();
        when(candidatureService.findById(5)).thenReturn(Optional.of(candidature));
        when(candidatureService.estAffichable("cv.pdf")).thenReturn(true);

        String vue = controller(offreService, candidatureService).afficherCandidature(5, model);

        assertEquals("RH/candidature-detail", vue);
        assertSame(candidature, model.get("candidature"));
        assertEquals(true, model.get("cvAffichable"));
        assertEquals(false, model.get("lettreAffichable"));
    }

    @Test
    void retourne404QuandLaCandidatureNExistePas() {
        CandidatureService candidatureService = mock(CandidatureService.class);
        when(candidatureService.findById(5)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller(mock(OffreEmploiService.class), candidatureService)
                        .afficherCandidature(5, new ConcurrentModel()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void afficheUnPdfDansLeNavigateur() throws IOException {
        OffreEmploiService offreService = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        Candidature candidature = new Candidature();
        candidature.cv = "cv.pdf";
        Path fichier = Files.writeString(uploadDirectory.resolve("cv.pdf"), "pdf");
        when(candidatureService.findById(5)).thenReturn(Optional.of(candidature));
        when(candidatureService.trouverFichier("cv.pdf")).thenReturn(Optional.of(fichier));
        when(candidatureService.estAffichable("cv.pdf")).thenReturn(true);

        ResponseEntity<Resource> response = controller(offreService, candidatureService).afficherCv(5);

        assertEquals("inline", response.getHeaders().getContentDisposition().getType());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void telechargeUnFormatNonAffichable() throws IOException {
        OffreEmploiService offreService = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        Candidature candidature = new Candidature();
        candidature.lettreMotivation = "lettre.docx";
        Path fichier = Files.writeString(uploadDirectory.resolve("lettre.docx"), "document");
        when(candidatureService.findById(5)).thenReturn(Optional.of(candidature));
        when(candidatureService.trouverFichier("lettre.docx")).thenReturn(Optional.of(fichier));

        ResponseEntity<Resource> response = controller(offreService, candidatureService)
                .afficherLettreMotivation(5);

        assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
    }

    @Test
    void prepareLeFormulaireEvaluation() {
        CandidatureService candidatureService = mock(CandidatureService.class);
        SeanceEvaluationService evaluationService = mock(SeanceEvaluationService.class);
        Candidature candidature = new Candidature();
        List<Employe> evaluateurs = List.of(new Employe());
        List<TestRecrutement> tests = List.of(new TestRecrutement());
        ConcurrentModel model = new ConcurrentModel();
        when(candidatureService.findById(5)).thenReturn(Optional.of(candidature));
        when(evaluationService.listerEvaluateurs()).thenReturn(evaluateurs);
        when(evaluationService.listerTests()).thenReturn(tests);

        String vue = new RHController(mock(OffreEmploiService.class), candidatureService, evaluationService)
                .afficherFormulaireEvaluation(5, model);

        assertEquals("RH/seance-evaluation-form", vue);
        assertSame(candidature, model.get("candidature"));
        assertSame(evaluateurs, model.get("evaluateurs"));
        assertSame(tests, model.get("tests"));
    }

    @Test
    void enregistreLaSeanceEvaluation() {
        CandidatureService candidatureService = mock(CandidatureService.class);
        SeanceEvaluationService evaluationService = mock(SeanceEvaluationService.class);
        Candidature candidature = new Candidature();
        LocalDateTime dateEntretien = LocalDateTime.of(2026, 10, 25, 9, 0);
        LocalDateTime dateTest = LocalDateTime.of(2026, 10, 25, 10, 0);
        when(candidatureService.findById(5)).thenReturn(Optional.of(candidature));

        String vue = new RHController(mock(OffreEmploiService.class), candidatureService, evaluationService)
                .arrangerEvaluation(5, "EV001", 2, dateEntretien, dateTest,
                        "Salle A", "Laboratoire", "PLANIFIEE");

        assertEquals("redirect:/rh/candidatures/5?evaluation=planifiee", vue);
        verify(evaluationService).creer(candidature, "EV001", 2, dateEntretien, dateTest,
                "Salle A", "Laboratoire", "PLANIFIEE");
    }

    @Test
    void afficheLaListeDesSeancesEvaluation() {
        SeanceEvaluationService evaluationService = mock(SeanceEvaluationService.class);
        List<SeanceEvaluation> seances = List.of(new SeanceEvaluation());
        ConcurrentModel model = new ConcurrentModel();
        when(evaluationService.findAll()).thenReturn(seances);

        String vue = new RHController(mock(OffreEmploiService.class), mock(CandidatureService.class),
                evaluationService).afficherEvaluations(model);

        assertEquals("RH/seance-evaluation-list", vue);
        assertSame(seances, model.get("seances"));
    }

    @Test
    void afficheLeDetailDuneSeanceEvaluation() {
        SeanceEvaluationService evaluationService = mock(SeanceEvaluationService.class);
        SeanceEvaluation seance = new SeanceEvaluation();
        ConcurrentModel model = new ConcurrentModel();
        when(evaluationService.findById(4)).thenReturn(Optional.of(seance));
        when(evaluationService.estEvaluee(4)).thenReturn(true);

        String vue = new RHController(mock(OffreEmploiService.class), mock(CandidatureService.class),
                evaluationService).afficherEvaluation(4, model);

        assertEquals("RH/seance-evaluation-detail", vue);
        assertSame(seance, model.get("seance"));
        assertEquals(true, model.get("dejaEvaluee"));
    }

    @Test
    void retourne404QuandLaSeanceEvaluationNExistePas() {
        SeanceEvaluationService evaluationService = mock(SeanceEvaluationService.class);
        when(evaluationService.findById(4)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new RHController(mock(OffreEmploiService.class), mock(CandidatureService.class),
                        evaluationService).afficherEvaluation(4, new ConcurrentModel()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void afficheLeFormulaireDeNotation() {
        SeanceEvaluationService evaluationService = mock(SeanceEvaluationService.class);
        SeanceEvaluation seance = new SeanceEvaluation();
        ConcurrentModel model = new ConcurrentModel();
        when(evaluationService.findById(4)).thenReturn(Optional.of(seance));

        String vue = new RHController(mock(OffreEmploiService.class), mock(CandidatureService.class),
                evaluationService).afficherFormulaireNotation(4, model);

        assertEquals("RH/fiche-note-evaluation-form", vue);
        assertSame(seance, model.get("seance"));
    }

    @Test
    void enregistreLaFicheDeNotation() {
        SeanceEvaluationService evaluationService = mock(SeanceEvaluationService.class);
        BigDecimal noteEntretien = new BigDecimal("15.50");
        BigDecimal noteTest = new BigDecimal("17.25");

        String vue = new RHController(mock(OffreEmploiService.class), mock(CandidatureService.class),
                evaluationService).evaluer(4, noteEntretien, noteTest, "Bon profil", "FAVORABLE");

        assertEquals("redirect:/rh/evaluations/4?note=enregistree", vue);
        verify(evaluationService).evaluer(4, noteEntretien, noteTest, "Bon profil", "FAVORABLE");
    }

    private RHController controller(OffreEmploiService offreService, CandidatureService candidatureService) {
        return new RHController(offreService, candidatureService, mock(SeanceEvaluationService.class));
    }
}
