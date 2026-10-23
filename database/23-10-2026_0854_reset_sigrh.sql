-- =============================================================================
-- SIGRH : REMISE À ZÉRO COMPLÈTE ET DONNÉES DE DÉMONSTRATION
-- PostgreSQL / psql. Script autonome : aucun fichier SQL externe à inclure.
-- =============================================================================
-- ATTENTION : supprime la base cible ET TOUTES SES DONNÉES, puis la recrée.
-- Arrêter l'application avant l'exécution : les connexions sont coupées de force.
-- Ce script recharge les données présentes dans les fichiers SQL du dépôt ;
-- il ne sauvegarde pas les données ajoutées manuellement dans une base en cours.
-- Les fichiers PDF/CV référencés ne sont ni créés ni supprimés par ce script.
--
-- Depuis la racine du dépôt, avec un compte autorisé à créer/supprimer la base :
--   psql -X -h localhost -U postgres -d postgres -f database/reset_sigrh.sql
--
-- Pour une AUTRE base (ex. une base de test jetable) :
--   psql -X -h localhost -U postgres -d postgres \
--     -v db_name=sigrh_demo -f database/reset_sigrh.sql
--
-- Utiliser psql : les commandes \connect, \if et \gset ne sont pas du SQL JDBC.
-- Ne pas utiliser --single-transaction : DROP/CREATE DATABASE sont hors transaction.
-- Le schéma et toutes les insertions sont ensuite validés dans une transaction.
-- La base et ses tables appartiennent au compte qui exécute ce script.
--
-- Sources examinées et consolidées :
--   SIGRH.sql                              : schéma partagé actuel (blocs 1–3).
--   20-10-2026_2105_init.sql                : employés EMP*, anciens postes/comptes.
--   21-10-2026_0102_modifinit.sql           : référentiels et employés E* actualisés.
--   21-10-2026_0103_Noah_Init_Bloc2.sql     : deux offres et le premier candidat.
--   21-10-2026_0104_Noah_Init_Bloc2_Evaluations.sql : évaluateurs et tests.
--   22-10-2026_0105_insertAaron.sql         : poste de direction et candidats 2/3.
--   22-10-2026_01_bloc4_tables_manquantes.sql : neuf tables et deux vues actuelles.
--   22-10-2026_02_bloc4_donnees_test.sql    : scénarios Soa, Rija et Lova, avec audit.
--   22-10-2026_03_bloc4_acces_salaries_test.sql : trois accès salariés inclus.
--   database/bloc4.sql et Note/bloc4.sql   : prototypes examinés mais remplacés
--     par le modèle actuel (pas de table document/onboarding/onboarding_etape).
--
-- Arbitrages nécessaires entre les anciens seeds incompatibles :
--   - Les rôles et départements communs ne sont insérés qu'une fois.
--   - Les libellés actualisés de 0102 sont retenus pour les mêmes départements.
--   - Le département informatique 10 du seed Noah est rattaché à Informatique 1.
--   - Les postes Java Senior et Contrôleur Financier sont conservés sous 7 et 8.
--   - Chargé de Recrutement réutilise le poste 4 ; la différence de casse est ignorée.
--   - La grille Java Senior est donc rattachée au poste 7, pas au Développeur 1.
--   - Les comptes du premier seed conservent password123, stocké en BCrypt.
--   - Les empreintes BCrypt des seeds suivants restent inchangées.
--   - Le JOIN role_user absent du SELECT utilisateur de 0102 est rétabli.
--   - Les séquences SERIAL sont resynchronisées avant et après le jeu B4.
--
-- Comptes :
--   m_rakoto, rh_marie, fin_hery, dir_paul : password123 (anciens acteurs EMP*).
--   manager, manager_mkt, rh, finance, direction, candidat1/2/3@mail.com :
--     empreinte historique inchangée ; le clair n'est pas indiqué dans ces seeds.
--   b4_rh, b4_it, b4_formation, b4_paie, b4_direction,
--   b4_soa, b4_rija, b4_lova : Bloc4Test2026! (scénarios d'intégration).
-- =============================================================================

\set ON_ERROR_STOP on
\encoding UTF8

-- La variable optionnelle permet de tester le script sans viser la base habituelle.
\if :{?db_name}
\else
    \set db_name sigrh
\endif

SELECT
    :'db_name' ~ '^[A-Za-z_][A-Za-z0-9_]*$'
    AND octet_length(:'db_name') <= 63
    AND lower(:'db_name') NOT IN ('postgres', 'template0', 'template1')
    AS nom_base_valide
\gset

\if :nom_base_valide
\else
    DO $$
    BEGIN
        RAISE EXCEPTION 'Nom de base invalide ou réservé. Utiliser db_name=sigrh ou un nom de base de démonstration.';
    END;
    $$;
\endif

-- On quitte la base cible avant de la supprimer. Hôte, port et utilisateur sont repris.
\connect postgres

-- Vérifier la capacité de recréation AVANT de supprimer quoi que ce soit.
DO $privileges$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = current_user AND (rolcreatedb OR rolsuper)
    ) THEN
        RAISE EXCEPTION 'Le compte % doit avoir CREATEDB pour reconstruire la base.', current_user;
    END IF;
END;
$privileges$;

\echo Reconstruction de la base :db_name
DROP DATABASE IF EXISTS :"db_name" WITH (FORCE);
CREATE DATABASE :"db_name" WITH TEMPLATE = template0 ENCODING = 'UTF8';
\connect :db_name

SET TIME ZONE 'Indian/Antananarivo';
SET search_path TO public;

BEGIN;

-- =============================================================================
-- 1. SCHÉMA PARTAGÉ ACTUEL : BLOCS 1 À 3
-- Source : database/SIGRH.sql
-- =============================================================================

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
    id_besoin_poste INTEGER NOT NULL REFERENCES besoin_poste(id),
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
    id_proposition INTEGER UNIQUE REFERENCES proposition_embauche(id),
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    id_contrat_precedent INTEGER REFERENCES contrat(id),
    id_poste INTEGER NOT NULL REFERENCES poste(id),
    type_contrat VARCHAR(30) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    date_signature DATE,
    salaire_mensuel NUMERIC(14,2) NOT NULL CHECK (salaire_mensuel >= 0),
    horaires VARCHAR(150),
    lieu_travail VARCHAR(150),
    conditions TEXT,
    date_creation DATE NOT NULL DEFAULT CURRENT_DATE,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON' CHECK (statut IN ('BROUILLON', 'SIGNE', 'TERMINE', 'ANNULE')),
    CHECK (date_fin IS NULL OR date_fin >= date_debut),
    CHECK (
        id_contrat_precedent IS NULL
        OR id_contrat_precedent <> id
    )
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
    -- Contrat à l'origine de cette affectation.
    id_contrat INTEGER REFERENCES contrat(id),
    -- Le département se retrouve via poste.id_departement.
    id_poste INTEGER NOT NULL REFERENCES poste(id),
    lieu VARCHAR(150),
    date_debut DATE NOT NULL,
    date_fin DATE,
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

