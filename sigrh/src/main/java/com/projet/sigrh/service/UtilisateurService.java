package com.projet.sigrh.service;

import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;

/** Charge le compte pour la connexion et retrouve l'utilisateur connecté. */
@Service
public class UtilisateurService implements UserDetailsService {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur u = utilisateurRepository.findByUsername(username);
        if (u == null) {
            throw new UsernameNotFoundException("Utilisateur non trouvé : " + username);
        }
        return User.withUsername(u.getUsername())
                .password(u.getPassword())      // mot de passe haché BCrypt
                .authorities(u.getRole())       // ex. ROLE_RH
                .build();
    }

    public Utilisateur getConnecte(Principal principal) {
        if (principal == null) {
            throw new BusinessException("Non connecté.", HttpStatus.UNAUTHORIZED);
        }
        Utilisateur u = utilisateurRepository.findByUsername(principal.getName());
        if (u == null) {
            throw new BusinessException("Utilisateur introuvable.", HttpStatus.UNAUTHORIZED);
        }
        return u;
    }
}