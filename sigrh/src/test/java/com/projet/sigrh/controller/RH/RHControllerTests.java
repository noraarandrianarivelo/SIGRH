package com.projet.sigrh.controller.RH;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.OffreEmploiService;

class RHControllerTests {

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
}
