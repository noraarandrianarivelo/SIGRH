/*
SIGRH — Schéma harmonisé des modules 1, 2 et 3
Source : « Regrouped conception(1).md » et workflow fourni.

INSTALLATION
  Script d'initialisation PostgreSQL, sans extension, à exécuter UNE FOIS dans
  une base existante où le schéma sigrh n'existe pas encore.
  Exemple : psql -v ON_ERROR_STOP=1 -d votre_base -f SIGRH_modules_1_2_3_postgresql.sql
  Aucun DROP, aucune suppression de données. Toute erreur annule la transaction.
  Les noms SQL sont en minuscules snake_case, sans guillemets obligatoires.
  Après installation : SELECT * FROM sigrh.offre_emploi;

PERIMETRE
  M1 : besoin, postes concernés, demande et historique de validation.
  M2 : offres, candidatures, séances, tests et fiches de notes.
  M3 : propositions d'embauche successives et réponse du candidat.
  Contrat, matricule, dossier salarié et affectations seront ajoutés ensuite.
  La table employe représente ici les salariés DEJA présents, dont les acteurs
  de validation et les évaluateurs. Accepter une proposition ne crée aucun salarié.

CHOIX DE CONCEPTION
  1. DemandeCreationEmploi et DemandeRecrutement deviennent une seule table :
     demande_recrutement. Le motif couvre la création et le remplacement.
  2. Un besoin appartient à un département et contient plusieurs lignes
     besoin_poste. L'effectif et le budget unitaire sont portés par ces lignes.
     Exemple : 2 chauffeurs + 1 comptable = effectif total 3.
     Le total est calculé dans une vue ; il n'est pas stocké une deuxième fois.
  3. Un poste désigne ici une fonction dans un département, pas une personne.
     Deux chauffeurs du même département peuvent donc partager le même id_poste.
  4. Une demande concerne un besoin ; un besoin a au maximum une demande.
     Une offre concerne une ligne besoin_poste, au maximum une offre par ligne.
     Son poste et sa demande se retrouvent par jointure, sans les dupliquer.
     La vue v_offre_recrutement expose directement tous ces identifiants.
  5. Une séance concerne une candidature, un évaluateur et au maximum un test.
     On conserve date_entretien ET date_test : une ligne représente une étape
     pouvant regrouper deux rendez-vous. Au moins une date doit être renseignée.
  6. Les niveaux 1 à 3 sont ceux des tests. Le passage obligatoire par les trois
     niveaux n'est pas imposé : cette règle reste à décider dans l'application.
  7. Une fiche par séance ; notes sur 20. NULL = pas encore de note ou épreuve
     absente. Zéro = note réelle. Il n'y a pas de moyenne automatique.
  8. Les propositions sont versionnées par candidature. Après un refus, on crée
     une nouvelle version ; on conserve la précédente et sa réponse.
  9. Hypothèse monétaire : budgets unitaires et salaires sont mensuels BRUTS.
     Devise par défaut MGA (ariary). Aucune conversion de devise n'est implicite.
 10. Les dates de rendez-vous/envoi sont TIMESTAMPTZ. Exemple :
     '2026-10-25 09:00:00+03'. L'affichage dépend du fuseau de la connexion.

IMPORTANT : ce fichier crée les tables et les contraintes de données. Les droits
des utilisateurs et les transitions métier sont à implémenter dans les services
de l'application ; leur liste précise figure à la fin. Les statuts ne changent
pas seuls, les notifications ne sont pas envoyées par ce script.
*/

BEGIN;

CREATE SCHEMA sigrh;
SET LOCAL search_path TO sigrh, public;

-- ============================================================================
-- 0. REFERENTIELS COMMUNS ET EMPLOYES DEJA PRESENTS
-- ============================================================================

CREATE TABLE departement (
    id_departement BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    libelle VARCHAR(150) NOT NULL UNIQUE,
    CONSTRAINT ck_departement_libelle CHECK (btrim(libelle) <> '')
);

CREATE TABLE poste (
    id_poste BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_departement BIGINT NOT NULL REFERENCES departement(id_departement),
    libelle VARCHAR(150) NOT NULL,
    description TEXT,
    CONSTRAINT uq_poste_departement_libelle UNIQUE (id_departement, libelle),
    -- Clé composée utilisée pour contrôler le département dans besoin_poste.
    CONSTRAINT uq_poste_departement UNIQUE (id_poste, id_departement),
    CONSTRAINT ck_poste_libelle CHECK (btrim(libelle) <> '')
);

CREATE TABLE employe (
    id_employe BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(150),
    email VARCHAR(254),
    id_departement BIGINT REFERENCES departement(id_departement),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_employe_nom CHECK (btrim(nom) <> '')
);

