package com.projet.sigrh.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet.sigrh.dto.DemandeRequest;
import com.projet.sigrh.dto.DemandeRequest.LigneRequest;
import com.projet.sigrh.dto.DemandeResponse;
import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Besoin;
import com.projet.sigrh.model.BesoinPoste;
import com.projet.sigrh.model.DemandeRecrutement;
import com.projet.sigrh.model.Departement;
import com.projet.sigrh.model.GrilleSalaire;
import com.projet.sigrh.model.Poste;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.model.enums.Circuit;
import com.projet.sigrh.model.enums.EtapeValidation;
import com.projet.sigrh.model.enums.StatutDemande;
import com.projet.sigrh.repository.DemandeRecrutementRepository;
import com.projet.sigrh.repository.DepartementRepository;
import com.projet.sigrh.repository.GrilleSalaireRepository;
import com.projet.sigrh.repository.HistoriqueValidationRepository;
import com.projet.sigrh.repository.PosteRepository;
import com.projet.sigrh.security.Roles;

/** Bloc 1 : création, modification, soumission et consultation des demandes de recrutement. */
@Service
public class DemandeRecrutementService {

    @Autowired
    private DemandeRecrutementRepository demandeRepository;
    @Autowired
    private DepartementRepository departementRepository;
    @Autowired
    private PosteRepository posteRepository;
    @Autowired
    private GrilleSalaireRepository grilleSalaireRepository;
    @Autowired
    private HistoriqueValidationRepository historiqueRepository;

    // sigrh.validation-finance dans application.properties : contrôle Finance activé ou non.
    @Value("${sigrh.validation-finance:true}")
    private boolean financeActive;

    // ---------- Consultation ----------

    @Transactional(readOnly = true)
    public List<DemandeResponse> lister(Utilisateur u) {
        List<DemandeRecrutement> demandes;
        switch (u.getRole()) {
            case Roles.MANAGER -> demandes = demandeRepository.findByDemandeurIdOrderByIdDesc(idEmploye(u));
            case Roles.RH, Roles.FINANCE, Roles.DIRECTION ->
                    demandes = demandeRepository.findVisibles(StatutDemande.BROUILLON, idEmploye(u));
            default -> demandes = List.of();
        }
        return demandes.stream().map(d -> toResponse(d, u)).toList();
    }

    @Transactional(readOnly = true)
    public DemandeResponse detail(Long id, Utilisateur u) {
        DemandeRecrutement d = trouver(id);
        boolean estDemandeur = estDemandeur(d, u);
        boolean autorise = switch (u.getRole()) {
            case Roles.MANAGER -> estDemandeur;
            case Roles.RH, Roles.FINANCE, Roles.DIRECTION -> estDemandeur || d.getStatut() != StatutDemande.BROUILLON;
            default -> false;
        };
        if (!autorise) {
            throw new BusinessException("Vous n'avez pas accès à cette demande.", HttpStatus.FORBIDDEN);
        }
        return toResponse(d, u);
    }

    // ---------- Manager : création, modification, soumission ----------

    @Transactional
    public DemandeResponse creer(DemandeRequest req, Utilisateur u) {
        verifierDroitDeCreation(u, req.departementId());

        DemandeRecrutement d = new DemandeRecrutement();
        d.setDemandeur(u.getEmploye());
        d.setStatut(StatutDemande.BROUILLON);
        d.setCircuit(Roles.DIRECTION.equals(u.getRole()) ? Circuit.DIRECTION_DIRECTE : Circuit.STANDARD);
        d.setValidationFinance(financeActive);
        remplir(d, req);

        return toResponse(demandeRepository.save(d), u);
    }

    @Transactional
    public DemandeResponse modifier(Long id, DemandeRequest req, Utilisateur u) {
        DemandeRecrutement d = trouver(id);
        verifierDemandeurModifiable(d, u);
        verifierDroitDeCreation(u, req.departementId());

        remplir(d, req);
        return toResponse(demandeRepository.save(d), u);
    }

