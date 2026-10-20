package com.projet.sigrh.security;

import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;

@Configuration
public class SecurityConfig {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsService() {
            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                Utilisateur utilisateur = utilisateurRepository.findByUsername(username);
                if (utilisateur == null) {
                    throw new UsernameNotFoundException("Utilisateur non trouvé : " + username);
                }

                // Récupère le rôle depuis l'entité (ex: "ROLE_RH", "ROLE_MANAGER", etc.)
                String role = utilisateur.getRole();
                if (role == null || role.isEmpty()) {
                    role = "ROLE_USER";
                }

                return new User(
                    utilisateur.getUsername(),
                    utilisateur.getPassword(), // Doit contenir {noop}password123 en base
                    Collections.singletonList(new SimpleGrantedAuthority(role))
                );
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Permet d'accepter les mots de passe préfixés par {noop} en clair
        return NoOpPasswordEncoder.getInstance();
    }
}