-- =============================================================================
-- 2. TABLES ET VUES DU BLOC 4
-- Source : database/22-10-2026_01_bloc4_tables_manquantes.sql
-- =============================================================================

CREATE TABLE compte_employe (
    id SERIAL PRIMARY KEY,
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    systeme VARCHAR(100) NOT NULL,
    identifiant VARCHAR(150) NOT NULL,
    date_activation DATE NOT NULL,
    UNIQUE (systeme, identifiant)
);
-- Une ligne constate un compte informatique operationnel. Aucun mot de passe :
-- celui de l'application RH appartient deja a utilisateur.

CREATE TABLE badge_employe (
    id SERIAL PRIMARY KEY,
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    numero_badge VARCHAR(50) NOT NULL,
    date_remise DATE NOT NULL,
    date_restitution DATE,
    CHECK (date_restitution IS NULL OR date_restitution >= date_remise)
);

CREATE UNIQUE INDEX uq_badge_non_restitue
    ON badge_employe (numero_badge)
    WHERE date_restitution IS NULL;

CREATE TABLE materiel (
    id SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL
);
-- Une ligne = un objet physique. Exemple : "Ordinateur Dell inventaire 001".

CREATE TABLE materiel_employe (
    id SERIAL PRIMARY KEY,
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    id_materiel INTEGER NOT NULL REFERENCES materiel(id),
    date_reception DATE NOT NULL,
    date_restitution DATE,
    CHECK (date_restitution IS NULL OR date_restitution >= date_reception)
);

CREATE UNIQUE INDEX uq_materiel_non_restitue
    ON materiel_employe (id_materiel)
    WHERE date_restitution IS NULL;
-- Ces index empechent deux detentions non cloturees du meme objet.
-- Les chevauchements entre periodes historiques restent a verifier au serveur.

CREATE TABLE formation (
    id SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL
);

CREATE TABLE formation_employe (
    id SERIAL PRIMARY KEY,
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    id_formation INTEGER NOT NULL REFERENCES formation(id),
    date_completion DATE
);
-- NULL = attribuee mais pas terminee. Une formation peut etre suivie de nouveau.

CREATE TABLE inscription_paie (
    id SERIAL PRIMARY KEY,
    id_employe VARCHAR(10) NOT NULL REFERENCES employe(id),
    date_inscription DATE NOT NULL,
    date_debut_prise_en_charge DATE NOT NULL,
    UNIQUE (id_employe, date_debut_prise_en_charge)
);
-- Une ligne = inscription CONFIRMEE, pas simple transmission du dossier.
-- La preparation est suivie dans onboarding_tache. Pas de reinscription
-- automatique lors d'un renouvellement du contrat.

CREATE TABLE onboarding_tache (
    id SERIAL PRIMARY KEY,
    id_contrat INTEGER NOT NULL REFERENCES contrat(id),
    etape VARCHAR(30) NOT NULL CHECK (etape IN (
        'SIGNATURE_CONTRAT', 'BADGE', 'COMPTE',
        'MATERIEL', 'FORMATION', 'INSCRIPTION_PAIE'
    )),
    libelle VARCHAR(200) NOT NULL,
    id_assignateur VARCHAR(10) NOT NULL REFERENCES employe(id),
    id_responsable VARCHAR(10) NOT NULL REFERENCES employe(id),
    statut VARCHAR(20) NOT NULL DEFAULT 'A_FAIRE'
        CHECK (statut IN ('A_FAIRE', 'EN_COURS', 'BLOQUEE', 'TERMINEE')),
    date_completion TIMESTAMPTZ,
    CHECK ((statut = 'TERMINEE') = (date_completion IS NOT NULL))
);
-- id_contrat = contrat A L'ENTREE, pas le contrat actuel a chaque renouvellement.
-- L'employe concerne se deduit de contrat.id_employe, sans doublon.
-- Avant TERMINEE, le serveur verifie/enregistre le resultat reel : signature,
-- remise, activation, formation terminee ou inscription paie confirmee.
-- Si la tache est rouverte : remettre date_completion a NULL.

CREATE TABLE mouvement_rh (
    id SERIAL PRIMARY KEY,
    id_employe_concerne VARCHAR(10) REFERENCES employe(id),
    id_acteur VARCHAR(10) REFERENCES employe(id),
    table_concernee VARCHAR(50) NOT NULL,
    id_ligne TEXT NOT NULL,
    action VARCHAR(20) NOT NULL
        CHECK (action IN ('CREATION', 'MODIFICATION', 'SUPPRESSION')),
    date_mouvement TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valeurs_avant JSONB,
    valeurs_apres JSONB,
    CHECK (
        (action = 'CREATION' AND valeurs_avant IS NULL AND valeurs_apres IS NOT NULL)
        OR (action = 'MODIFICATION' AND valeurs_avant IS NOT NULL AND valeurs_apres IS NOT NULL)
        OR (action = 'SUPPRESSION' AND valeurs_avant IS NOT NULL AND valeurs_apres IS NULL)
    )
);
-- id_ligne est du texte : accepte "12" (contrat/tache) ou "B4N001" (employe).
-- table_concernee + id_ligne est une reference de journal, pas une FK SQL.
-- id_employe_concerne peut etre NULL pour un element de catalogue partage.
-- id_acteur peut etre NULL pour un traitement automatique.
-- Proteger ce journal contre UPDATE/DELETE par le compte applicatif courant.
-- Ne pas journaliser le mot de passe de utilisateur.

-- Les vues sont des lectures calculees, pas deux nouvelles tables.
-- On garde les anciens parcours et on exclut les simples renouvellements
-- sans tache. Une reembauche distincte commence un nouveau parcours.
CREATE VIEW suivi_onboarding_etape AS
WITH etapes (etape) AS (
    VALUES ('SIGNATURE_CONTRAT'), ('BADGE'), ('COMPTE'),
           ('MATERIEL'), ('FORMATION'), ('INSCRIPTION_PAIE')
)
SELECT
    c.id AS id_contrat,
    c.id_employe,
    e.etape,
    COUNT(t.id) AS nombre_taches,
    COUNT(t.id) FILTER (WHERE t.statut = 'TERMINEE') AS nombre_terminees,
    CASE
        WHEN COUNT(t.id) = 0 THEN 'A_CONFIGURER'
        WHEN BOOL_AND(t.statut = 'TERMINEE') THEN 'TERMINEE'
        WHEN BOOL_OR(t.statut = 'BLOQUEE') THEN 'BLOQUEE'
        WHEN BOOL_OR(t.statut <> 'A_FAIRE') THEN 'EN_COURS'
        ELSE 'A_FAIRE'
    END AS statut
