-- SIGRH simplifié — PostgreSQL — modules 1, 2 et 3 (3.7 contrat, 3.8 affectation inclus).
-- Initialisation dans une base vide ; ce fichier n'est pas une migration.
-- Montants en ariary, budgets/salaires mensuels par personne, notes sur 20.
-- Les droits, validations et changements de statut sont gérés par l'application.

BEGIN;

-- Tables communes : un poste existe indépendamment des employés.
CREATE TABLE departement (
    id SERIAL PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE poste (
    id SERIAL PRIMARY KEY,
    id_departement INTEGER NOT NULL REFERENCES departement(id),
    libelle VARCHAR(100) NOT NULL
);

-- Employés existants : demandeurs, validateurs et évaluateurs.
-- Un candidat embauché y est ajouté par l'application à la création du contrat (3.7).
CREATE TABLE employe (
    id VARCHAR(10) PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100),
    id_departement INTEGER REFERENCES departement(id)
);

-- MODULE 1 : besoin et validation de la demande.
CREATE TABLE grille_salaire (
    id SERIAL PRIMARY KEY,
    id_poste INTEGER NOT NULL UNIQUE REFERENCES poste(id),
    salaire_min NUMERIC(14,2) NOT NULL,
    salaire_max NUMERIC(14,2) NOT NULL,
    CHECK (salaire_min >= 0 AND salaire_max >= salaire_min)
);

CREATE TABLE besoin (
    id SERIAL PRIMARY KEY,
    id_departement INTEGER NOT NULL REFERENCES departement(id),
    -- NOUVEAU : catégorie du motif (adapte la liste à ton cours).
    type_motif VARCHAR(30) NOT NULL
        CHECK (type_motif IN ('REMPLACEMENT','NOUVEAU_POSTE','NOUVEAU_SERVICE','AUTRE')),
    motif TEXT NOT NULL
);

CREATE TABLE besoin_poste (
    id SERIAL PRIMARY KEY,
    id_besoin INTEGER NOT NULL REFERENCES besoin(id),
    id_poste INTEGER REFERENCES poste(id),
    libelle_poste_propose VARCHAR(100),
    effectif INTEGER NOT NULL CHECK (effectif > 0),
    budget_mensuel_unitaire NUMERIC(14,2) NOT NULL CHECK (budget_mensuel_unitaire >= 0),
    UNIQUE (id_besoin, id_poste),
    CHECK (id_poste IS NOT NULL OR libelle_poste_propose IS NOT NULL)
);

CREATE TABLE demande_recrutement (
    id SERIAL PRIMARY KEY,
    id_besoin INTEGER NOT NULL UNIQUE REFERENCES besoin(id),
    id_demandeur VARCHAR(10) NOT NULL REFERENCES employe(id),
    date_creation DATE NOT NULL DEFAULT CURRENT_DATE,
    date_arrivee_souhaitee DATE,
    circuit VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
        CHECK (circuit IN ('STANDARD','DIRECTION_DIRECTE')),
    validation_finance BOOLEAN NOT NULL DEFAULT FALSE,
    statut VARCHAR(30) NOT NULL DEFAULT 'BROUILLON'
        CHECK (statut IN ('BROUILLON','ATTENTE_RH','ATTENTE_FINANCE',
                          'ATTENTE_DIRECTION','A_CORRIGER','VALIDEE','REJETEE')),
    CHECK (date_arrivee_souhaitee IS NULL OR date_arrivee_souhaitee >= date_creation)
);

-- Ajouter une ligne à chaque décision, avec l'acteur et son commentaire.
CREATE TABLE historique_validation (
    id SERIAL PRIMARY KEY,
    id_demande INTEGER NOT NULL REFERENCES demande_recrutement(id),
    id_validateur VARCHAR(10) NOT NULL REFERENCES employe(id),
    etape VARCHAR(20) NOT NULL
        CHECK (etape IN ('RH','FINANCE','DIRECTION')),
    decision VARCHAR(20) NOT NULL
        CHECK (decision IN ('APPROUVEE','REJETEE','A_CORRIGER')),
    date_validation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    commentaire TEXT,
    -- Motif obligatoire si ce n'est pas une approbation.
    CHECK (decision = 'APPROUVEE' OR (commentaire IS NOT NULL AND length(trim(commentaire)) > 0))
);

