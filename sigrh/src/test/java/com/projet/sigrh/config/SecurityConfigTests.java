package com.projet.sigrh.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.repository.CandidatRepository;
import com.projet.sigrh.security.Roles;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

class SecurityConfigTests {

    @Test
    void connecteLeCandidatEtLeRedirigeVersLesOffres() throws Exception {
        CandidatRepository repository = mock(CandidatRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Candidat candidat = new Candidat();
        when(repository.findByEmail("candidat1@mail.com")).thenReturn(Optional.of(candidat));
        when(request.getSession()).thenReturn(session);

        new SecurityConfig(repository).redirigerApresConnexion(request, response,
                new TestingAuthenticationToken("candidat1@mail.com", null, Roles.CANDIDAT));

        verify(session).setAttribute("candidatConnecte", candidat);
        verify(response).sendRedirect("/candidat/offre-emploi");
    }

    @Test
    void redirigeLesEmployesVersLaPagePrincipale() throws Exception {
        CandidatRepository repository = mock(CandidatRepository.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        new SecurityConfig(repository).redirigerApresConnexion(mock(HttpServletRequest.class), response,
                new TestingAuthenticationToken("rh", null, Roles.RH));

        verify(response).sendRedirect("/index.html");
        verifyNoInteractions(repository);
    }

    @Test
    void refuseUneSessionCandidateSansCandidatAssocie() {
        CandidatRepository repository = mock(CandidatRepository.class);
        when(repository.findByEmail("inconnu@mail.com")).thenReturn(Optional.empty());

        assertThrows(ServletException.class,
                () -> new SecurityConfig(repository).redirigerApresConnexion(
                        mock(HttpServletRequest.class), mock(HttpServletResponse.class),
                        new TestingAuthenticationToken("inconnu@mail.com", null, Roles.CANDIDAT)));
    }
}
