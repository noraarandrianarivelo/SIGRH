-- Poste demandé pour le recrutement d'une assistante de direction.
-- L'insertion est idempotente : elle ne crée pas de doublon si le poste existe déjà.
INSERT INTO poste (id_departement, libelle)
SELECT d.id, 'Assistante de direction'
FROM departement d
WHERE d.libelle = 'Direction'
  AND NOT EXISTS (
      SELECT 1
      FROM poste p
      WHERE p.id_departement = d.id
        AND LOWER(p.libelle) = LOWER('Assistante de direction')
  );

INSERT INTO utilisateur (username, password, id_role) values ('candidat2@mail.com', '$2a$10$y.p1DuAOKG6XCoM.ySMAOeAUQk6rNRFxCxhyKFXbF1G9rLNEVlZW2', (SELECT id FROM role_user WHERE role_name = 'CANDIDAT'));
INSERT INTO candidat (nom, prenom,date_naissance, email, telephone,qualifications) values ('Aaron', 'Tsangy', '1990-05-15', 'candidat2@mail.com', '034 12 445 67', 'Master en informatique');

INSERT INTO utilisateur (username, password, id_role) values ('candidat3@mail.com', '$2a$10$y.p1DuAOKG6XCoM.ySMAOeAUQk6rNRFxCxhyKFXbF1G9rLNEVlZW2', (SELECT id FROM role_user WHERE role_name = 'CANDIDAT'));
INSERT INTO candidat (nom, prenom,date_naissance, email, telephone,qualifications) values ('Kot', 'Bekibo', '1990-05-15', 'candidat3@mail.com', '034 12 455 67', 'Chaauffeur vtc');