FROM contrat c
CROSS JOIN etapes e
LEFT JOIN onboarding_tache t ON t.id_contrat = c.id AND t.etape = e.etape
WHERE c.id_contrat_precedent IS NULL
   OR EXISTS (SELECT 1 FROM onboarding_tache x WHERE x.id_contrat = c.id)
GROUP BY c.id, c.id_employe, e.etape;

CREATE VIEW suivi_onboarding AS
SELECT
    id_contrat,
    id_employe,
    COUNT(*) FILTER (WHERE statut = 'TERMINEE') AS nombre_etapes_terminees,
    COUNT(*) AS nombre_etapes,
    CASE
        WHEN BOOL_OR(statut = 'A_CONFIGURER') THEN 'A_CONFIGURER'
        WHEN BOOL_AND(statut = 'TERMINEE') THEN 'TERMINE'
        WHEN BOOL_OR(statut = 'BLOQUEE') THEN 'BLOQUE'
        WHEN BOOL_OR(statut <> 'A_FAIRE') THEN 'EN_COURS'
        ELSE 'A_FAIRE'
    END AS statut
FROM suivi_onboarding_etape
GROUP BY id_contrat, id_employe;

-- =============================================================================
-- 3. DONNÉES COMMUNES CONSOLIDÉES : BLOCS 1 À 3
-- =============================================================================

-- Rôles uniques. ROLE_SALARIE est destiné aux recrues, pas ROLE_MANAGER.
INSERT INTO role_user (id, role_name) VALUES
    (1, 'ROLE_MANAGER'),
    (2, 'ROLE_RH'),
    (3, 'ROLE_FINANCE'),
    (4, 'ROLE_DIRECTION'),
    (5, 'CANDIDAT'),
    (6, 'ROLE_SALARIE');

-- Référentiels actualisés du seed 0102 ; Informatique n'est pas recréé sous 10.
INSERT INTO departement (id, libelle) VALUES
    (1, 'Informatique'),
    (2, 'Ressources humaines'),
    (3, 'Finance'),
    (4, 'Direction'),
    (5, 'Marketing');

INSERT INTO poste (id, id_departement, libelle) VALUES
    (1, 1, 'Développeur'),
    (2, 1, 'Administrateur réseau'),
    (3, 1, 'Chef de projet'),
    (4, 2, 'Chargé de recrutement'),
    (5, 3, 'Comptable'),
    (6, 5, 'Community manager'),
    (7, 1, 'Développeur Java Senior'),
    (8, 3, 'Contrôleur Financier'),
    (9, 4, 'Assistante de direction');

INSERT INTO grille_salaire (id_poste, salaire_min, salaire_max) VALUES
    (1, 1500000.00, 3000000.00),
    (2, 1800000.00, 3500000.00),
    (3, 3000000.00, 6000000.00),
    (4, 1200000.00, 2500000.00),
    (5, 1300000.00, 2800000.00),
    (6, 1000000.00, 2000000.00),
    (7, 2000000.00, 3500000.00);

-- Les deux jeux d'acteurs sont distincts : leurs identifiants et comptes sont conservés.
INSERT INTO employe (id, nom, prenom, id_departement) VALUES
    ('EMP001', 'RAKOTO', 'Jean', 1),
    ('EMP002', 'RANDRIA', 'Marie', 2),
    ('EMP003', 'RAZAFY', 'Hery', 3),
    ('EMP004', 'RASOA', 'Paul', 1),
    ('E001', 'Rakoto', 'Jean', 1),
    ('E002', 'Rabe', 'Sala', 2),
    ('E003', 'Rasoa', 'Voahangy', 3),
    ('E004', 'Andry', 'Hery', 4),
    ('E005', 'Rivo', 'Miora', 5),
    ('EV001', 'Randria', 'Lova', 2),
    ('EV002', 'Razafy', 'Aina', 1),
    ('EV003', 'Rabe', 'Tiana', 3);

-- Le premier seed stockait le clair password123 : même mot de passe, désormais BCrypt.
INSERT INTO utilisateur (username, password, id_role, id_employee)
SELECT
    v.username,
    '$2a$10$R1bL6beiLhGrSZpcjOjkLuewJDf83Fri1pLXTyKVsMMDbfO4pqhIu',
    r.id,
    v.id_employee
FROM (VALUES
    ('m_rakoto', 'ROLE_MANAGER', 'EMP001'),
    ('rh_marie', 'ROLE_RH', 'EMP002'),
    ('fin_hery', 'ROLE_FINANCE', 'EMP003'),
    ('dir_paul', 'ROLE_DIRECTION', 'EMP004')
) AS v(username, role_name, id_employee)
JOIN role_user r ON r.role_name = v.role_name;

-- Empreinte du seed actualisé conservée ; résolution du rôle par son nom.
INSERT INTO utilisateur (username, password, id_role, id_employee)
SELECT
    v.username,
    '$2a$10$y.p1DuAOKG6XCoM.ySMAOeAUQk6rNRFxCxhyKFXbF1G9rLNEVlZW2',
    r.id,
    v.id_employee
FROM (VALUES
    ('manager', 'ROLE_MANAGER', 'E001'),
    ('manager_mkt', 'ROLE_MANAGER', 'E005'),
    ('rh', 'ROLE_RH', 'E002'),
    ('finance', 'ROLE_FINANCE', 'E003'),
    ('direction', 'ROLE_DIRECTION', 'E004')
) AS v(username, role_name, id_employee)
JOIN role_user r ON r.role_name = v.role_name;

-- Besoin et deux offres du bloc 2 : identifiants 10/11 conservés.
INSERT INTO besoin (id, id_departement, type_motif, motif) VALUES
    (10, 1, 'NOUVEAU_POSTE', 'Renforcement de l’équipe informatique');

INSERT INTO besoin_poste (
    id, id_besoin, libelle_poste_propose, effectif, budget_mensuel_unitaire
) VALUES
    (10, 10, 'Développeur web', 1, 1500000.00),
    (11, 10, 'Technicien informatique', 1, 1000000.00);

INSERT INTO offre_emploi (
    id, id_besoin_poste, titre, description,
    date_creation, date_publication, date_limite, statut
) VALUES
    (10, 10, 'Développeur web', 'Développement et maintenance des applications web',
     CURRENT_DATE, CURRENT_DATE, CURRENT_DATE + 30, 'OUVERT'),
    (11, 11, 'Technicien informatique', 'Maintenance du matériel et assistance aux utilisateurs',
     CURRENT_DATE, CURRENT_DATE, CURRENT_DATE + 30, 'OUVERT');

