-- 1. Insertion des Rôles utilisateurs
INSERT INTO role_user (id, role_name) VALUES 
(1, 'ROLE_MANAGER'),
(2, 'ROLE_RH'),
(3, 'ROLE_FINANCE'),
(4, 'ROLE_DIRECTION');

-- 2. Insertion des Départements
INSERT INTO departement (id, libelle) VALUES 
(1, 'Informatique'),
(2, 'Ressources Humaines'),
(3, 'Finance et Comptabilité');

-- 3. Insertion des Postes
INSERT INTO poste (id, id_departement, libelle) VALUES 
(1, 1, 'Développeur Java Senior'),
(2, 2, 'Chargé de Recrutement'),
(3, 3, 'Contrôleur Financier');

-- 4. Insertion des Employés (Acteurs : demandeur, validateurs)
INSERT INTO employe (id, nom, prenom, id_departement) VALUES 
('EMP001', 'RAKOTO', 'Jean', 1),     -- Manager IT (Demandeur)
('EMP002', 'RANDRIA', 'Marie', 2),   -- RH (Validateur RH)
('EMP003', 'RAZAFY', 'Hery', 3),     -- Financier (Validateur Finance)
('EMP004', 'RASOA', 'Paul', 1);      -- Directeur (Validateur Direction)

-- 5. Association Comptes Utilisateurs (optionnel mais utile si vous gérez l'authentification)
INSERT INTO utilisateur (username, password, id_role, id_employee) VALUES 
('m_rakoto', 'password123', 1, 'EMP001'),
('rh_marie', 'password123', 2, 'EMP002'),
('fin_hery', 'password123', 3, 'EMP003'),
('dir_paul', 'password123', 4, 'EMP004');

-- 6. Insertion de la Grille des Salaires (pour le contrôle budgétaire)
INSERT INTO grille_salaire (id_poste, salaire_min, salaire_max) 
VALUES (1, 2000000.00, 3500000.00);