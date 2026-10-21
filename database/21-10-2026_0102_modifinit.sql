INSERT INTO departement (libelle) VALUES
  ('Informatique'), ('Ressources humaines'), ('Finance'), ('Direction'), ('Marketing');

INSERT INTO poste (id_departement, libelle) VALUES
  (1, 'Développeur'), (1, 'Administrateur réseau'), (1, 'Chef de projet'),
  (2, 'Chargé de recrutement'), (3, 'Comptable'), (5, 'Community manager');

INSERT INTO grille_salaire (id_poste, salaire_min, salaire_max) VALUES
  (1, 1500000, 3000000), (2, 1800000, 3500000), (3, 3000000, 6000000),
  (4, 1200000, 2500000), (5, 1300000, 2800000), (6, 1000000, 2000000);

INSERT INTO employe (id, nom, prenom, id_departement) VALUES
  ('E001', 'Rakoto',  'Jean',     1),   -- manager Informatique
  ('E002', 'Rabe',    'Sala',     2),   -- RH
  ('E003', 'Rasoa',   'Voahangy', 3),   -- Finance
  ('E004', 'Andry',   'Hery',     4),   -- Direction
  ('E005', 'Rivo',    'Miora',    5);   -- manager Marketing

INSERT INTO role_user (role_name) VALUES
  ('ROLE_MANAGER'), ('ROLE_RH'), ('ROLE_FINANCE'), ('ROLE_DIRECTION');

INSERT INTO utilisateur (username, password, id_role, id_employee)
SELECT v.username, '$2a$10$y.p1DuAOKG6XCoM.ySMAOeAUQk6rNRFxCxhyKFXbF1G9rLNEVlZW2', r.id, v.id_employee
FROM (VALUES
  ('manager',     'ROLE_MANAGER',   'E001'),
  ('manager_mkt', 'ROLE_MANAGER',   'E005'),
  ('rh',          'ROLE_RH',        'E002'),
  ('finance',     'ROLE_FINANCE',   'E003'),
  ('direction',   'ROLE_DIRECTION', 'E004')
) AS v(username, role_name, id_employee)
JOIN role_user r ON r.role_name = v.role_name;