-- Les candidats se connectent par leur adresse e-mail, sans employé lié avant embauche.
INSERT INTO candidat (nom, prenom, date_naissance, email, telephone, qualifications) VALUES
    ('Rasoa', 'Voahangy', '1990-05-15', 'candidat1@mail.com', '034 12 345 67', 'Licence en informatique'),
    ('Aaron', 'Tsangy', '1990-05-15', 'candidat2@mail.com', '034 12 445 67', 'Master en informatique'),
    ('Kot', 'Bekibo', '1990-05-15', 'candidat3@mail.com', '034 12 455 67', 'Chaauffeur vtc');

INSERT INTO utilisateur (username, password, id_role)
SELECT
    c.email,
    '$2a$10$y.p1DuAOKG6XCoM.ySMAOeAUQk6rNRFxCxhyKFXbF1G9rLNEVlZW2',
    r.id
FROM candidat c
JOIN role_user r ON r.role_name = 'CANDIDAT';

INSERT INTO test_recrutement (niveau, description, id_responsable) VALUES
    (1, 'Test de logique et de raisonnement', 'EV001'),
    (2, 'Test technique lié au poste', 'EV002'),
    (3, 'Mise en situation professionnelle', 'EV003');

-- Référentiel de décisions employé par le service existant (valeurs créées à la demande sinon).
INSERT INTO decision_candidature (label) VALUES
    ('RETENUE'),
    ('REJETEE'),
    ('RESERVE');

-- Recaler toutes les séquences SERIAL, y compris les tables encore vides.
-- Le prochain INSERT sans id utilisera MAX(id)+1 ; une table vide repart de 1.
DO $sequences$
DECLARE
    colonne RECORD;
    sequence_nom TEXT;
BEGIN
    FOR colonne IN
        SELECT table_schema, table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_default LIKE 'nextval(%'
        ORDER BY table_name, ordinal_position
    LOOP
        sequence_nom := pg_get_serial_sequence(
            format('%I.%I', colonne.table_schema, colonne.table_name),
            colonne.column_name
        );
        IF sequence_nom IS NOT NULL THEN
            EXECUTE format(
                'SELECT setval(%L, COALESCE(MAX(%I), 1), MAX(%I) IS NOT NULL) FROM %I.%I',
                sequence_nom,
                colonne.column_name,
                colonne.column_name,
                colonne.table_schema,
                colonne.table_name
            );
        END IF;
    END LOOP;
END;
$sequences$;

-- =============================================================================
-- 4. SCÉNARIOS D’INTÉGRATION SOA / RIJA / LOVA
-- Source : database/22-10-2026_02_bloc4_donnees_test.sql
-- Dates historiques de référence : 22 octobre 2026.
-- =============================================================================