-- MODULE 2 : offres et candidatures.
-- Le poste et la demande se retrouvent via besoin_poste puis besoin.
-- L'application autorise la création de l'offre seulement si la demande est VALIDEE.
CREATE TABLE offre_emploi (
    id SERIAL PRIMARY KEY,
    id_besoin_poste INTEGER NOT NULL UNIQUE REFERENCES besoin_poste(id),
    titre VARCHAR(150) NOT NULL,
    description TEXT,
    date_creation DATE NOT NULL DEFAULT CURRENT_DATE,
    date_publication DATE,
    date_limite DATE,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON' -- BROUILLON / OUVERTE / CLOTUREE
);

CREATE TABLE candidat (
    id SERIAL PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100),
    date_naissance DATE,
    email VARCHAR(255) NOT NULL UNIQUE,
    telephone VARCHAR(30),
    qualifications TEXT
);

-- Le CV appartient à la candidature : un candidat peut postuler à plusieurs offres.
CREATE TABLE candidature (
    id SERIAL PRIMARY KEY,
    id_candidat INTEGER NOT NULL REFERENCES candidat(id),
    id_offre INTEGER NOT NULL REFERENCES offre_emploi(id),
    cv TEXT NOT NULL, -- Référence unique du fichier stocké.
    lettre_motivation TEXT,
    message TEXT,
    date_candidature TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    statut VARCHAR(30) NOT NULL DEFAULT 'RECUE', -- RECUE / PRESELECTIONNEE / RETENUE / REJETEE
    UNIQUE (id_candidat, id_offre)
);

-- Définition du test ; ses résultats sont dans fiche_note_evaluation.
CREATE TABLE test_recrutement (
    id SERIAL PRIMARY KEY,
    niveau INTEGER NOT NULL CHECK (niveau BETWEEN 1 AND 3),
    description TEXT NOT NULL,
    id_responsable VARCHAR(10) NOT NULL REFERENCES employe(id)
);

-- Une séance concerne une candidature et un évaluateur.
-- Entretien seul, test seul ou les deux : les dates inutilisées restent NULL.
CREATE TABLE seance_evaluation (
    id SERIAL PRIMARY KEY,
    id_candidature INTEGER NOT NULL REFERENCES candidature(id),
    id_evaluateur VARCHAR(10) NOT NULL REFERENCES employe(id),
    id_test INTEGER REFERENCES test_recrutement(id),
    date_entretien TIMESTAMP,
    date_test TIMESTAMP,
    lieu_entretien TEXT,
    lieu_test TEXT,
    statut VARCHAR(20) NOT NULL DEFAULT 'PLANIFIEE' -- PLANIFIEE / TERMINEE / ANNULEE / ABSENT
);

create table decision_candidature(
    id SERIAL PRIMARY KEY,
    label VARCHAR(30)
);

CREATE TABLE historique_decision_candidature (
    id SERIAL PRIMARY KEY,
    id_candidature INTEGER NOT NULL REFERENCES candidature(id),
    id_decideur VARCHAR(50) NOT NULL REFERENCES employe(id) ,
    idDecision INTEGER NOT NULL REFERENCES decision_candidature(id),
    date_decision TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motif TEXT
);

-- Une seule fiche par séance. NULL signifie qu'aucune note n'est renseignée.
CREATE TABLE fiche_note_evaluation (
    id SERIAL PRIMARY KEY,
    id_seance INTEGER NOT NULL UNIQUE REFERENCES seance_evaluation(id),
    note_entretien NUMERIC(4,2) CHECK (note_entretien BETWEEN 0 AND 20),
    note_test NUMERIC(4,2) CHECK (note_test BETWEEN 0 AND 20),
    commentaire TEXT,
    avis VARCHAR(20) -- FAVORABLE / RESERVE / DEFAVORABLE
);