    @Transactional
    public DemandeResponse soumettre(Long id, Utilisateur u) {
        DemandeRecrutement d = trouver(id);
        verifierDemandeurModifiable(d, u);

        d.setStatut(WorkflowDemande.statutApresSoumission(d));
        return toResponse(demandeRepository.save(d), u);
    }

    // ---------- Conversion en DTO (utilisée aussi par ValidationService) ----------

    public DemandeResponse toResponse(DemandeRecrutement d, Utilisateur u) {
        Besoin b = d.getBesoin();

        List<DemandeResponse.Ligne> lignes = b.getLignes().stream().map(this::toLigne).toList();

        List<DemandeResponse.Historique> historique = historiqueRepository
                .findByIdDemandeOrderByDateValidationAsc(d.getId()).stream()
                .map(h -> new DemandeResponse.Historique(
                        h.getEtape().name(), h.getDecision().name(), h.getValidateur().getNomComplet(),
                        h.getDateValidation(), h.getCommentaire()))
                .toList();

        EtapeValidation etape = WorkflowDemande.etapeCourante(d.getStatut());
        List<String> decisions = WorkflowDemande.peutDecider(d, u)
                ? WorkflowDemande.decisionsPossibles(etape).stream().map(Enum::name).toList()
                : List.of();

        return new DemandeResponse(
                d.getId(), d.getDemandeur().getNomComplet(),
                b.getDepartement().getId(), b.getDepartement().getLibelle(),
                b.getTypeMotif(), b.getMotif(),
                d.getDateCreation(), d.getDateArriveeSouhaitee(),
                d.getCircuit(), d.isValidationFinance(), d.getStatut(),
                lignes, historique,
                WorkflowDemande.peutModifier(d, u), decisions);
    }

    private DemandeResponse.Ligne toLigne(BesoinPoste bp) {
        GrilleSalaire g = bp.getPoste() == null ? null
                : grilleSalaireRepository.findByPosteId(bp.getPoste().getId()).orElse(null);
        boolean conforme = g == null
                || (bp.getBudgetMensuelUnitaire().compareTo(g.getSalaireMin()) >= 0
                    && bp.getBudgetMensuelUnitaire().compareTo(g.getSalaireMax()) <= 0);
        return new DemandeResponse.Ligne(
                bp.getId(),
                bp.getPoste() == null ? null : bp.getPoste().getId(),
                bp.getPoste() == null ? bp.getLibellePostePropose() : bp.getPoste().getLibelle(),
                bp.getPoste() == null,
                bp.getEffectif(), bp.getBudgetMensuelUnitaire(),
                g == null ? null : g.getSalaireMin(), g == null ? null : g.getSalaireMax(),
                conforme);
    }

    // ---------- Règles de gestion ----------

    public DemandeRecrutement trouver(Long id) {
        return demandeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Demande introuvable.", HttpStatus.NOT_FOUND));
    }

    /** RG Manager : rôle MANAGER ou DIRECTION ; le manager ne crée que pour son département. */
    private void verifierDroitDeCreation(Utilisateur u, Long departementId) {
        boolean manager = Roles.MANAGER.equals(u.getRole());
        boolean direction = Roles.DIRECTION.equals(u.getRole());
        if (!manager && !direction) {
            throw new BusinessException("Seul un manager ou la direction peut initier une demande.", HttpStatus.FORBIDDEN);
        }
        if (u.getEmploye() == null) {
            throw new BusinessException("Ce compte n'est lié à aucun employé.", HttpStatus.FORBIDDEN);
        }
        if (manager) {
            Departement dep = u.getEmploye().getDepartement();
            if (dep == null || !dep.getId().equals(departementId)) {
                throw new BusinessException("Un manager ne peut créer une demande que pour son propre département.", HttpStatus.FORBIDDEN);
            }
        }
    }

    private void verifierDemandeurModifiable(DemandeRecrutement d, Utilisateur u) {
        if (!estDemandeur(d, u)) {
            throw new BusinessException("Seul le demandeur peut modifier ou soumettre sa demande.", HttpStatus.FORBIDDEN);
        }
        if (!d.getStatut().isModifiable()) {
            throw new BusinessException("Une demande n'est modifiable qu'au statut BROUILLON ou A_CORRIGER.");
        }
    }

