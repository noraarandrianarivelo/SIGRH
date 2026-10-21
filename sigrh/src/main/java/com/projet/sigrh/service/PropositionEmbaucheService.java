package com.projet.sigrh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet.sigrh.dto.PropositionEmbaucheRequest;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.model.Employe;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.model.PropositionEmbauche;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.repository.CandidatureRepository;
import com.projet.sigrh.repository.OffreEmploiRepository;
import com.projet.sigrh.repository.PropositionEmbaucheRepository;
import com.projet.sigrh.repository.DemandeRecrutementRepository;
import com.projet.sigrh.repository.EmployeRepository;
import com.projet.sigrh.security.Roles;

@Service
public class PropositionEmbaucheService {
    private static final List<String> STATUTS_BLOQUANTS = List.of("EN_ATTENTE", "ACCEPTEE");

    private final PropositionEmbaucheRepository repository;
    private final DemandeRecrutementRepository demandeRepository;
    private final EmployeRepository employeRepository;
    private final CandidatureRepository candidatureRepository;
    private final OffreEmploiRepository offreRepository;

    public PropositionEmbaucheService(PropositionEmbaucheRepository repository,
            DemandeRecrutementRepository demandeRepository, EmployeRepository employeRepository,
            CandidatureRepository candidatureRepository, OffreEmploiRepository offreRepository) {
        this.repository = repository;
        this.demandeRepository = demandeRepository;
        this.employeRepository = employeRepository;
        this.candidatureRepository = candidatureRepository;
        this.offreRepository = offreRepository;
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

    @Transactional(readOnly = true)
    public List<PropositionEmbauche> propositionsDuCandidat(Candidat candidat) {
        if (candidat == null || candidat.id == null) {
            throw new BusinessException("Candidat non connecté.", HttpStatus.UNAUTHORIZED);
        }
        return repository.findByCandidatureCandidatIdOrderByDateEnvoiDesc(candidat.id);
    }

    @Transactional
    public void repondre(Integer propositionId, Candidat candidat, String statut, String message) {
        if (candidat == null || candidat.id == null) {
            throw new BusinessException("Candidat non connecté.", HttpStatus.UNAUTHORIZED);
        }
        PropositionEmbauche proposition = repository.findById(propositionId)
                .orElseThrow(() -> new BusinessException("Proposition introuvable.", HttpStatus.NOT_FOUND));
        if (proposition.candidature.candidat == null
                || !candidat.id.equals(proposition.candidature.candidat.id)) {
            throw new BusinessException("Cette proposition ne vous est pas destinée.", HttpStatus.FORBIDDEN);
        }
        if (!"EN_ATTENTE".equals(proposition.statut)) {
            throw new BusinessException("Cette proposition a déjà reçu une réponse.", HttpStatus.CONFLICT);
        }
        if (!"ACCEPTEE".equals(statut) && !"REFUSEE".equals(statut)) {
            throw new BusinessException("La réponse doit être ACCEPTEE ou REFUSEE.");
        }
        proposition.statut = statut;
        proposition.dateReponse = java.time.LocalDateTime.now();
        proposition.messageReponse = message == null || message.isBlank() ? null : message.trim();
        if ("ACCEPTEE".equals(statut)) {
            creerEmploye(proposition.candidature);
        } else {
            traiterRefus(proposition);
        }
        repository.save(proposition);
    }

    private void traiterRefus(PropositionEmbauche proposition) {
        Candidature candidatureRefusee = proposition.candidature;
        candidatureRefusee.statut = "REJETEE";
        candidatureRepository.save(candidatureRefusee);

        boolean reserveDisponible = !candidatureRepository
                .findByOffreIdAndStatutOrderByDateCandidatureAsc(candidatureRefusee.offre.id, "RESERVE")
                .isEmpty();
        boolean autrePropositionActive = repository.existsByCandidatureOffreIdAndStatutIn(
                candidatureRefusee.offre.id, List.of("EN_ATTENTE", "ACCEPTEE"));
        if (!reserveDisponible && !autrePropositionActive) {
            creerNouvelleOffre(candidatureRefusee.offre);
        }
    }

    private OffreEmploi creerNouvelleOffre(OffreEmploi ancienneOffre) {
        ancienneOffre.statut = "CLOTUREE";
        offreRepository.save(ancienneOffre);

        OffreEmploi nouvelleOffre = new OffreEmploi();
        nouvelleOffre.besoinPoste = ancienneOffre.besoinPoste;
        nouvelleOffre.titre = ancienneOffre.titre;
        nouvelleOffre.description = ancienneOffre.description;
        nouvelleOffre.dateCreation = LocalDate.now();
        nouvelleOffre.datePublication = LocalDate.now();
        nouvelleOffre.dateLimite = ancienneOffre.dateLimite;
        nouvelleOffre.statut = "OUVERT";
        return offreRepository.save(nouvelleOffre);
    }

    private Employe creerEmploye(Candidature candidature) {
        if (candidature.candidat == null || candidature.candidat.nom == null
                || candidature.candidat.nom.isBlank()) {
            throw new BusinessException("Le candidat doit avoir un nom pour devenir employé.");
        }

        Employe employe = new Employe();
        employe.setId(genererMatricule());
        employe.setNom(candidature.candidat.nom.trim());
        employe.setPrenom(texteOptionnel(candidature.candidat.prenom));
        if (candidature.offre != null && candidature.offre.besoinPoste != null
                && candidature.offre.besoinPoste.getPoste() != null) {
            employe.setDepartement(candidature.offre.besoinPoste.getPoste().getDepartement());
        }
        return employeRepository.save(employe);
    }

    private String genererMatricule() {
        for (int tentative = 0; tentative < 10; tentative++) {
            String matricule = "EMP" + String.format("%07d",
                    ThreadLocalRandom.current().nextInt(10_000_000));
            if (!employeRepository.existsById(matricule)) {
                return matricule;
            }
        }
        throw new BusinessException("Impossible de générer un matricule unique.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Transactional
    public PropositionEmbauche envoyer(Candidature candidature, PropositionEmbaucheRequest request,
            Utilisateur utilisateur) {
        verifierEmetteur(utilisateur);
        if (!"RETENUE".equals(candidature.statut) && !"RESERVE".equals(candidature.statut)) {
            throw new BusinessException("Une proposition ne peut être envoyée qu'à un candidat retenu ou en réserve.");
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
