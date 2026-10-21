package com.projet.sigrh.repository;

import com.projet.sigrh.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    // Permet de retrouver un utilisateur par son identifiant de connexion (username)
    Utilisateur findByUsername(String username);
}