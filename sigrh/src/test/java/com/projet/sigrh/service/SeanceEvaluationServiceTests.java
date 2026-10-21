package com.projet.sigrh.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.Employe;
import com.projet.sigrh.model.FicheNoteEvaluation;
import com.projet.sigrh.model.SeanceEvaluation;
import com.projet.sigrh.model.TestRecrutement;
import com.projet.sigrh.repository.EmployeRepository;
import com.projet.sigrh.repository.FicheNoteEvaluationRepository;
import com.projet.sigrh.repository.SeanceEvaluationRepository;
import com.projet.sigrh.repository.TestRecrutementRepository;

class SeanceEvaluationServiceTests {

    @Test
    void enregistreUnEntretienEtUnTest() {
        SeanceEvaluationRepository repository = mock(SeanceEvaluationRepository.class);
        EmployeRepository employeRepository = mock(EmployeRepository.class);
        TestRecrutementRepository testRepository = mock(TestRecrutementRepository.class);
        FicheNoteEvaluationRepository ficheRepository = mock(FicheNoteEvaluationRepository.class);
        Employe evaluateur = new Employe();
        TestRecrutement test = new TestRecrutement();
        Candidature candidature = new Candidature();
        LocalDateTime dateEntretien = LocalDateTime.of(2026, 10, 25, 9, 0);
        LocalDateTime dateTest = LocalDateTime.of(2026, 10, 25, 10, 0);
        when(employeRepository.findById("EV001")).thenReturn(Optional.of(evaluateur));
        when(testRepository.findById(2)).thenReturn(Optional.of(test));
        when(repository.save(any(SeanceEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeanceEvaluation seance = new SeanceEvaluationService(
                repository, employeRepository, testRepository, ficheRepository)
                .creer(candidature, "EV001", 2, dateEntretien, dateTest,
                        "Salle A", "Laboratoire", "PLANIFIEE");

        assertSame(candidature, seance.candidature);
        assertSame(evaluateur, seance.evaluateur);
        assertSame(test, seance.test);
        assertEquals(dateEntretien, seance.dateEntretien);
        assertEquals(dateTest, seance.dateTest);
        verify(repository).save(seance);
    }

    @Test
    void refuseUneSeanceSansDate() {
        SeanceEvaluationService service = new SeanceEvaluationService(
                mock(SeanceEvaluationRepository.class), mock(EmployeRepository.class),
                mock(TestRecrutementRepository.class), mock(FicheNoteEvaluationRepository.class));

        assertThrows(BusinessException.class,
                () -> service.creer(new Candidature(), "EV001", null,
                        null, null, null, null, "PLANIFIEE"));
    }

    @Test
    void refuseUnTestSansDateDeTest() {
        SeanceEvaluationService service = new SeanceEvaluationService(
                mock(SeanceEvaluationRepository.class), mock(EmployeRepository.class),
                mock(TestRecrutementRepository.class), mock(FicheNoteEvaluationRepository.class));

        assertThrows(BusinessException.class,
                () -> service.creer(new Candidature(), "EV001", 2,
                        LocalDateTime.now(), null, "Salle A", null, "PLANIFIEE"));
    }

    @Test
    void enregistreLaFicheDeNotes() {
        SeanceEvaluationRepository repository = mock(SeanceEvaluationRepository.class);
        FicheNoteEvaluationRepository ficheRepository = mock(FicheNoteEvaluationRepository.class);
        SeanceEvaluation seance = new SeanceEvaluation();
        when(repository.findById(4)).thenReturn(Optional.of(seance));
        when(ficheRepository.save(any(FicheNoteEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SeanceEvaluationService service = new SeanceEvaluationService(
                repository, mock(EmployeRepository.class), mock(TestRecrutementRepository.class), ficheRepository);

        FicheNoteEvaluation fiche = service.evaluer(4, new BigDecimal("15.50"),
                new BigDecimal("17.25"), "  Bon profil  ", "FAVORABLE");

        assertSame(seance, fiche.seance);
        assertEquals(new BigDecimal("15.50"), fiche.noteEntretien);
        assertEquals(new BigDecimal("17.25"), fiche.noteTest);
        assertEquals("Bon profil", fiche.commentaire);
        assertEquals("FAVORABLE", fiche.avis);
        verify(ficheRepository).save(fiche);
    }

    @Test
    void refuseUneNoteHorsBareme() {
        SeanceEvaluationService service = new SeanceEvaluationService(
                mock(SeanceEvaluationRepository.class), mock(EmployeRepository.class),
                mock(TestRecrutementRepository.class), mock(FicheNoteEvaluationRepository.class));

        assertThrows(BusinessException.class,
                () -> service.evaluer(4, new BigDecimal("20.01"), null, null, "FAVORABLE"));
    }
}
