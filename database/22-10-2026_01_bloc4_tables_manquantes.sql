-- BLOC 4 : TABLES MANQUANTES ET VUES DE SUIVI
-- A executer APRES SIGRH.sql fourni par le groupe.
-- Ne recree pas contrat, affectation, document_contrat, employe, poste,
-- departement, role_user ni utilisateur.
-- Script a executer une fois ; ce n'est pas une migration depuis nos anciens essais.
--
-- COHERENCE AVEC SIGRH.sql :
-- * Toutes les references a employe(id) utilisent VARCHAR(10).
-- * Les acteurs sont des employes ; l'utilisateur connecte se retrouve via
--   utilisateur.id_employee. Les droits sont controles dans le serveur.
-- * Le contrat d'entree identifie le parcours d'integration, sans tables
--   onboarding/onboarding_etape. Un renouvellement n'en cree pas un autre.
-- * Les six etapes sont fixes. Chaque tache creee est necessaire.
-- * Les documents restent dans document_contrat.
-- * A la mobilite, synchroniser employe.id_departement avec le nouveau poste.
-- * Verifier dans le serveur : contrat precedent/affectation du meme employe,
--   absence de cycle et de chevauchement, signature effective si statut SIGNE.
--
-- Journal : aucun trigger n'est installe. Le code serveur enregistre
-- l'action metier ET mouvement_rh dans la meme transaction.

BEGIN;

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

COMMIT;

-- Exemples de lecture :
-- SELECT * FROM suivi_onboarding ORDER BY id_employe, id_contrat;
-- SELECT * FROM suivi_onboarding_etape WHERE id_employe = 'B4N002';
-- SELECT * FROM mouvement_rh WHERE id_employe_concerne = 'B4N002'
-- ORDER BY date_mouvement, id;