    private boolean estDemandeur(DemandeRecrutement d, Utilisateur u) {
        return u.getEmploye() != null && d.getDemandeur().getId().equals(u.getEmploye().getId());
    }

    private String idEmploye(Utilisateur u) {
        return u.getEmploye() == null ? "" : u.getEmploye().getId();
    }

    /** Copie la requête dans la demande (besoin + lignes) après avoir tout contrôlé. */
    private void remplir(DemandeRecrutement d, DemandeRequest req) {
        if (req.departementId() == null) {
            throw new BusinessException("Le département est obligatoire.");
        }
        Departement dep = departementRepository.findById(req.departementId())
                .orElseThrow(() -> new BusinessException("Département introuvable.", HttpStatus.NOT_FOUND));
        if (req.typeMotif() == null) {
            throw new BusinessException("Le type de motif est obligatoire.");
        }
        if (req.motif() == null || req.motif().isBlank()) {
            throw new BusinessException("Le motif est obligatoire.");
        }
        if (req.dateArriveeSouhaitee() != null && req.dateArriveeSouhaitee().isBefore(d.getDateCreation())) {
            throw new BusinessException("La date d'arrivée souhaitée ne peut pas être avant la date de création.");
        }

        List<BesoinPoste> lignes = new ArrayList<>();
        Set<Long> postesVus = new HashSet<>();
        int numero = 0;
        if (req.lignes() != null) {
            for (LigneRequest l : req.lignes()) {
                numero++;
                lignes.add(construireLigne(numero, l, dep, postesVus));
            }
        }
        if (lignes.isEmpty()) {
            throw new BusinessException("Ajoutez au moins un poste au besoin.");
        }

        Besoin besoin = d.getBesoin();
        if (besoin == null) {
            besoin = new Besoin();
            d.setBesoin(besoin);
        } else {
            // On supprime les anciennes lignes en base avant d'insérer les nouvelles,
            // sinon la contrainte UNIQUE (id_besoin, id_poste) peut être violée.
            besoin.getLignes().clear();
            demandeRepository.flush();
        }
        besoin.setDepartement(dep);
        besoin.setTypeMotif(req.typeMotif());
        besoin.setMotif(req.motif().trim());
        for (BesoinPoste bp : lignes) {
            bp.setBesoin(besoin);
            besoin.getLignes().add(bp);
        }
        d.setDateArriveeSouhaitee(req.dateArriveeSouhaitee());
    }

    private BesoinPoste construireLigne(int numero, LigneRequest l, Departement dep, Set<Long> postesVus) {
        BesoinPoste bp = new BesoinPoste();
        String prefixe = "Ligne " + numero + " : ";

        if (l.posteId() != null) {
            Poste p = posteRepository.findById(l.posteId())
                    .orElseThrow(() -> new BusinessException(prefixe + "poste introuvable.", HttpStatus.NOT_FOUND));
            if (!p.getDepartement().getId().equals(dep.getId())) {
                throw new BusinessException(prefixe + "le poste « " + p.getLibelle() + " » n'appartient pas au département choisi.");
            }
            if (!postesVus.add(p.getId())) {
                throw new BusinessException(prefixe + "le poste « " + p.getLibelle() + " » est déjà dans ce besoin.");
            }
            bp.setPoste(p);
        } else if (l.libellePostePropose() == null || l.libellePostePropose().isBlank()) {
            throw new BusinessException(prefixe + "choisissez un poste existant ou proposez un nouveau poste.");
        } else {
            bp.setLibellePostePropose(l.libellePostePropose().trim());
        }

        if (l.effectif() == null || l.effectif() <= 0) {
            throw new BusinessException(prefixe + "l'effectif doit être supérieur à 0.");
        }
        if (l.budgetMensuelUnitaire() == null || l.budgetMensuelUnitaire().signum() < 0) {
            throw new BusinessException(prefixe + "le budget mensuel unitaire est obligatoire (>= 0).");
        }
        bp.setEffectif(l.effectif());
        bp.setBudgetMensuelUnitaire(l.budgetMensuelUnitaire());
        return bp;
    }
}