-- MODULE 3 : une ligne par proposition envoyée.
-- Après un refus : INSERT d'une nouvelle ligne pour la même candidature.
-- Conserver les anciennes conditions et réponses. Aucun employé créé ici.
CREATE TABLE proposition_embauche (
    id SERIAL PRIMARY KEY,
    id_candidature INTEGER NOT NULL REFERENCES candidature(id),
    id_emetteur VARCHAR(10) NOT NULL REFERENCES employe(id),
    salaire_mensuel NUMERIC(14,2) NOT NULL,
    date_debut_prevue DATE NOT NULL,
    type_contrat VARCHAR(30) NOT NULL,
    horaires VARCHAR(150),
    lieu_travail VARCHAR(150),
    autres_conditions TEXT,
    date_envoi TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_reponse TIMESTAMP,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE', -- EN_ATTENTE / ACCEPTEE / REFUSEE
    message_reponse TEXT
);

-- 3.7 Création du contrat : après acceptation d'une proposition (statut ACCEPTEE).
-- L'application crée l'employé (candidat embauché) puis le contrat. Les valeurs
-- (type, début, salaire, horaires, lieu) sont reprises de la proposition acceptée
-- et peuvent être ajustées avant signature : le contrat fait foi.
CREATE TABLE contrat (
    id SERIAL PRIMARY KEY,
    id_proposition INTEGER NOT NULL UNIQUE REFERENCES proposition_embauche(id),
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    id_poste INTEGER NOT NULL REFERENCES poste(id),
    type_contrat VARCHAR(30) NOT NULL, -- CDI / CDD / ...
    date_debut DATE NOT NULL,
    date_fin DATE, -- NULL si le contrat n'a pas de fin (CDI).
    salaire_mensuel NUMERIC(14,2) NOT NULL CHECK (salaire_mensuel >= 0),
    horaires VARCHAR(150),
    lieu_travail VARCHAR(150),
    conditions TEXT, -- Conditions prévues par l'entreprise.
    date_creation DATE NOT NULL DEFAULT CURRENT_DATE,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON', -- BROUILLON / SIGNE / TERMINE / ANNULE
    CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Documents associés au contrat, dont le PDF généré.
CREATE TABLE document_contrat (
    id SERIAL PRIMARY KEY,
    id_contrat INTEGER NOT NULL REFERENCES contrat(id),
    type_document VARCHAR(30) NOT NULL, -- CONTRAT_PDF / PIECE_IDENTITE / AUTRE
    reference_fichier TEXT NOT NULL, -- Référence unique du fichier stocké.
    date_ajout TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3.8 Affectation : une ligne par période d'affectation, jamais écrasée.
-- Un changement de poste, de département ou de lieu clôture la ligne en cours
-- (date_fin) et en insère une nouvelle : l'historique est conservé.
CREATE TABLE affectation (
    id SERIAL PRIMARY KEY,
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    id_contrat INTEGER REFERENCES contrat(id), -- Contrat à l'origine de l'affectation.
    id_poste INTEGER NOT NULL REFERENCES poste(id),
    id_departement INTEGER NOT NULL REFERENCES departement(id),
    lieu VARCHAR(150),
    date_debut DATE NOT NULL,
    date_fin DATE, -- NULL = affectation en cours.
    motif TEXT,
    CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Au plus une affectation en cours par employé.
CREATE UNIQUE INDEX uq_affectation_en_cours
    ON affectation (id_employe) WHERE date_fin IS NULL;

CREATE INDEX idx_affectation_employe ON affectation (id_employe, date_debut);

CREATE TABLE role_user (
    id SERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE utilisateur (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    id_role INTEGER NOT NULL REFERENCES role_user(id),
    id_employee VARCHAR(10) UNIQUE REFERENCES employe(id),
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMIT;

-- À contrôler dans l'application : rôles, ordre des validations, cohérence du
-- département du besoin et des postes, offre ouverte, présélection avant séance,
-- correspondance dates/tests/notes, candidature retenue avant proposition.
-- Une seule proposition en attente ou acceptée par candidature ; une nouvelle
-- proposition après refus conserve l'ancienne ligne.
-- Contrat : créé seulement si la proposition est ACCEPTEE ; génération du PDF et
-- ajout dans document_contrat. Affectation : créée à partir du contrat, cohérence
-- poste/département, pas de chevauchement de périodes pour un même employé.
