package com.projet.sigrh.config;

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
import com.projet.sigrh.controller.LoginController;

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
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(mock(HttpSession.class));

        new SecurityConfig(repository).redirigerApresConnexion(request, response,
                new TestingAuthenticationToken("rh", null, Roles.RH));

        verify(response).sendRedirect("/");
        verifyNoInteractions(repository);
    }

    @Test
    void fermeUneSessionCandidateSansProfilEtAfficheUnMessage() throws Exception {
        CandidatRepository repository = mock(CandidatRepository.class);
        when(repository.findByEmail("inconnu@mail.com")).thenReturn(Optional.empty());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);

        new SecurityConfig(repository).redirigerApresConnexion(request, response,
                new TestingAuthenticationToken("inconnu@mail.com", null, Roles.CANDIDAT));
        verify(session).invalidate();
        verify(response).sendRedirect("/login?profile");
    }

    @Test
    void echecConserveSeulementIdentifiantEtMessageGenerique() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
        when(request.getParameter("username")).thenReturn("  compte-test  ");

        new SecurityConfig(mock(CandidatRepository.class)).echecConnexion(request, response,
                new org.springframework.security.authentication.BadCredentialsException("Détail interne"));
        verify(session).setAttribute(LoginController.LAST_USERNAME, "compte-test");
        verify(session).setAttribute(LoginController.ERROR_KIND, "credentials");
        verify(response).sendRedirect("/login?error");
    }
}
