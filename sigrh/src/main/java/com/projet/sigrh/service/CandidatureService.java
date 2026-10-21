package com.projet.sigrh.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.repository.CandidatureRepository;

@Service
public class CandidatureService {
    private final CandidatureRepository repository;
    private final Path uploadDirectory;

    public CandidatureService(CandidatureRepository repository,
            @Value("${sigrh.candidature.upload-dir:uploads/candidatures}") String uploadDirectory) {
        this.repository = repository;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public List<Candidature> findByOffreId(Integer offreId) {
        return repository.findByOffreId(offreId);
    }

    public Optional<Candidature> findById(Integer id) {
        return repository.findById(id);
    }

    public Optional<Path> trouverFichier(String nom) {
        if (!StringUtils.hasText(nom)) {
            return Optional.empty();
        }
        Path fichier = uploadDirectory.resolve(nom).normalize();
        return fichier.startsWith(uploadDirectory) && Files.isRegularFile(fichier)
                ? Optional.of(fichier)
                : Optional.empty();
    }

    public boolean estAffichable(String nom) {
        if (nom == null) {
            return false;
        }
        String nomMinuscule = nom.toLowerCase();
        return nomMinuscule.endsWith(".pdf")
                || nomMinuscule.endsWith(".png")
                || nomMinuscule.endsWith(".jpg")
                || nomMinuscule.endsWith(".jpeg")
                || nomMinuscule.endsWith(".gif")
                || nomMinuscule.endsWith(".webp")
                || nomMinuscule.endsWith(".txt");
    }

    public Candidature creer(Candidat candidat, OffreEmploi offre, MultipartFile cv,
            MultipartFile lettreEncouragement, String message) {
        if (cv == null || cv.isEmpty()) {
            throw new BusinessException("Le CV est obligatoire");
        }
        if (!"OUVERT".equals(offre.statut)) {
            throw new BusinessException("Cette offre n'est plus ouverte");
        }
        if (repository.existsByCandidatIdAndOffreId(candidat.id, offre.id)) {
            throw new BusinessException("Vous avez déjà candidaté à cette offre", HttpStatus.CONFLICT);
        }

        List<Path> fichiersCrees = new ArrayList<>();
        try {
            Files.createDirectories(uploadDirectory);
            String nomCv = sauvegarder(cv, fichiersCrees);
            String nomLettre = lettreEncouragement == null || lettreEncouragement.isEmpty()
                    ? null
                    : sauvegarder(lettreEncouragement, fichiersCrees);

            Candidature candidature = new Candidature();
            candidature.candidat = candidat;
            candidature.offre = offre;
            candidature.cv = nomCv;
            candidature.lettreMotivation = nomLettre;
            candidature.message = message;
            return repository.save(candidature);
        } catch (IOException exception) {
            supprimer(fichiersCrees);
            throw new BusinessException("Impossible d'enregistrer les fichiers", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RuntimeException exception) {
            supprimer(fichiersCrees);
            throw exception;
        }
    }

    private String sauvegarder(MultipartFile fichier, List<Path> fichiersCrees) throws IOException {
        String nomOriginal = StringUtils.cleanPath(Optional.ofNullable(fichier.getOriginalFilename()).orElse("fichier"));
        if (nomOriginal.contains("..")) {
            throw new BusinessException("Nom de fichier invalide");
        }

        String nomSecurise = nomOriginal.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        String nomStocke = UUID.randomUUID() + "-" + (nomSecurise.isBlank() ? "fichier" : nomSecurise);
        Path destination = uploadDirectory.resolve(nomStocke);
        try (InputStream contenu = fichier.getInputStream()) {
            Files.copy(contenu, destination);
        }
        fichiersCrees.add(destination);
        return nomStocke;
    }

    private void supprimer(List<Path> fichiers) {
        // shortcut: database and filesystem writes are not atomic, use transactional object storage if uploads become critical.
        for (Path fichier : fichiers) {
            try {
                Files.deleteIfExists(fichier);
            } catch (IOException ignored) {
                // Le fichier orphelin pourra être supprimé manuellement sans perte de candidature.
            }
        }
    }
}
