package com.projet.sigrh.config;

import java.io.IOException;

import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.controller.LoginController;
import com.projet.sigrh.repository.CandidatRepository;
import com.projet.sigrh.security.Roles;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final CandidatRepository candidatRepository;

    public SecurityConfig(CandidatRepository candidatRepository) {
        this.candidatRepository = candidatRepository;
    }

    // Les mots de passe en base sont des hachés BCrypt ($2a$10$...). Voir sql/donnees_test.sql.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Tous les formulaires et les appels fetch envoient désormais un jeton CSRF.
            .csrf(csrf -> {})
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            .exceptionHandling(exceptions -> exceptions.accessDeniedPage("/acces-refuse"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/fonts/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/candidat/**").hasAuthority(Roles.CANDIDAT)
                .requestMatchers("/rh/**").hasAnyAuthority(Roles.RH, Roles.DIRECTION)
                // Données de référence : seule la RH (ou la Direction) peut en ajouter.
                .requestMatchers(HttpMethod.POST, "/api/postes", "/api/departements", "/api/grilles-salaires")
                    .hasAnyAuthority(Roles.RH, Roles.DIRECTION)
                .requestMatchers("/api/recrutement/demandes/*/decision")
                    .hasAnyAuthority(Roles.RH, Roles.FINANCE, Roles.DIRECTION)
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(this::redirigerApresConnexion)
                .failureHandler(this::echecConnexion)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                .clearAuthentication(true));
        return http.build();
    }

    void redirigerApresConnexion(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        boolean estCandidat = authentication.getAuthorities().stream()
                .anyMatch(authority -> Roles.CANDIDAT.equals(authority.getAuthority()));

        request.getSession().removeAttribute(LoginController.LAST_USERNAME);
        request.getSession().removeAttribute(LoginController.ERROR_KIND);
        if (!estCandidat) {
            response.sendRedirect("/");
            return;
        }

        Candidat candidat = candidatRepository.findByEmail(authentication.getName()).orElse(null);
        if (candidat == null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect("/login?profile");
            return;
        }
        request.getSession().setAttribute("candidatConnecte", candidat);
        response.sendRedirect("/candidat/offre-emploi");
    }

    void echecConnexion(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String username = request.getParameter("username");
        request.getSession().setAttribute(LoginController.LAST_USERNAME,
                username == null ? "" : username.trim().substring(0, Math.min(username.trim().length(), 150)));
        request.getSession().setAttribute(LoginController.ERROR_KIND,
                exception instanceof AuthenticationServiceException ? "service" : "credentials");
        // Aucun détail d'exception, mot de passe ou existence de compte n'est envoyé au navigateur.
        response.sendRedirect("/login?error");
    }
}