DO $bloc4$
DECLARE
    v_dept_rh INTEGER;
    v_dept_it INTEGER;
    v_dept_projet INTEGER;
    v_dept_finance INTEGER;
    v_dept_formation INTEGER;
    v_poste_support INTEGER;
    v_poste_projet INTEGER;
    v_poste_dev INTEGER;
    v_poste_comptable INTEGER;
    v_besoin INTEGER;
    v_besoin_poste INTEGER;
    v_demande INTEGER;
    v_offre INTEGER;
    v_candidat INTEGER;
    v_candidature INTEGER;
    v_proposition INTEGER;
    v_contrat INTEGER;
    v_soa_cdd INTEGER;
    v_soa_cdi INTEGER;
    v_rija INTEGER;
    v_lova INTEGER;
    v_pc_partage INTEGER;
    v_pc_soa INTEGER;
    v_tel_soa INTEGER;
    v_tel_rija INTEGER;
    v_pc_lova INTEGER;
    v_formation_accueil INTEGER;
    v_formation_securite INTEGER;
    v_tache INTEGER;
    v_avant JSONB;
    v_apres JSONB;
    v_nouveau_statut VARCHAR(20);
    v_date_completion TIMESTAMPTZ;
    r RECORD;
    t RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM employe WHERE id LIKE 'B4%')
       OR EXISTS (SELECT 1 FROM utilisateur WHERE username LIKE 'b4_%') THEN
        RAISE EXCEPTION
            'Le prefixe B4 est deja utilise. Jeu non recharge ; aucune donnee modifiee.';
    END IF;

    -- 1. Referentiels et acteurs propres a ce jeu.
    INSERT INTO departement (libelle) VALUES ('B4 - Ressources humaines')
        RETURNING id INTO v_dept_rh;
    INSERT INTO departement (libelle) VALUES ('B4 - Informatique')
        RETURNING id INTO v_dept_it;
    INSERT INTO departement (libelle) VALUES ('B4 - Projets')
        RETURNING id INTO v_dept_projet;
    INSERT INTO departement (libelle) VALUES ('B4 - Finance')
        RETURNING id INTO v_dept_finance;
    INSERT INTO departement (libelle) VALUES ('B4 - Formation')
        RETURNING id INTO v_dept_formation;

    INSERT INTO poste (id_departement, libelle)
        VALUES (v_dept_it, 'B4 - Technicien support')
        RETURNING id INTO v_poste_support;
    INSERT INTO poste (id_departement, libelle)
        VALUES (v_dept_projet, 'B4 - Chef de projet')
        RETURNING id INTO v_poste_projet;
    INSERT INTO poste (id_departement, libelle)
        VALUES (v_dept_it, 'B4 - Developpeur')
        RETURNING id INTO v_poste_dev;
    INSERT INTO poste (id_departement, libelle)
        VALUES (v_dept_finance, 'B4 - Comptable')
        RETURNING id INTO v_poste_comptable;

    INSERT INTO grille_salaire (id_poste, salaire_min, salaire_max) VALUES
        (v_poste_support, 600000, 1000000),
        (v_poste_projet, 1300000, 2000000),
        (v_poste_dev, 1000000, 1500000),
        (v_poste_comptable, 800000, 1200000);

    INSERT INTO employe (id, nom, prenom, id_departement) VALUES
        ('B4RH001', 'Rakoto', 'Ravo', v_dept_rh),
        ('B4IT001', 'Andria', 'Hery', v_dept_it),
        ('B4FOR001', 'Rabe', 'Fara', v_dept_formation),
        ('B4PAY001', 'Rasoa', 'Noro', v_dept_finance),
        ('B4DIR001', 'Ranaivo', 'Mamy', v_dept_projet);

    INSERT INTO role_user (role_name) VALUES
        ('ROLE_RH'), ('ROLE_MANAGER'), ('ROLE_FINANCE'), ('ROLE_DIRECTION')
        ON CONFLICT (role_name) DO NOTHING;

    INSERT INTO utilisateur (username, password, id_role, id_employee)
    SELECT
        u.username,
        '$2b$10$g5eS53ZApEJZyD/El3Aeiu2XR0d8.HZKHnemFfu7h9TlHsFblsjL6',
        ro.id,
        u.id_employe
    FROM (VALUES
        ('b4_rh', 'ROLE_RH', 'B4RH001'),
        ('b4_it', 'ROLE_MANAGER', 'B4IT001'),
        ('b4_formation', 'ROLE_MANAGER', 'B4FOR001'),
        ('b4_paie', 'ROLE_FINANCE', 'B4PAY001'),
        ('b4_direction', 'ROLE_DIRECTION', 'B4DIR001')
    ) AS u(username, role_name, id_employe)
    JOIN role_user ro ON ro.role_name = u.role_name;

    -- 2. Trois embauches, avec la chaine FK des modules 1-3.
    FOR r IN
        SELECT * FROM (VALUES
            ('B4N001', 'Rakotobe', 'Soa', 'soa.b4@example.test',
             v_dept_it, v_poste_support, 800000, 'CDD',
             DATE '2026-04-01', DATE '2026-09-30', DATE '2026-03-25',
             'TERMINE', DATE '2026-03-01'),
            ('B4N002', 'Ramanana', 'Rija', 'rija.b4@example.test',
             v_dept_it, v_poste_dev, 1200000, 'CDD',
             DATE '2026-10-15', DATE '2027-04-14', DATE '2026-10-10',
             'SIGNE', DATE '2026-09-20'),
            ('B4N003', 'Rasolon', 'Lova', 'lova.b4@example.test',
             v_dept_finance, v_poste_comptable, 900000, 'CDI',
             DATE '2026-11-01', NULL::DATE, NULL::DATE,
             'BROUILLON', DATE '2026-10-01')
        ) AS x(id_employe, nom, prenom, email, id_departement, id_poste,
               salaire, type_contrat, date_debut, date_fin, date_signature,
               statut_contrat, date_demande)
    LOOP
        -- Soa est aujourd'hui au departement Projets, apres sa mobilite.
        INSERT INTO employe (id, nom, prenom, id_departement)
        VALUES (r.id_employe, r.nom, r.prenom,
                CASE WHEN r.id_employe = 'B4N001'
                     THEN v_dept_projet ELSE r.id_departement END);

        INSERT INTO besoin (id_departement, type_motif, motif)
        VALUES (r.id_departement, 'NOUVEAU_POSTE',
                'B4 - Recrutement de test pour ' || r.prenom)
        RETURNING id INTO v_besoin;

        INSERT INTO besoin_poste
            (id_besoin, id_poste, effectif, budget_mensuel_unitaire)
        VALUES (v_besoin, r.id_poste, 1, r.salaire)
        RETURNING id INTO v_besoin_poste;

        INSERT INTO demande_recrutement
            (id_besoin, id_demandeur, date_creation, date_arrivee_souhaitee,
             circuit, validation_finance, statut)
        VALUES (
            v_besoin,
            CASE WHEN r.id_employe = 'B4N003' THEN 'B4PAY001' ELSE 'B4IT001' END,
            r.date_demande, r.date_debut, 'STANDARD', TRUE, 'VALIDEE'
        ) RETURNING id INTO v_demande;

        INSERT INTO historique_validation
            (id_demande, id_validateur, etape, decision, date_validation)
        VALUES
            (v_demande, 'B4RH001', 'RH', 'APPROUVEE', r.date_demande + INTERVAL '1 day'),
            (v_demande, 'B4PAY001', 'FINANCE', 'APPROUVEE', r.date_demande + INTERVAL '2 days'),
            (v_demande, 'B4DIR001', 'DIRECTION', 'APPROUVEE', r.date_demande + INTERVAL '3 days');

        INSERT INTO offre_emploi
            (id_besoin_poste, titre, description, date_creation,
             date_publication, date_limite, statut)
        VALUES (v_besoin_poste, 'B4 - Poste pour ' || r.prenom,
                'Offre fictive necessaire aux tests du bloc 4',
                r.date_demande + 4, r.date_demande + 5,
                r.date_demande + 12, 'CLOTUREE')
        RETURNING id INTO v_offre;

        INSERT INTO candidat (nom, prenom, email)
        VALUES (r.nom, r.prenom, r.email)
        RETURNING id INTO v_candidat;

        INSERT INTO candidature
            (id_candidat, id_offre, cv, date_candidature, statut)
        VALUES (v_candidat, v_offre, 'tests/bloc4/cv_' || r.id_employe || '.pdf',
                r.date_demande + INTERVAL '7 days', 'RETENUE')
        RETURNING id INTO v_candidature;

        INSERT INTO proposition_embauche
            (id_candidature, id_emetteur, salaire_mensuel, date_debut_prevue,
             type_contrat, horaires, lieu_travail, date_envoi, date_reponse, statut)
        VALUES (v_candidature, 'B4RH001', r.salaire, r.date_debut,
                r.type_contrat, '40 heures par semaine', 'Antananarivo',
                r.date_demande + INTERVAL '14 days',
                r.date_demande + INTERVAL '17 days', 'ACCEPTEE')
        RETURNING id INTO v_proposition;

        INSERT INTO contrat
            (id_proposition, id_employe, id_poste, type_contrat,
             date_debut, date_fin, date_signature, salaire_mensuel,
             horaires, lieu_travail, date_creation, statut)
        VALUES (v_proposition, r.id_employe, r.id_poste, r.type_contrat,
                r.date_debut, r.date_fin, r.date_signature, r.salaire,
                '40 heures par semaine', 'Antananarivo',
                r.date_demande + 17, r.statut_contrat)
        RETURNING id INTO v_contrat;

        INSERT INTO document_contrat (id_contrat, type_document, reference_fichier, date_ajout)
        VALUES (v_contrat, 'CONTRAT_PDF',
                'tests/bloc4/' || r.id_employe || '_initial.pdf',
                r.date_demande + INTERVAL '17 days');

        IF r.date_signature IS NOT NULL THEN
            INSERT INTO document_contrat
                (id_contrat, type_document, reference_fichier, date_ajout)
            VALUES (v_contrat, 'CONTRAT_SIGNE',
                    'tests/bloc4/' || r.id_employe || '_initial_signe.pdf',
                    r.date_signature);
        END IF;

        INSERT INTO affectation
            (id_employe, id_contrat, id_poste, lieu, date_debut, date_fin, motif)
        VALUES (
            r.id_employe, v_contrat, r.id_poste, 'Antananarivo - Siege',
            r.date_debut,
            CASE
                WHEN r.id_employe = 'B4N001' THEN DATE '2026-09-30'
                WHEN r.id_employe = 'B4N002' THEN DATE '2026-10-19'
                ELSE NULL
            END,
            'B4 - Affectation initiale'
        );

        IF r.id_employe = 'B4N001' THEN v_soa_cdd := v_contrat;
        ELSIF r.id_employe = 'B4N002' THEN v_rija := v_contrat;
        ELSE v_lova := v_contrat;
        END IF;
    END LOOP;

    -- 3. Renouvellement/passage au CDI : aucune nouvelle proposition.
    INSERT INTO contrat
        (id_employe, id_contrat_precedent, id_poste, type_contrat,
         date_debut, date_signature, salaire_mensuel, horaires,
         lieu_travail, date_creation, statut)
    VALUES ('B4N001', v_soa_cdd, v_poste_projet, 'CDI',
            '2026-10-01', '2026-09-25', 1500000, '40 heures par semaine',
            'Antananarivo - Annexe', '2026-09-20', 'SIGNE')
    RETURNING id INTO v_soa_cdi;

    INSERT INTO document_contrat
        (id_contrat, type_document, reference_fichier, date_ajout)
    VALUES
        (v_soa_cdi, 'CONTRAT_PDF', 'tests/bloc4/B4N001_cdi.pdf', '2026-09-20'),
        (v_soa_cdi, 'CONTRAT_SIGNE', 'tests/bloc4/B4N001_cdi_signe.pdf', '2026-09-25');

    INSERT INTO affectation
        (id_employe, id_contrat, id_poste, lieu, date_debut, motif)
    VALUES
        ('B4N001', v_soa_cdi, v_poste_projet, 'Antananarivo - Annexe',
         '2026-10-01', 'B4 - Passage CDI et mobilite vers Projets'),
        ('B4N002', v_rija, v_poste_dev, 'Antananarivo - Annexe',
         '2026-10-20', 'B4 - Changement de lieu de travail');

    -- 4. Comptes, badges et materiels reellement remis.
    INSERT INTO compte_employe (id_employe, systeme, identifiant, date_activation) VALUES
        ('B4N001', 'Messagerie', 'soa.b4@example.test', '2026-04-01'),
        ('B4N001', 'ERP', 'b4_soa_erp', '2026-04-02'),
        ('B4N002', 'Messagerie', 'rija.b4@example.test', '2026-10-15');

    INSERT INTO badge_employe
        (id_employe, numero_badge, date_remise, date_restitution)
    VALUES
        ('B4N001', 'B4-B001', '2026-04-01', '2026-09-30'),
        ('B4N001', 'B4-B002', '2026-10-01', NULL),
        ('B4N002', 'B4-B001', '2026-10-15', NULL);

    INSERT INTO materiel (nom) VALUES ('B4 - Ordinateur inventaire 001')
        RETURNING id INTO v_pc_partage;
    INSERT INTO materiel (nom) VALUES ('B4 - Ordinateur inventaire 002')
        RETURNING id INTO v_pc_soa;
    INSERT INTO materiel (nom) VALUES ('B4 - Telephone inventaire 003')
        RETURNING id INTO v_tel_soa;
    INSERT INTO materiel (nom) VALUES ('B4 - Telephone inventaire 004')
        RETURNING id INTO v_tel_rija;
    INSERT INTO materiel (nom) VALUES ('B4 - Ordinateur inventaire 005')
        RETURNING id INTO v_pc_lova;

    INSERT INTO materiel_employe
        (id_employe, id_materiel, date_reception, date_restitution)
    VALUES
        ('B4N001', v_pc_partage, '2026-04-01', '2026-09-30'),
        ('B4N002', v_pc_partage, '2026-10-15', NULL),
        ('B4N001', v_pc_soa, '2026-10-01', NULL),
        ('B4N001', v_tel_soa, '2026-04-01', NULL);
    -- Le telephone de Rija et le PC de Lova existent mais ne sont pas encore remis.

    INSERT INTO formation (nom) VALUES ('B4 - Accueil et procedures internes')
        RETURNING id INTO v_formation_accueil;
    INSERT INTO formation (nom) VALUES ('B4 - Securite informatique')
        RETURNING id INTO v_formation_securite;

    INSERT INTO formation_employe (id_employe, id_formation, date_completion) VALUES
        ('B4N001', v_formation_accueil, '2026-04-02'),
        ('B4N001', v_formation_securite, '2026-04-03'),
        ('B4N002', v_formation_securite, NULL);

    INSERT INTO inscription_paie
        (id_employe, date_inscription, date_debut_prise_en_charge)
    VALUES ('B4N001', '2026-03-30', '2026-04-01');
    -- Un seul dossier pour Soa malgre ses deux contrats.

    -- 5. Creation des taches : toutes commencent A_FAIRE.
    -- Les taches de Soa restent liees au contrat d'entree (le CDD).
    INSERT INTO onboarding_tache
        (id_contrat, etape, libelle, id_assignateur, id_responsable)
    VALUES
        (v_soa_cdd, 'SIGNATURE_CONTRAT', 'Confirmer la signature du contrat', 'B4RH001', 'B4RH001'),
        (v_soa_cdd, 'BADGE', 'Remettre le badge', 'B4RH001', 'B4IT001'),
        (v_soa_cdd, 'COMPTE', 'Activer la messagerie', 'B4RH001', 'B4IT001'),
        (v_soa_cdd, 'COMPTE', 'Activer le compte ERP', 'B4RH001', 'B4IT001'),
        (v_soa_cdd, 'MATERIEL', 'Remettre l ordinateur', 'B4RH001', 'B4IT001'),
        (v_soa_cdd, 'MATERIEL', 'Remettre le telephone', 'B4RH001', 'B4IT001'),
        (v_soa_cdd, 'FORMATION', 'Terminer la formation accueil', 'B4RH001', 'B4FOR001'),
        (v_soa_cdd, 'FORMATION', 'Terminer la formation securite', 'B4RH001', 'B4FOR001'),
        (v_soa_cdd, 'INSCRIPTION_PAIE', 'Confirmer l inscription a la paie', 'B4RH001', 'B4PAY001'),

        (v_rija, 'SIGNATURE_CONTRAT', 'Confirmer la signature du contrat', 'B4RH001', 'B4RH001'),
        (v_rija, 'BADGE', 'Remettre le badge', 'B4RH001', 'B4IT001'),
        (v_rija, 'COMPTE', 'Activer la messagerie', 'B4RH001', 'B4IT001'),
        (v_rija, 'MATERIEL', 'Remettre l ordinateur', 'B4RH001', 'B4IT001'),
        (v_rija, 'MATERIEL', 'Remettre le telephone', 'B4RH001', 'B4RH001'),
        (v_rija, 'FORMATION', 'Terminer la formation securite', 'B4RH001', 'B4FOR001'),
        (v_rija, 'INSCRIPTION_PAIE', 'Confirmer l inscription a la paie', 'B4RH001', 'B4PAY001'),

        (v_lova, 'SIGNATURE_CONTRAT', 'Faire signer le contrat', 'B4RH001', 'B4RH001'),
        (v_lova, 'BADGE', 'Remettre le badge', 'B4RH001', 'B4IT001'),
        (v_lova, 'COMPTE', 'Activer la messagerie', 'B4RH001', 'B4IT001'),
        (v_lova, 'MATERIEL', 'Remettre l ordinateur', 'B4RH001', 'B4IT001'),
        (v_lova, 'FORMATION', 'Terminer la formation accueil', 'B4RH001', 'B4FOR001'),
        (v_lova, 'INSCRIPTION_PAIE', 'Confirmer l inscription a la paie', 'B4RH001', 'B4PAY001');

    -- 6. Traces des creations du jeu. Les objets de catalogue n'ont pas
    -- d'employe concerne particulier. Le compte utilisateur/password est exclu.
    INSERT INTO mouvement_rh
        (id_employe_concerne, id_acteur, table_concernee,
         id_ligne, action, valeurs_apres)
    SELECT e.id, 'B4RH001', 'employe', e.id, 'CREATION', to_jsonb(e)
        FROM employe e WHERE e.id LIKE 'B4%'
    UNION ALL
    SELECT c.id_employe, 'B4RH001', 'contrat', c.id::TEXT, 'CREATION', to_jsonb(c)
        FROM contrat c WHERE c.id_employe IN ('B4N001', 'B4N002', 'B4N003')
    UNION ALL
    SELECT c.id_employe, 'B4RH001', 'document_contrat', d.id::TEXT, 'CREATION', to_jsonb(d)
        FROM document_contrat d JOIN contrat c ON c.id = d.id_contrat
        WHERE c.id_employe IN ('B4N001', 'B4N002', 'B4N003')
    UNION ALL
    SELECT a.id_employe, 'B4RH001', 'affectation', a.id::TEXT, 'CREATION', to_jsonb(a)
        FROM affectation a WHERE a.id_employe IN ('B4N001', 'B4N002', 'B4N003')
    UNION ALL
    SELECT c.id_employe, 'B4IT001', 'compte_employe', c.id::TEXT, 'CREATION', to_jsonb(c)
        FROM compte_employe c WHERE c.id_employe IN ('B4N001', 'B4N002')
    UNION ALL
    SELECT b.id_employe, 'B4IT001', 'badge_employe', b.id::TEXT, 'CREATION', to_jsonb(b)
        FROM badge_employe b WHERE b.id_employe IN ('B4N001', 'B4N002')
    UNION ALL
    SELECT NULL::VARCHAR, 'B4IT001', 'materiel', m.id::TEXT, 'CREATION', to_jsonb(m)
        FROM materiel m WHERE m.id IN (v_pc_partage, v_pc_soa, v_tel_soa, v_tel_rija, v_pc_lova)
    UNION ALL
    SELECT m.id_employe, 'B4IT001', 'materiel_employe', m.id::TEXT, 'CREATION', to_jsonb(m)
        FROM materiel_employe m WHERE m.id_employe IN ('B4N001', 'B4N002')
    UNION ALL
    SELECT NULL::VARCHAR, 'B4FOR001', 'formation', f.id::TEXT, 'CREATION', to_jsonb(f)
        FROM formation f WHERE f.id IN (v_formation_accueil, v_formation_securite)
    UNION ALL
    SELECT f.id_employe, 'B4FOR001', 'formation_employe', f.id::TEXT, 'CREATION', to_jsonb(f)
        FROM formation_employe f WHERE f.id_employe IN ('B4N001', 'B4N002')
    UNION ALL
    SELECT p.id_employe, 'B4PAY001', 'inscription_paie', p.id::TEXT, 'CREATION', to_jsonb(p)
        FROM inscription_paie p WHERE p.id_employe = 'B4N001'
    UNION ALL
    SELECT c.id_employe, ot.id_assignateur, 'onboarding_tache', ot.id::TEXT, 'CREATION', to_jsonb(ot)
        FROM onboarding_tache ot JOIN contrat c ON c.id = ot.id_contrat
        WHERE c.id_employe IN ('B4N001', 'B4N002', 'B4N003');

    -- 7. Exemple de reassignation : le RH confie le telephone de Rija a l'IT.
    SELECT id, to_jsonb(onboarding_tache) INTO v_tache, v_avant
    FROM onboarding_tache
    WHERE id_contrat = v_rija AND libelle = 'Remettre le telephone';

    UPDATE onboarding_tache SET id_responsable = 'B4IT001' WHERE id = v_tache;
    SELECT to_jsonb(onboarding_tache) INTO v_apres
        FROM onboarding_tache WHERE id = v_tache;

    INSERT INTO mouvement_rh
        (id_employe_concerne, id_acteur, table_concernee, id_ligne,
         action, valeurs_avant, valeurs_apres)
    VALUES ('B4N002', 'B4RH001', 'onboarding_tache', v_tache::TEXT,
            'MODIFICATION', v_avant, v_apres);

    -- 8. Avancement des taches, avec une trace pour CHAQUE modification.
    FOR t IN
        SELECT x.* FROM onboarding_tache x
        WHERE x.id_contrat IN (v_soa_cdd, v_rija)
        ORDER BY x.id
    LOOP
        v_nouveau_statut := 'A_FAIRE';
        v_date_completion := NULL;

        IF t.id_contrat = v_soa_cdd THEN
            v_nouveau_statut := 'TERMINEE';
            v_date_completion := TIMESTAMPTZ '2026-04-03 16:00:00+03';
        ELSIF t.etape IN ('SIGNATURE_CONTRAT', 'BADGE', 'COMPTE')
              OR (t.etape = 'MATERIEL' AND t.libelle = 'Remettre l ordinateur') THEN
            v_nouveau_statut := 'TERMINEE';
            v_date_completion := TIMESTAMPTZ '2026-10-16 16:00:00+03';
        ELSIF t.etape = 'MATERIEL' THEN
            v_nouveau_statut := 'BLOQUEE';
        ELSIF t.etape = 'FORMATION' THEN
            v_nouveau_statut := 'EN_COURS';
        END IF;

        IF v_nouveau_statut <> 'A_FAIRE' THEN
            v_avant := to_jsonb(t);
            UPDATE onboarding_tache
                SET statut = v_nouveau_statut, date_completion = v_date_completion
                WHERE id = t.id;
            SELECT to_jsonb(onboarding_tache) INTO v_apres
                FROM onboarding_tache WHERE id = t.id;

            INSERT INTO mouvement_rh
                (id_employe_concerne, id_acteur, table_concernee, id_ligne,
                 action, valeurs_avant, valeurs_apres)
            VALUES (
                CASE WHEN t.id_contrat = v_soa_cdd THEN 'B4N001' ELSE 'B4N002' END,
                t.id_responsable, 'onboarding_tache', t.id::TEXT,
                'MODIFICATION', v_avant, v_apres
            );
        END IF;
    END LOOP;
