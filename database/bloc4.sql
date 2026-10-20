CREATE TABLE departement (
    id SERIAL PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE poste (
    id SERIAL PRIMARY KEY,
    id_departement INTEGER NOT NULL REFERENCES departement(id),
    libelle VARCHAR(100) NOT NULL
);

CREATE TABLE employe (
    id SERIAL PRIMARY KEY,
    matricule VARCHAR(10) NOT NULL UNIQUE,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100)
);

CREATE TABLE contrat (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    id_proposition INTEGER UNIQUE REFERENCES proposition_embauche(id),
    id_contrat_precedent INTEGER REFERENCES contrat(id),
    id_poste INTEGER NOT NULL REFERENCES poste(id),
    type_contrat VARCHAR(30) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    date_fin_effective DATE,
    salaire_mensuel NUMERIC(14,2) NOT NULL CHECK (salaire_mensuel >= 0),
    date_signature DATE,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON'
        CHECK (statut IN ('BROUILLON', 'SIGNE', 'TERMINE', 'ANNULE')),
    CHECK (date_fin IS NULL OR date_fin >= date_debut),
    CHECK (date_fin_effective IS NULL OR date_fin_effective >= date_debut),
    CHECK (id_contrat_precedent IS NULL OR id_contrat_precedent <> id),
    CHECK (statut NOT IN ('SIGNE', 'TERMINE') OR date_signature IS NOT NULL)
);

-- date_fin = fin prevue au contrat ; date_fin_effective = cloture reelle.
-- Le departement d'une affectation se deduit de poste.id_departement.
-- Ne pas changer le departement d'un poste deja utilise dans l'historique.
CREATE TABLE affectation (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    id_poste INTEGER NOT NULL REFERENCES poste(id),
    date_debut DATE NOT NULL,
    date_fin DATE,
    CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

CREATE TABLE document (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    id_contrat INTEGER REFERENCES contrat(id),
    type_document VARCHAR(30) NOT NULL,
    reference_fichier TEXT NOT NULL UNIQUE
);

-- Suivi des comptes informatiques deja operationnels, sans mot de passe.
CREATE TABLE compte_employe (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    systeme VARCHAR(100) NOT NULL,
    identifiant VARCHAR(150) NOT NULL,
    date_activation DATE NOT NULL,
    UNIQUE (systeme, identifiant)
);

CREATE TABLE badge_employe (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    numero_badge VARCHAR(50) NOT NULL,
    date_remise DATE NOT NULL,
    date_restitution DATE,
    CHECK (date_restitution IS NULL OR date_restitution >= date_remise)
);

-- Un numero peut etre reutilise apres restitution, pas avant.
CREATE UNIQUE INDEX badge_non_restitue_unique
    ON badge_employe (numero_badge)
    WHERE date_restitution IS NULL;

-- Une ligne represente un objet physique, pas un type de materiel.
CREATE TABLE materiel (
    id SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL
);

CREATE TABLE materiel_employe (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    id_materiel INTEGER NOT NULL REFERENCES materiel(id),
    date_reception DATE NOT NULL,
    date_restitution DATE,
    CHECK (date_restitution IS NULL OR date_restitution >= date_reception)
);

CREATE UNIQUE INDEX materiel_non_restitue_unique
    ON materiel_employe (id_materiel)
    WHERE date_restitution IS NULL;

CREATE TABLE formation (
    id SERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL
);

-- NULL = formation pas encore terminee.
-- Pas d'unicite employe/formation : la formation peut etre suivie de nouveau.
CREATE TABLE formation_employe (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    id_formation INTEGER NOT NULL REFERENCES formation(id),
    date_completion DATE
);

-- Une ligne est creee lorsque l'inscription a la paie est CONFIRMEE.
-- La preparation et la transmission sont suivies par la tache d'onboarding.
-- Un renouvellement de contrat ne recree pas automatiquement cette inscription.
CREATE TABLE inscription_paie (
    id SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id),
    date_inscription DATE NOT NULL,
    date_debut_prise_en_charge DATE NOT NULL,
    UNIQUE (id_employe, date_debut_prise_en_charge)
);

-- L'employe se retrouve via contrat.id_employe : pas de colonne dupliquee.
-- Une reembauche peut avoir un nouvel onboarding lie a son nouveau contrat.
CREATE TABLE onboarding (
    id SERIAL PRIMARY KEY,
    id_contrat INTEGER NOT NULL UNIQUE REFERENCES contrat(id)
);

CREATE TABLE onboarding_etape (
    id SERIAL PRIMARY KEY,
    id_onboarding INTEGER NOT NULL REFERENCES onboarding(id),
    code VARCHAR(30) NOT NULL CHECK (code IN (
        'SIGNATURE_CONTRAT', 'BADGE', 'COMPTE',
        'MATERIEL', 'FORMATION', 'INSCRIPTION_PAIE'
    )),
    UNIQUE (id_onboarding, code)
);

CREATE TABLE onboarding_tache (
    id SERIAL PRIMARY KEY,
    id_etape INTEGER NOT NULL REFERENCES onboarding_etape(id),
    libelle VARCHAR(200) NOT NULL,
    id_assignateur INTEGER NOT NULL REFERENCES employe(id),
    id_responsable INTEGER NOT NULL REFERENCES employe(id),
    statut VARCHAR(20) NOT NULL DEFAULT 'A_FAIRE'
        CHECK (statut IN ('A_FAIRE', 'EN_COURS', 'BLOQUEE', 'TERMINEE')),
    date_completion TIMESTAMPTZ,
    CHECK ((statut = 'TERMINEE') = (date_completion IS NOT NULL))
);