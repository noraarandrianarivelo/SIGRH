package com.projet.sigrh.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.repository.CandidatureRepository;

class CandidatureServiceTests {

    @TempDir
    Path uploadDirectory;

    @Test
    void sauvegardeLesFichiersAvecUnNomUnique() throws IOException {
        CandidatureRepository repository = mock(CandidatureRepository.class);
        Candidat candidat = candidat(3);
        OffreEmploi offre = offreOuverte(7);
        when(repository.save(any(Candidature.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Candidature candidature = new CandidatureService(repository, uploadDirectory.toString()).creer(
                candidat,
                offre,
                new MockMultipartFile("cv", "mon cv.pdf", "application/pdf", "cv".getBytes()),
                new MockMultipartFile("lettre", "lettre.pdf", "application/pdf", "lettre".getBytes()),
                "Je souhaite rejoindre votre équipe.");

        assertTrue(candidature.cv.matches("[0-9a-f-]{36}-mon_cv\\.pdf"));
        assertTrue(candidature.lettreMotivation.matches("[0-9a-f-]{36}-lettre\\.pdf"));
        assertEquals("cv", Files.readString(uploadDirectory.resolve(candidature.cv)));
        assertEquals("lettre", Files.readString(uploadDirectory.resolve(candidature.lettreMotivation)));
        assertEquals("Je souhaite rejoindre votre équipe.", candidature.message);
    }

    @Test
    void refuseUnCvVide() {
        CandidatureService service = new CandidatureService(mock(CandidatureRepository.class), uploadDirectory.toString());

        assertThrows(BusinessException.class,
                () -> service.creer(candidat(3), offreOuverte(7),
                        new MockMultipartFile("cv", new byte[0]), null, null));
    }

    @Test
    void supprimeLeFichierSiLaBaseRefuseLaCandidature() throws IOException {
        CandidatureRepository repository = mock(CandidatureRepository.class);
        when(repository.save(any(Candidature.class))).thenThrow(new IllegalStateException("base indisponible"));
        CandidatureService service = new CandidatureService(repository, uploadDirectory.toString());

        assertThrows(IllegalStateException.class,
                () -> service.creer(candidat(3), offreOuverte(7),
                        new MockMultipartFile("cv", "cv.pdf", "application/pdf", "cv".getBytes()), null, null));

        try (var fichiers = Files.list(uploadDirectory)) {
            assertFalse(fichiers.findAny().isPresent());
        }
    }

    private Candidat candidat(Integer id) {
        Candidat candidat = new Candidat();
        candidat.id = id;
        return candidat;
    }

    private OffreEmploi offreOuverte(Integer id) {
        OffreEmploi offre = new OffreEmploi();
        offre.id = id;
        offre.statut = "OUVERT";
        return offre;
    }
}
