package com.projet.sigrh.controller.RH;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.OffreEmploiService;

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

        String vue = new RHController(offreService, candidatureService).afficherOffre(7, model);

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
                () -> new RHController(offreService, candidatureService).afficherOffre(7, new ConcurrentModel()));

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

        String vue = new RHController(offreService, candidatureService).afficherCandidature(5, model);

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
                () -> new RHController(mock(OffreEmploiService.class), candidatureService)
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

        ResponseEntity<Resource> response = new RHController(offreService, candidatureService).afficherCv(5);

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

        ResponseEntity<Resource> response = new RHController(offreService, candidatureService)
                .afficherLettreMotivation(5);

        assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
    }
}
