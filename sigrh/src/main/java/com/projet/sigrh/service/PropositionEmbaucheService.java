package com.projet.sigrh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet.sigrh.dto.PropositionEmbaucheRequest;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.PropositionEmbauche;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.repository.PropositionEmbaucheRepository;
import com.projet.sigrh.repository.DemandeRecrutementRepository;
import com.projet.sigrh.security.Roles;

@Service
public class PropositionEmbaucheService {
    private static final List<String> STATUTS_BLOQUANTS = List.of("EN_ATTENTE", "ACCEPTEE");

    private final PropositionEmbaucheRepository repository;
    private final DemandeRecrutementRepository demandeRepository;

    public PropositionEmbaucheService(PropositionEmbaucheRepository repository,
            DemandeRecrutementRepository demandeRepository) {
        this.repository = repository;
        this.demandeRepository = demandeRepository;
    }

    public BigDecimal salaireInitial(Candidature candidature) {
        return candidature.offre.besoinPoste.getBudgetMensuelUnitaire();
    }

    public LocalDate dateDebutInitiale(Candidature candidature) {
        return demandeRepository.findByBesoinId(candidature.offre.besoinPoste.getBesoin().getId())
                .map(demande -> demande.getDateArriveeSouhaitee())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<PropositionEmbauche> propositionActive(Integer candidatureId) {
        return repository.findFirstByCandidatureIdAndStatutInOrderByDateEnvoiDesc(candidatureId, STATUTS_BLOQUANTS);
    }

    @Transactional
    public PropositionEmbauche envoyer(Candidature candidature, PropositionEmbaucheRequest request,
            Utilisateur utilisateur) {
        verifierEmetteur(utilisateur);
        if (!"RETENUE".equals(candidature.statut)) {
            throw new BusinessException("Une proposition ne peut être envoyée qu'à un candidat retenu.");
        }
        if (request == null) {
            throw new BusinessException("Les informations de la proposition sont obligatoires.");
        }
        verifier(request);
        if (propositionActive(candidature.id).isPresent()) {
            throw new BusinessException("Une proposition est déjà en attente ou acceptée pour cette candidature.",
                    HttpStatus.CONFLICT);
        }

        PropositionEmbauche proposition = new PropositionEmbauche();
        proposition.candidature = candidature;
        proposition.emetteur = utilisateur.getEmploye();
        proposition.salaireMensuel = request.salaireMensuel();
        proposition.dateDebutPrevue = request.dateDebutPrevue();
        proposition.typeContrat = request.typeContrat().trim();
        proposition.horaires = texteOptionnel(request.horaires());
        proposition.lieuTravail = texteOptionnel(request.lieuTravail());
        proposition.autresConditions = texteOptionnel(request.autresConditions());
        return repository.save(proposition);
    }

    private void verifier(PropositionEmbaucheRequest request) {
        if (request.salaireMensuel() == null || request.salaireMensuel().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Le salaire mensuel doit être renseigné et positif.");
        }
        if (request.dateDebutPrevue() == null) {
            throw new BusinessException("La date de début prévue est obligatoire.");
        }
        if (request.typeContrat() == null || request.typeContrat().isBlank()) {
            throw new BusinessException("Le type de contrat est obligatoire.");
        }
    }

    private void verifierEmetteur(Utilisateur utilisateur) {
        if (utilisateur == null
                || (!Roles.RH.equals(utilisateur.getRole()) && !Roles.DIRECTION.equals(utilisateur.getRole()))) {
            throw new BusinessException("Seuls la RH et la Direction peuvent envoyer une proposition.",
                    HttpStatus.FORBIDDEN);
        }
        if (utilisateur.getEmploye() == null) {
            throw new BusinessException("Le compte émetteur n'est lié à aucun employé.", HttpStatus.FORBIDDEN);
        }
    }

    private String texteOptionnel(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
