# Remarques a moi meme et fanontaniana pour les autres
- id_employe zany varchar fa tsy integer
- le id_proposition ao am contrat tokony tsy "not null" satria tsy voatery proposition no nahazoana contrat fa mety renouvellement koa
- de asiana id_contrat_precedent ahafantarana ny contrat teo aloha
- affectation misy id_contrat ahafantaran ny origine any
- voatery misy id_departement ve ao am affectation? satria manko id_poste efa manana id_departement de mety tsy voatery ilaina fa confirmeo ihany
- de le resaka hoe traçabilité des mouvements aona ny hanaovana azy? pour info efa nipropose table MouvementRh i chat 🥴 fa mbola tsy nasiako lo fa mila resahana aminareo
```sql
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
```