package com.projet.sigrh.controller.Candidat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.OffreEmploiService;

import jakarta.servlet.http.HttpSession;

class CandidatControllerTests {

    @Test
    void afficheLeDetailDeLOffre() {
        OffreEmploiService service = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        OffreEmploi offre = new OffreEmploi();
        ConcurrentModel model = new ConcurrentModel();
        when(service.findById(7)).thenReturn(Optional.of(offre));

        String vue = new CandidatController(service, candidatureService).afficherOffre(7, model);

        assertEquals("Candidat/offre-detail", vue);
        assertSame(offre, model.get("offre"));
    }

    @Test
    void retourne404QuandLOffreNExistePas() {
        OffreEmploiService service = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        when(service.findById(7)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new CandidatController(service, candidatureService).afficherOffre(7, new ConcurrentModel()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void envoieLaCandidatureDuCandidatConnecte() {
        OffreEmploiService offreService = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        HttpSession session = mock(HttpSession.class);
        MultipartFile cv = mock(MultipartFile.class);
        Candidat candidat = new Candidat();
        OffreEmploi offre = new OffreEmploi();
        when(session.getAttribute("candidatConnecte")).thenReturn(candidat);
        when(offreService.findById(7)).thenReturn(Optional.of(offre));

        String vue = new CandidatController(offreService, candidatureService)
                .envoyerCandidature(7, cv, null, "Je souhaite rejoindre votre équipe.", session);

        assertEquals("redirect:/candidat/offre-emploi/7?candidature=envoyee", vue);
        verify(candidatureService).creer(candidat, offre, cv, null, "Je souhaite rejoindre votre équipe.");
    }

    @Test
    void refuseLaCandidatureSansCandidatEnSession() {
        OffreEmploiService offreService = mock(OffreEmploiService.class);
        CandidatureService candidatureService = mock(CandidatureService.class);
        OffreEmploi offre = new OffreEmploi();
        when(offreService.findById(7)).thenReturn(Optional.of(offre));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new CandidatController(offreService, candidatureService)
                        .envoyerCandidature(7, mock(MultipartFile.class), null, null, mock(HttpSession.class)));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }
}
