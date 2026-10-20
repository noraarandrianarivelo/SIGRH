-- SIGRH simplifié — PostgreSQL — modules 1, 2 et 3.
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
CREATE TABLE employe (
    id SERIAL PRIMARY KEY,
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
    motif TEXT NOT NULL
);

-- Un besoin peut demander plusieurs postes, chacun avec son effectif.
CREATE TABLE besoin_poste (
    id SERIAL PRIMARY KEY,
    id_besoin INTEGER NOT NULL REFERENCES besoin(id),
    id_poste INTEGER NOT NULL REFERENCES poste(id),
    effectif INTEGER NOT NULL CHECK (effectif > 0),
    budget_mensuel_unitaire NUMERIC(14,2),
    UNIQUE (id_besoin, id_poste)
);

CREATE TABLE demande_recrutement (
    id SERIAL PRIMARY KEY,
    id_besoin INTEGER NOT NULL UNIQUE REFERENCES besoin(id),
    id_demandeur INTEGER NOT NULL REFERENCES employe(id),
    date_creation DATE NOT NULL DEFAULT CURRENT_DATE,
    date_arrivee_souhaitee DATE,
    circuit VARCHAR(20) NOT NULL DEFAULT 'STANDARD', -- STANDARD / DIRECTION_DIRECTE
    validation_finance BOOLEAN NOT NULL DEFAULT FALSE, -- Contrôle requis ou non.
    statut VARCHAR(30) NOT NULL DEFAULT 'BROUILLON'
    -- BROUILLON / ATTENTE_RH / ATTENTE_FINANCE / ATTENTE_DIRECTION /
    -- A_CORRIGER / VALIDEE / REJETEE
);

-- Ajouter une ligne à chaque décision, avec l'acteur et son commentaire.
CREATE TABLE historique_validation (
    id SERIAL PRIMARY KEY,
    id_demande INTEGER NOT NULL REFERENCES demande_recrutement(id),
    id_validateur INTEGER NOT NULL REFERENCES employe(id),
    etape VARCHAR(20) NOT NULL, -- RH / FINANCE / DIRECTION
    decision VARCHAR(20) NOT NULL, -- APPROUVEE / REJETEE / A_CORRIGER
    date_validation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    commentaire TEXT
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
    email VARCHAR(255) NOT NULL,
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
    id_decideur INTEGER REFERENCES employe(id),
    date_decision TIMESTAMP,
    motif_decision TEXT,
    UNIQUE (id_candidat, id_offre)
);

-- Définition du test ; ses résultats sont dans fiche_note_evaluation.
CREATE TABLE test_recrutement (
    id SERIAL PRIMARY KEY,
    niveau INTEGER NOT NULL CHECK (niveau BETWEEN 1 AND 3),
    description TEXT NOT NULL,
    id_responsable INTEGER NOT NULL REFERENCES employe(id)
);

-- Une séance concerne une candidature et un évaluateur.
-- Entretien seul, test seul ou les deux : les dates inutilisées restent NULL.
CREATE TABLE seance_evaluation (
    id SERIAL PRIMARY KEY,
    id_candidature INTEGER NOT NULL REFERENCES candidature(id),
    id_evaluateur INTEGER NOT NULL REFERENCES employe(id),
    id_test INTEGER REFERENCES test_recrutement(id),
    date_entretien TIMESTAMP,
    date_test TIMESTAMP,
    lieu_entretien TEXT,
    lieu_test TEXT,
    statut VARCHAR(20) NOT NULL DEFAULT 'PLANIFIEE' -- PLANIFIEE / TERMINEE / ANNULEE / ABSENT
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
    id_emetteur INTEGER NOT NULL REFERENCES employe(id),
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

COMMIT;

-- À contrôler dans l'application : rôles, ordre des validations, cohérence du
-- département du besoin et des postes, offre ouverte, présélection avant séance,
-- correspondance dates/tests/notes, candidature retenue avant proposition.
-- Une seule proposition en attente ou acceptée par candidature ; une nouvelle
-- proposition après refus conserve l'ancienne ligne. Contrat et affectation : plus tard.
