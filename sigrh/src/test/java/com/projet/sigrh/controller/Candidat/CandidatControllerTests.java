package com.projet.sigrh.controller.Candidat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.service.OffreEmploiService;

class CandidatControllerTests {

    @Test
    void afficheLeDetailDeLOffre() {
        OffreEmploiService service = mock(OffreEmploiService.class);
        OffreEmploi offre = new OffreEmploi();
        ConcurrentModel model = new ConcurrentModel();
        when(service.findById(7)).thenReturn(Optional.of(offre));

        String vue = new CandidatController(service).afficherOffre(7, model);

        assertEquals("Candidat/offre-detail", vue);
        assertSame(offre, model.get("offre"));
    }

    @Test
    void retourne404QuandLOffreNExistePas() {
        OffreEmploiService service = mock(OffreEmploiService.class);
        when(service.findById(7)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new CandidatController(service).afficherOffre(7, new ConcurrentModel()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