END;
$bloc4$;

-- =============================================================================
-- 5. ACCÈS SALARIÉS ET RESYNCHRONISATION FINALE
-- Source : database/22-10-2026_03_bloc4_acces_salaries_test.sql
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM utilisateur WHERE username='b4_rh') THEN
        RAISE EXCEPTION 'Charger d’abord les fixtures du bloc 4.';
    END IF;
END $$;
INSERT INTO role_user(role_name) VALUES ('ROLE_SALARIE') ON CONFLICT (role_name) DO NOTHING;
INSERT INTO utilisateur(username,password,id_role,id_employee)
SELECT v.login,u.password,r.id,v.employe
FROM (VALUES ('b4_soa','B4N001'),('b4_rija','B4N002'),('b4_lova','B4N003')) v(login,employe)
JOIN employe e ON e.id=v.employe
CROSS JOIN utilisateur u CROSS JOIN role_user r
WHERE u.username='b4_rh' AND r.role_name='ROLE_SALARIE'
ON CONFLICT DO NOTHING;

-- Recaler toutes les séquences SERIAL, y compris les tables encore vides.
-- Le prochain INSERT sans id utilisera MAX(id)+1 ; une table vide repart de 1.
DO $sequences$
DECLARE
    colonne RECORD;
    sequence_nom TEXT;