COMMENT ON TABLE employe IS
    'Salariés existants : demandeurs, validateurs, évaluateurs, auteurs. Aucun employé créé automatiquement après acceptation.';
COMMENT ON COLUMN employe.id_departement IS
    'Rattachement actuel minimal pour le workflow. Le futur module affectation apportera son historique.';

-- Grille retenue PAR POSTE, comme dans la proposition du document.
-- Une nouvelle grille peut être ajoutée sans écraser les anciennes références.
CREATE TABLE grille_salaire (
    id_grille_salaire BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_poste BIGINT NOT NULL REFERENCES poste(id_poste),
    libelle VARCHAR(150) NOT NULL,
    salaire_min NUMERIC(14,2) NOT NULL,
    salaire_max NUMERIC(14,2) NOT NULL,
    devise VARCHAR(3) NOT NULL DEFAULT 'MGA',
    date_debut DATE NOT NULL,
    date_fin DATE,
    CONSTRAINT uq_grille_poste UNIQUE (id_grille_salaire, id_poste),
    CONSTRAINT ck_grille_montants CHECK (
        salaire_min >= 0 AND salaire_max >= salaire_min
        AND salaire_max < 'Infinity'::NUMERIC
    ),
    CONSTRAINT ck_grille_devise CHECK (devise ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_grille_dates CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

COMMENT ON TABLE grille_salaire IS
    'Fourchette de salaire mensuel brut par poste ; valeur1/valeur2 deviennent salaire_min/salaire_max.';

-- ============================================================================
-- 1. BESOIN, DEMANDE DE RECRUTEMENT ET VALIDATIONS
-- ============================================================================

CREATE TABLE besoin (
    id_besoin BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_departement BIGINT NOT NULL REFERENCES departement(id_departement),
    motif TEXT NOT NULL,
    devise VARCHAR(3) NOT NULL DEFAULT 'MGA',
    date_creation TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_besoin_departement UNIQUE (id_besoin, id_departement),
    CONSTRAINT ck_besoin_motif CHECK (btrim(motif) <> ''),
    CONSTRAINT ck_besoin_devise CHECK (devise ~ '^[A-Z]{3}$')
);

CREATE TABLE besoin_poste (
    id_besoin_poste BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_besoin BIGINT NOT NULL,
    id_poste BIGINT NOT NULL,
    -- Répétition contrôlée : les DEUX FK ci-dessous imposent le même département.
    id_departement BIGINT NOT NULL,
    effectif INTEGER NOT NULL,
    budget_mensuel_unitaire NUMERIC(14,2),
    id_grille_salaire BIGINT,
    CONSTRAINT uq_besoin_poste UNIQUE (id_besoin, id_poste),
    CONSTRAINT fk_besoin_poste_besoin FOREIGN KEY (id_besoin, id_departement)
        REFERENCES besoin(id_besoin, id_departement),
    CONSTRAINT fk_besoin_poste_poste FOREIGN KEY (id_poste, id_departement)
        REFERENCES poste(id_poste, id_departement),
    -- Une grille renseignée doit appartenir au poste de cette ligne.
    CONSTRAINT fk_besoin_poste_grille FOREIGN KEY (id_grille_salaire, id_poste)
        REFERENCES grille_salaire(id_grille_salaire, id_poste),
    CONSTRAINT ck_besoin_poste_effectif CHECK (effectif > 0),
    CONSTRAINT ck_besoin_poste_budget CHECK (
        budget_mensuel_unitaire >= 0
        AND budget_mensuel_unitaire < 'Infinity'::NUMERIC
    )
);

COMMENT ON TABLE besoin_poste IS
    'Une ligne par poste demandé : effectif et budget unitaire. Le budget est exprimé dans la devise du besoin.';
COMMENT ON COLUMN besoin_poste.budget_mensuel_unitaire IS
    'Budget salarial mensuel brut par personne ; NULL si encore inconnu au brouillon, à compléter avant validation.';

CREATE TABLE demande_recrutement (
    id_demande_recrutement BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_besoin BIGINT NOT NULL UNIQUE REFERENCES besoin(id_besoin),
    id_demandeur BIGINT NOT NULL REFERENCES employe(id_employe),
    date_creation TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_soumission TIMESTAMPTZ,
    date_arrivee_souhaitee DATE NOT NULL,
    circuit_validation VARCHAR(25) NOT NULL DEFAULT 'STANDARD',
    controle_finance_requis BOOLEAN NOT NULL DEFAULT FALSE,
    statut VARCHAR(40) NOT NULL DEFAULT 'BROUILLON',
    CONSTRAINT ck_demande_circuit CHECK (
        circuit_validation IN ('STANDARD', 'DIRECTION_DIRECTE')
    ),
    CONSTRAINT ck_demande_statut CHECK (statut IN (
        'BROUILLON', 'A_CORRIGER', 'EN_ATTENTE_VALIDATION_RH',
        'EN_ATTENTE_VALIDATION_FINANCE', 'EN_ATTENTE_VALIDATION_DIRECTION',
        'REJETEE_RH', 'REJETEE_FINANCE', 'REJETEE_DIRECTION', 'VALIDEE'
    )),
    CONSTRAINT ck_demande_soumission CHECK (
        statut = 'BROUILLON' OR date_soumission IS NOT NULL
    ),
    CONSTRAINT ck_demande_dates CHECK (
        date_soumission IS NULL OR date_soumission >= date_creation
    )
);

COMMENT ON TABLE demande_recrutement IS
    'Demande du bloc 1 ; remplace aussi DemandeCreationEmploi du bloc 2. Un besoin correspond au maximum à une demande.';
COMMENT ON COLUMN demande_recrutement.circuit_validation IS
    'STANDARD : RH puis Finance éventuelle puis Direction. DIRECTION_DIRECTE : départ à Finance si requise, sinon Direction ; pas de passage RH.';

CREATE TABLE historique_validation (
    id_historique_validation BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_demande_recrutement BIGINT NOT NULL
        REFERENCES demande_recrutement(id_demande_recrutement),
    id_acteur BIGINT NOT NULL REFERENCES employe(id_employe),
    etape VARCHAR(20) NOT NULL,
    action VARCHAR(25) NOT NULL,
    statut_avant VARCHAR(40) NOT NULL,
    statut_apres VARCHAR(40) NOT NULL,
    commentaire TEXT,
    date_action TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_validation_etape CHECK (
        etape IN ('SOUMISSION', 'RH', 'FINANCE', 'DIRECTION')
    ),
    CONSTRAINT ck_validation_action CHECK (action IN (
        'SOUMISE', 'RESOUMISE', 'APPROUVEE', 'REJETEE', 'CORRECTION_DEMANDEE'
    )),
    CONSTRAINT ck_validation_motif CHECK (
        action NOT IN ('REJETEE', 'CORRECTION_DEMANDEE')
        OR (commentaire IS NOT NULL AND btrim(commentaire) <> '')
    )
);

COMMENT ON TABLE historique_validation IS
    'Journal des décisions avec acteur et motif. Le service écrit ici dans la même transaction que le changement de statut de la demande.';

-- ============================================================================
-- 2. OFFRES, CANDIDATS ET CANDIDATURES
-- ============================================================================

-- Identifiants de référence fixes ; le code est à utiliser dans la logique métier.
CREATE TABLE statut_offre_emploi (
    id_statut_offre SMALLINT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL
);

INSERT INTO statut_offre_emploi (id_statut_offre, code, libelle) VALUES
    (1, 'BROUILLON', 'Brouillon'),
    (2, 'OUVERTE', 'Ouverte aux candidatures'),
    (3, 'CLOTUREE', 'Clôturée'),
    (4, 'ANNULEE', 'Annulée');

CREATE TABLE offre_emploi (
    id_offre_emploi BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_besoin_poste BIGINT NOT NULL UNIQUE REFERENCES besoin_poste(id_besoin_poste),
    id_createur BIGINT NOT NULL REFERENCES employe(id_employe),
    titre VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    profil_recherche TEXT,
    lieu_travail VARCHAR(200),
    date_creation TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_publication TIMESTAMPTZ,
    date_limite_candidature TIMESTAMPTZ,
    date_cloture TIMESTAMPTZ,
    id_statut_offre SMALLINT NOT NULL DEFAULT 1
        REFERENCES statut_offre_emploi(id_statut_offre),
    CONSTRAINT ck_offre_titre CHECK (btrim(titre) <> ''),
    CONSTRAINT ck_offre_description CHECK (btrim(description) <> ''),
    CONSTRAINT ck_offre_publication CHECK (
        id_statut_offre NOT IN (2, 3) OR date_publication IS NOT NULL
    ),
    CONSTRAINT ck_offre_limite CHECK (
        date_limite_candidature IS NULL OR date_publication IS NULL
        OR date_limite_candidature >= date_publication
    ),
    CONSTRAINT ck_offre_cloture CHECK (
        (id_statut_offre <> 3 OR date_cloture IS NOT NULL)
        AND (date_cloture IS NULL OR date_publication IS NULL
             OR date_cloture >= date_publication)
    )
);

COMMENT ON TABLE offre_emploi IS
    'Offre pour une ligne besoin_poste. Le poste, le besoin et la demande sont accessibles via v_offre_recrutement. Création réservée aux demandes validées par le service.';

CREATE TABLE candidat (
    id_candidat BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(150),
    date_naissance DATE,
    email VARCHAR(254) NOT NULL,
    telephone VARCHAR(30),
    qualifications TEXT,
    date_creation TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_candidat_nom CHECK (btrim(nom) <> ''),
    CONSTRAINT ck_candidat_email CHECK (btrim(email) <> '')
);

-- Hypothèse simple : une adresse email identifie un profil candidat.
CREATE UNIQUE INDEX uq_candidat_email ON candidat (lower(btrim(email)));

CREATE TABLE candidature (
    id_candidature BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_candidat BIGINT NOT NULL REFERENCES candidat(id_candidat),
    id_offre_emploi BIGINT NOT NULL REFERENCES offre_emploi(id_offre_emploi),
    cv_fichier TEXT NOT NULL,
    lettre_motivation_fichier TEXT,
    message TEXT,
    date_soumission TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    statut VARCHAR(25) NOT NULL DEFAULT 'RECUE',
    id_decideur BIGINT REFERENCES employe(id_employe),
    date_decision TIMESTAMPTZ,
    motif_decision TEXT,
    CONSTRAINT uq_candidature_candidat_offre UNIQUE (id_candidat, id_offre_emploi),
    CONSTRAINT ck_candidature_cv CHECK (btrim(cv_fichier) <> ''),
    CONSTRAINT ck_candidature_statut CHECK (statut IN (
        'RECUE', 'EN_EXAMEN', 'PRESELECTIONNEE', 'EN_EVALUATION',
        'RETENUE', 'REJETEE', 'RETIREE'
    )),
    CONSTRAINT ck_candidature_decision CHECK (
        (statut IN ('RETENUE', 'REJETEE')
            AND id_decideur IS NOT NULL AND date_decision IS NOT NULL)
        OR
        (statut NOT IN ('RETENUE', 'REJETEE')
            AND id_decideur IS NULL AND date_decision IS NULL)
    ),
    CONSTRAINT ck_candidature_dates CHECK (
        date_decision IS NULL OR date_decision >= date_soumission
    )
);

COMMENT ON TABLE candidature IS
    'Dossier d un candidat pour une offre. RETENUE autorise une proposition ; la réponse à la proposition se trouve dans proposition_embauche.';
COMMENT ON COLUMN candidature.cv_fichier IS
    'Clé de stockage unique du CV soumis, par exemple candidatures/123/cv_uuid.pdf ; pas seulement CV.pdf et pas le contenu binaire.';

-- ============================================================================
-- 2 (SUITE). TESTS, SEANCES ET FICHES DE NOTES
-- ============================================================================

CREATE TABLE statut_seance_evaluation (
    id_statut_seance SMALLINT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    libelle VARCHAR(100) NOT NULL
);

INSERT INTO statut_seance_evaluation (id_statut_seance, code, libelle) VALUES
    (1, 'PLANIFIEE', 'Planifiée'),
    (2, 'EN_COURS', 'En cours'),
    (3, 'TERMINEE', 'Terminée'),
    (4, 'ANNULEE', 'Annulée'),
    (5, 'ABSENT', 'Candidat absent');

CREATE TABLE test_recrutement (
    id_test BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    libelle VARCHAR(150) NOT NULL,
    niveau SMALLINT NOT NULL,
    description TEXT NOT NULL,
    id_responsable BIGINT NOT NULL REFERENCES employe(id_employe),
    sujet_fichier TEXT,
    duree_minutes INTEGER,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_test_niveau CHECK (niveau BETWEEN 1 AND 3),
    CONSTRAINT ck_test_duree CHECK (duree_minutes > 0)
);

COMMENT ON TABLE test_recrutement IS
    'Définition réutilisable d un test de niveau 1 à 3. Les résultats individuels restent dans les fiches des séances.';
COMMENT ON COLUMN test_recrutement.id_responsable IS
    'Employé responsable du contenu du test ; peut différer de l évaluateur désigné pour une séance.';

CREATE TABLE seance_evaluation (
    id_seance_evaluation BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_candidature BIGINT NOT NULL REFERENCES candidature(id_candidature),
    id_evaluateur BIGINT NOT NULL REFERENCES employe(id_employe),
    date_entretien TIMESTAMPTZ,
    date_test TIMESTAMPTZ,
    id_test BIGINT REFERENCES test_recrutement(id_test),
    lieu_entretien TEXT,
    lieu_test TEXT,
    consignes TEXT,
    id_statut_seance SMALLINT NOT NULL DEFAULT 1
        REFERENCES statut_seance_evaluation(id_statut_seance),
    CONSTRAINT ck_seance_au_moins_une_epreuve CHECK (
        date_entretien IS NOT NULL OR date_test IS NOT NULL
    ),
    CONSTRAINT ck_seance_test_et_date CHECK (
        (id_test IS NULL AND date_test IS NULL)
        OR (id_test IS NOT NULL AND date_test IS NOT NULL)
    )
);

COMMENT ON TABLE seance_evaluation IS
    'Une étape pour UNE candidature et UN évaluateur : entretien seul, test seul ou les deux, éventuellement à des dates différentes.';
COMMENT ON COLUMN seance_evaluation.id_candidature IS
    'Le candidat et l offre se déduisent de cette candidature ; ne pas recopier id_candidat ici.';
COMMENT ON COLUMN seance_evaluation.lieu_entretien IS
    'Salle, adresse ou lien de visioconférence pour l entretien ; lieu_test sert au rendez-vous du test.';

CREATE TABLE fiche_note_evaluation (
    id_fiche_note_evaluation BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_seance_evaluation BIGINT NOT NULL UNIQUE
        REFERENCES seance_evaluation(id_seance_evaluation),
    id_saisi_par BIGINT NOT NULL REFERENCES employe(id_employe),
    note_entretien NUMERIC(4,2),
    note_test NUMERIC(4,2),
    commentaire_entretien TEXT,
    commentaire_test TEXT,
    avis VARCHAR(15),
    statut VARCHAR(15) NOT NULL DEFAULT 'BROUILLON',
    date_saisie TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_finalisation TIMESTAMPTZ,
    CONSTRAINT ck_fiche_note_entretien CHECK (note_entretien BETWEEN 0 AND 20),
    CONSTRAINT ck_fiche_note_test CHECK (note_test BETWEEN 0 AND 20),
    CONSTRAINT ck_fiche_avis CHECK (avis IN ('FAVORABLE', 'RESERVE', 'DEFAVORABLE')),
    CONSTRAINT ck_fiche_statut CHECK (statut IN ('BROUILLON', 'FINALISEE')),
    CONSTRAINT ck_fiche_finalisation CHECK (
        (statut = 'BROUILLON' AND date_finalisation IS NULL)
        OR (statut = 'FINALISEE' AND date_finalisation IS NOT NULL
            AND avis IS NOT NULL
            AND (note_entretien IS NOT NULL OR note_test IS NOT NULL))
    ),
    CONSTRAINT ck_fiche_dates CHECK (
        date_finalisation IS NULL OR date_finalisation >= date_saisie
    )
);

COMMENT ON TABLE fiche_note_evaluation IS
    'Au maximum une fiche par séance, notes sur 20. Une fiche brouillon peut être partielle. L avis ne remplace pas la décision finale RH.';
COMMENT ON COLUMN fiche_note_evaluation.id_saisi_par IS
    'Employé ayant saisi la fiche, par exemple le RH. L évaluateur métier reste seance_evaluation.id_evaluateur.';

-- ============================================================================
-- 3. PROPOSITIONS D'EMBAUCHE ET REPONSES DU CANDIDAT
-- ============================================================================

CREATE TABLE proposition_embauche (
    id_proposition_embauche BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_candidature BIGINT NOT NULL REFERENCES candidature(id_candidature),
    numero_version INTEGER NOT NULL DEFAULT 1,
    numero_version_precedente INTEGER,
    id_redacteur BIGINT NOT NULL REFERENCES employe(id_employe),

    -- Conditions propres à CETTE proposition : conservées même après refus.
    -- Poste et département se déduisent de candidature -> offre -> besoin_poste.
    salaire_mensuel_propose NUMERIC(14,2) NOT NULL,
    devise VARCHAR(3) NOT NULL DEFAULT 'MGA',
    date_debut_prevue DATE NOT NULL,
    type_contrat_propose VARCHAR(50) NOT NULL, -- Exemple : CDD ou CDI.
    duree_contrat_mois INTEGER,              -- Facultatif ; aucun contrat créé ici.
    horaires TEXT,
    lieu_travail VARCHAR(200) NOT NULL,
    avantages TEXT,
    autres_conditions TEXT,
    document_proposition_fichier TEXT,

    statut VARCHAR(15) NOT NULL DEFAULT 'BROUILLON',
    date_creation TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_envoi TIMESTAMPTZ,
    date_limite_reponse TIMESTAMPTZ,
    date_reponse TIMESTAMPTZ,
    message_reponse TEXT, -- Peut contenir le motif du refus, sans l'imposer.

    CONSTRAINT uq_proposition_version UNIQUE (id_candidature, numero_version),
    -- La version précédente doit exister POUR LA MEME CANDIDATURE.
    CONSTRAINT fk_proposition_precedente
        FOREIGN KEY (id_candidature, numero_version_precedente)
        REFERENCES proposition_embauche(id_candidature, numero_version),
    CONSTRAINT ck_proposition_chaine CHECK (
        (numero_version = 1 AND numero_version_precedente IS NULL)
        OR (numero_version > 1 AND numero_version_precedente IS NOT NULL
            AND numero_version_precedente = numero_version - 1)
    ),
    CONSTRAINT ck_proposition_salaire CHECK (
        salaire_mensuel_propose > 0 AND salaire_mensuel_propose < 'Infinity'::NUMERIC
    ),
    CONSTRAINT ck_proposition_devise CHECK (devise ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_proposition_contrat CHECK (btrim(type_contrat_propose) <> ''),
    CONSTRAINT ck_proposition_duree CHECK (duree_contrat_mois > 0),
    CONSTRAINT ck_proposition_lieu CHECK (btrim(lieu_travail) <> ''),
    CONSTRAINT ck_proposition_statut CHECK (statut IN (
        'BROUILLON', 'EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'EXPIREE', 'ANNULEE'
    )),
    CONSTRAINT ck_proposition_dates_statut CHECK (
        (statut = 'BROUILLON' AND date_envoi IS NULL AND date_reponse IS NULL)
        OR (statut = 'EN_ATTENTE' AND date_envoi IS NOT NULL AND date_reponse IS NULL)
        OR (statut IN ('ACCEPTEE', 'REFUSEE')
            AND date_envoi IS NOT NULL AND date_reponse IS NOT NULL)
        OR (statut = 'EXPIREE' AND date_envoi IS NOT NULL
            AND date_limite_reponse IS NOT NULL AND date_reponse IS NULL)
        OR (statut = 'ANNULEE' AND date_reponse IS NULL)
    ),
    CONSTRAINT ck_proposition_envoi CHECK (
        date_envoi IS NULL OR date_envoi >= date_creation
    ),
    CONSTRAINT ck_proposition_limite CHECK (
        date_limite_reponse IS NULL OR date_envoi IS NULL
        OR date_limite_reponse >= date_envoi
    ),
    CONSTRAINT ck_proposition_reponse CHECK (
        date_reponse IS NULL OR date_reponse >= date_envoi
    )
);

-- Une seule proposition en préparation/en attente, OU acceptée, par candidature.
-- Une version refusée, expirée ou annulée n'empêche pas d'en créer une nouvelle.
-- Une version acceptée empêche toute nouvelle proposition pour cette candidature.
CREATE UNIQUE INDEX uq_proposition_active_par_candidature
    ON proposition_embauche(id_candidature)
    WHERE statut IN ('BROUILLON', 'EN_ATTENTE', 'ACCEPTEE');

COMMENT ON TABLE proposition_embauche IS
    'Conditions proposées et réponse pour une candidature retenue. Une ligne par version ; refus puis renégociation = nouvelle ligne, sans création automatique d employé.';
COMMENT ON COLUMN proposition_embauche.numero_version_precedente IS
    'NULL pour la première version, puis numéro immédiatement précédent pour cette même candidature. La FK évite les versions manquantes ou étrangères.';

-- ============================================================================
-- 4. INDEX POUR LES LISTES ET LE SUIVI
-- ============================================================================

CREATE INDEX ix_employe_departement ON employe(id_departement);
CREATE INDEX ix_grille_poste ON grille_salaire(id_poste);
CREATE INDEX ix_besoin_departement ON besoin(id_departement);
CREATE INDEX ix_besoin_poste_poste ON besoin_poste(id_poste);
CREATE INDEX ix_demande_statut ON demande_recrutement(statut);
CREATE INDEX ix_historique_demande_date
    ON historique_validation(id_demande_recrutement, date_action);
CREATE INDEX ix_offre_statut_date ON offre_emploi(id_statut_offre, date_publication);
CREATE INDEX ix_candidature_offre_statut ON candidature(id_offre_emploi, statut);
CREATE INDEX ix_seance_candidature ON seance_evaluation(id_candidature);
CREATE INDEX ix_seance_evaluateur_entretien
    ON seance_evaluation(id_evaluateur, date_entretien);
CREATE INDEX ix_seance_evaluateur_test ON seance_evaluation(id_evaluateur, date_test);
CREATE INDEX ix_seance_test ON seance_evaluation(id_test);
CREATE INDEX ix_proposition_attente ON proposition_embauche(statut, date_limite_reponse);

-- ============================================================================
-- 5. VUES DE LECTURE : PAS DE DUPLICATION DES TOTAUX OU DES RELATIONS
-- ============================================================================

CREATE VIEW v_synthese_besoin AS
SELECT
    b.id_besoin,
    b.id_departement,
    b.motif,
    b.devise,
    COUNT(bp.id_besoin_poste) AS nombre_postes_distincts,
    COALESCE(SUM(bp.effectif), 0) AS effectif_total,
    -- NULL si le besoin est vide ou si un budget unitaire reste à compléter.
    CASE
        WHEN COUNT(bp.id_besoin_poste) = 0
          OR COUNT(bp.budget_mensuel_unitaire) < COUNT(bp.id_besoin_poste)
        THEN NULL
        ELSE SUM(bp.effectif * bp.budget_mensuel_unitaire)
    END AS budget_mensuel_total
FROM besoin b
LEFT JOIN besoin_poste bp ON bp.id_besoin = b.id_besoin
GROUP BY b.id_besoin, b.id_departement, b.motif, b.devise;

CREATE VIEW v_offre_recrutement AS
SELECT
    o.id_offre_emploi,
    o.id_besoin_poste,
    d.id_demande_recrutement,
    b.id_besoin,
    p.id_poste,
    p.libelle AS poste,
    dep.id_departement,
    dep.libelle AS departement,
    bp.effectif,
    o.titre,
    o.description,
    o.profil_recherche,
    o.lieu_travail,
    o.date_publication,
    o.date_limite_candidature,
    so.code AS statut_offre,
    d.statut AS statut_demande
FROM offre_emploi o
JOIN besoin_poste bp ON bp.id_besoin_poste = o.id_besoin_poste
JOIN besoin b ON b.id_besoin = bp.id_besoin
JOIN poste p ON p.id_poste = bp.id_poste
JOIN departement dep ON dep.id_departement = b.id_departement
JOIN statut_offre_emploi so ON so.id_statut_offre = o.id_statut_offre
-- LEFT JOIN rend visible une offre incorrectement créée sans demande,
-- afin que cette anomalie ne soit pas masquée lors d'un contrôle.
LEFT JOIN demande_recrutement d ON d.id_besoin = b.id_besoin;

COMMENT ON VIEW v_offre_recrutement IS
    'Lecture simplifiée des offres et de leur origine ; aucune donnée privée de budget ni salaire exposée par cette vue.';

COMMIT;

/*
===============================================================================
6. REGLES METIER A IMPLEMENTER DANS LES SERVICES DE L'APPLICATION
===============================================================================

Ces règles ne sont PAS entièrement garanties par les seules FK/CHECK ci-dessus.
Les écritures métier doivent passer par les services autorisés ; ne pas donner
aux utilisateurs finaux un accès SQL direct. Le présent script ne crée ni
comptes de connexion, ni rôles applicatifs, ni droits PostgreSQL pour l'application.

MODULE 1
  - Authentifier l'acteur. Rôles MANAGER/RH/FINANCE/DIRECTION gérés par le module
    d'accès existant ou futur, pas déduits d'un id_employe envoyé par le navigateur.
  - Le manager demande seulement pour son département ; la direction peut initier.
  - Avant soumission : au moins une ligne besoin_poste, effectifs et budgets
    renseignés. Vérifier grille, devise, validité et cohérence budgétaire.
  - Circuit STANDARD : BROUILLON -> EN_ATTENTE_VALIDATION_RH -> Finance si requise
    -> EN_ATTENTE_VALIDATION_DIRECTION -> VALIDEE. Rejets/corrections selon l'étape.
  - Circuit DIRECTION_DIRECTE : pas de RH ; Finance si requise puis Direction.
    Ce chemin conserve une validation finale tracée, même pour une création DG.
  - Le besoin est modifiable en BROUILLON/A_CORRIGER uniquement. Après validation,
    ne pas changer les postes, effectifs, budget ou rattachement utilisés par une
    offre ; une modification substantielle doit repasser par validation.
  - Changement de statut ET INSERT historique_validation dans UNE transaction,
    avec verrouillage de la demande (SELECT ... FOR UPDATE) pour éviter les
    validations simultanées. Le journal est consulté, jamais réécrit par le métier.

MODULE 2
  - Avant création d'offre : vérifier que la ligne besoin_poste possède une demande
    VALIDEE. L'absence de demande ou un statut non validé doit bloquer l'action.
    Le lien est offre -> besoin_poste -> besoin -> demande_recrutement.
  - Avant candidature : vérifier offre OUVERTE, publication atteinte et date limite
    non dépassée. Verrouiller l'offre pendant la soumission si elle peut être clôturée
    simultanément. Une seule candidature par candidat et offre dans cette version.
  - La date limite ne clôture pas automatiquement une offre : filtrer les offres
    publiques sur les dates, et mettre à jour le statut via le service approprié.
  - Autoriser une séance après présélection ; refuser une candidature rejetée/retirée.
    Vérifier les disponibilités de l'évaluateur et du candidat ; prévenir les
    conflits d'horaires. Le planning détaillé/durées pourra être enrichi ensuite.
  - Avant saisie/finalisation : vérifier le droit du RH ou de l'évaluateur affecté.
    Interdire une note d'entretien sans entretien et une note de test sans test.
    Une fiche finalisée exige les notes de TOUTES les épreuves prévues et réalisées.
    Ne pas finaliser une séance annulée ou absente ; les absences ne valent pas 0.
  - Un test déjà utilisé conserve son niveau et son contenu ; créer un nouveau test
    pour une autre version du sujet. Ne pas réaffecter une séance déjà évaluée à
    une autre candidature. Les corrections de notes doivent être contrôlées.
  - Le RH décide RETENUE/REJETEE avec id_decideur, date_decision et motif, après
    examen des résultats. Aucun seuil de réussite ni moyenne n'est inventé ici.
  - Le candidat accède uniquement à ses propres dossiers et convocations ; les
    notes et commentaires internes sont réservés aux personnes autorisées.
  - CV/lettres/sujets/documents : clés de fichiers privés, téléchargement contrôlé.

MODULE 3
  - Avant création/envoi : candidature RETENUE, conditions complètes et autorisées.
    Vérifier l'effectif encore disponible sur l'offre, en tenant compte des
    propositions en attente/acceptées selon votre politique de réservation.
  - Version 1 = première proposition ; version N = nouvelle proposition après
    refus, expiration ou annulation de N-1. Ne jamais réécrire les conditions
    d'une proposition déjà envoyée, ni effacer la réponse précédente.
  - BROUILLON -> EN_ATTENTE avec date_envoi ; puis ACCEPTEE ou REFUSEE avec
    date_reponse et message éventuel. EXPIREE/ANNULEE clôturent sans réponse.
  - Seul le candidat concerné peut répondre via son accès authentifié.
    Accepter/refuser seulement une proposition EN_ATTENTE, dans son délai.
    Verrouiller la candidature/proposition dans la transaction pour les opérations
    concurrentes ; vérifier le nombre de places au moment où une place est réservée.
  - EXPIREE n'est pas calculé automatiquement : vérifier le délai dans le service.
  - Refus de proposition != rejet de candidature : elle peut rester RETENUE pendant
    la renégociation. Le numéro suivant est calculé sous verrou, pas via un MAX
    non protégé. La contrainte UNIQUE protège aussi contre les doublons.
  - ACCEPTEE termine le périmètre actuel. Le futur module créera explicitement
    employé/matricule/contrat/affectation en conservant le lien à la proposition
    acceptée et au candidat d'origine. Les anciennes candidatures sont conservées.

EXEMPLES DE LECTURE (à exécuter séparément si besoin)

-- Origine d'une offre, sans répéter id_poste/id_demande dans offre_emploi :
SELECT id_offre_emploi, id_demande_recrutement, poste, departement, effectif
FROM sigrh.v_offre_recrutement;

-- Offres actuellement ouvertes à la candidature :
SELECT * FROM sigrh.v_offre_recrutement
WHERE statut_offre = 'OUVERTE'
  AND statut_demande = 'VALIDEE'
  AND date_publication <= CURRENT_TIMESTAMP
  AND (date_limite_candidature IS NULL OR date_limite_candidature >= CURRENT_TIMESTAMP);

-- Exemple fictif : historique des propositions de la candidature 123 :
SELECT numero_version, salaire_mensuel_propose, devise, statut, date_reponse
FROM sigrh.proposition_embauche
WHERE id_candidature = 123
ORDER BY numero_version;

-- Budget mensuel total : NULL signifie « données incomplètes », pas zéro.
SELECT * FROM sigrh.v_synthese_besoin;

DOCUMENTATION POSTGRESQL UTILISEE
  https://www.postgresql.org/docs/18/ddl-identity-columns.html
  https://www.postgresql.org/docs/18/ddl-constraints.html
  https://www.postgresql.org/docs/18/indexes-partial.html
*/
