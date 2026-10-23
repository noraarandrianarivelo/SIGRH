-- BLOC 4 : DONNEES DE TEST
-- Ordre : SIGRH.sql -> 01_bloc4_tables_manquantes.sql -> ce fichier.
-- Cree ses propres prerequis minimaux des modules 1-3, sans modifier les
-- donnees deja presentes. Identifiants des employes reserves : prefixe B4.
-- Le script refuse une deuxieme execution pour eviter de dupliquer le jeu.
-- Tout est dans une seule transaction ; aucune suppression/TRUNCATE.
-- Les identifiants SERIAL sont recuperes avec RETURNING, pas codes en dur.
--
-- Scenarios, photographies fictives au 22 octobre 2026 :
-- B4N001 / Soa : ancien CDD, CDI actuel, mobilite, onboarding termine.
-- B4N002 / Rija : CDD signe, materiel partiel, formation en cours, paie non inscrite.
-- B4N003 / Lova : CDI futur en brouillon, toutes les taches a faire.
--
-- Comptes de test pour les acteurs (bcrypt) :
-- b4_rh, b4_it, b4_formation, b4_paie, b4_direction.
-- Mot de passe de test commun : Bloc4Test2026!
-- Les trois nouvelles recrues n'ont pas de compte de connexion dans ce jeu.
-- Les chemins PDF sont fictifs : aucun fichier PDF n'est genere par ces INSERT.
--
-- Les etats historiques charges sont journalises comme creations de donnees
-- de test au moment du chargement. Les transitions de taches sont ensuite
-- executees et journalisees explicitement : aucun trigger n'est necessaire.

BEGIN;

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

COMMIT;

-- Requetes de verification et resultats attendus :
-- SELECT * FROM suivi_onboarding WHERE id_employe IN ('B4N001','B4N002','B4N003');
--   B4N001 : TERMINE, 6/6 etapes ; rattachee au CDD initial.
--   B4N002 : BLOQUE, 3/6 etapes (signature, badge, compte).
--   B4N003 : A_FAIRE, 0/6 etapes.
--
-- SELECT * FROM suivi_onboarding_etape WHERE id_employe = 'B4N002';
-- SELECT t.* FROM onboarding_tache t JOIN contrat c ON c.id = t.id_contrat
-- WHERE c.id_employe = 'B4N002';
-- SELECT * FROM contrat WHERE id_employe = 'B4N001' ORDER BY date_debut;
-- SELECT * FROM affectation WHERE id_employe = 'B4N001' ORDER BY date_debut;
-- SELECT d.* FROM document_contrat d JOIN contrat c ON c.id = d.id_contrat
-- WHERE c.id_employe = 'B4N001';
-- SELECT * FROM mouvement_rh WHERE id_employe_concerne = 'B4N002'
-- ORDER BY date_mouvement, id;