BEGIN
    FOR colonne IN
        SELECT table_schema, table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_default LIKE 'nextval(%'
        ORDER BY table_name, ordinal_position
    LOOP
        sequence_nom := pg_get_serial_sequence(
            format('%I.%I', colonne.table_schema, colonne.table_name),
            colonne.column_name
        );
        IF sequence_nom IS NOT NULL THEN
            EXECUTE format(
                'SELECT setval(%L, COALESCE(MAX(%I), 1), MAX(%I) IS NOT NULL) FROM %I.%I',
                sequence_nom,
                colonne.column_name,
                colonne.column_name,
                colonne.table_schema,
                colonne.table_name
            );
        END IF;
    END LOOP;
END;
$sequences$;

-- =============================================================================
-- 6. CONTRÔLES AVANT VALIDATION DE LA TRANSACTION
-- =============================================================================
DO $controles$
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE') <> 31 THEN
        RAISE EXCEPTION 'Schéma incomplet : 31 tables attendues.';
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.views WHERE table_schema = 'public') <> 2 THEN
        RAISE EXCEPTION 'Schéma incomplet : 2 vues de suivi attendues.';
    END IF;

    IF (SELECT COUNT(*) FROM employe) <> 20
       OR (SELECT COUNT(*) FROM utilisateur) <> 20
       OR (SELECT COUNT(*) FROM candidat) <> 6
       OR (SELECT COUNT(*) FROM role_user) <> 6
       OR (SELECT COUNT(*) FROM poste) <> 13 THEN
        RAISE EXCEPTION 'Chargement incomplet des référentiels, employés, candidats ou comptes.';
    END IF;

    IF (SELECT COUNT(*) FROM contrat) <> 4
       OR (SELECT COUNT(*) FROM affectation) <> 5
       OR (SELECT COUNT(*) FROM document_contrat) <> 7
       OR (SELECT COUNT(*) FROM onboarding_tache) <> 22
       OR (SELECT COUNT(*) FROM mouvement_rh) <> 83 THEN
        RAISE EXCEPTION 'Chargement incomplet des scénarios du bloc 4.';
    END IF;

    IF (SELECT COUNT(*) FROM suivi_onboarding) <> 3
       OR NOT EXISTS (
           SELECT 1 FROM suivi_onboarding
           WHERE id_employe = 'B4N001' AND statut = 'TERMINE' AND nombre_etapes_terminees = 6
       )
       OR NOT EXISTS (
           SELECT 1 FROM suivi_onboarding
           WHERE id_employe = 'B4N002' AND statut = 'BLOQUE' AND nombre_etapes_terminees = 3
       )
       OR NOT EXISTS (
           SELECT 1 FROM suivi_onboarding
           WHERE id_employe = 'B4N003' AND statut = 'A_FAIRE' AND nombre_etapes_terminees = 0
       ) THEN
        RAISE EXCEPTION 'Les suivis calculés ne correspondent pas aux scénarios de référence.';
    END IF;

    IF (SELECT COUNT(*) FROM utilisateur u
        JOIN role_user r ON r.id = u.id_role
        WHERE u.id_employee IN ('B4N001', 'B4N002', 'B4N003')
          AND r.role_name = 'ROLE_SALARIE') <> 3 THEN
        RAISE EXCEPTION 'Accès salariés B4 manquants ou rôle incorrect.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM utilisateur
        WHERE password !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
    ) THEN
        RAISE EXCEPTION 'Un mot de passe de démonstration n’est pas stocké en BCrypt.';
    END IF;
END;
$controles$;

COMMIT;

\echo Base :db_name reconstruite et données chargées avec succès.

-- Résumé lisible après l'installation.
SELECT 'employe' AS objet, COUNT(*) AS nombre FROM employe
UNION ALL SELECT 'utilisateur', COUNT(*) FROM utilisateur
UNION ALL SELECT 'candidat', COUNT(*) FROM candidat
UNION ALL SELECT 'offre_emploi', COUNT(*) FROM offre_emploi
UNION ALL SELECT 'contrat', COUNT(*) FROM contrat
UNION ALL SELECT 'affectation', COUNT(*) FROM affectation
UNION ALL SELECT 'onboarding_tache', COUNT(*) FROM onboarding_tache
UNION ALL SELECT 'mouvement_rh', COUNT(*) FROM mouvement_rh
ORDER BY objet;

SELECT id_employe, id_contrat, statut, nombre_etapes_terminees, nombre_etapes
FROM suivi_onboarding
ORDER BY id_employe;
