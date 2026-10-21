package com.projet.sigrh.config;

import java.io.IOException;

import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.repository.CandidatRepository;
import com.projet.sigrh.security.Roles;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
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
            // Les appels fetch() de la page n'envoient pas de jeton CSRF : sans cette ligne,
            // tous les POST/PUT sont refusés en 403.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Données de référence : seule la RH (ou la Direction) peut en ajouter.
                .requestMatchers(HttpMethod.POST, "/api/postes", "/api/departements", "/api/grilles-salaires")
                    .hasAnyAuthority(Roles.RH, Roles.DIRECTION)
                .requestMatchers("/api/recrutement/demandes/*/decision")
                    .hasAnyAuthority(Roles.RH, Roles.FINANCE, Roles.DIRECTION)
                .anyRequest().authenticated())
            .formLogin(form -> form.successHandler(this::redirigerApresConnexion))
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    void redirigerApresConnexion(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        boolean estCandidat = authentication.getAuthorities().stream()
                .anyMatch(authority -> Roles.CANDIDAT.equals(authority.getAuthority()));

        if (!estCandidat) {
            response.sendRedirect("/index.html");
            return;
        }

        Candidat candidat = candidatRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ServletException("Aucun candidat associé à cet e-mail"));
        request.getSession().setAttribute("candidatConnecte", candidat);
        response.sendRedirect("/candidat/offre-emploi");
    }
}
