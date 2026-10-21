INSERT INTO departement (id, libelle) VALUES (10, 'Informatique');
INSERT INTO besoin (id, id_departement, type_motif, motif) VALUES (10, 10, 'NOUVEAU_POSTE', 'Renforcement de l’équipe informatique');
INSERT INTO besoin_poste (id, id_besoin, libelle_poste_propose, effectif, budget_mensuel_unitaire) VALUES (10, 10, 'Développeur web', 1, 1500000), (11, 10, 'Technicien informatique', 1, 1000000);
INSERT INTO offre_emploi (id, id_besoin_poste, titre, description, date_publication, date_limite, statut) VALUES (10, 10, 'Développeur web', 'Développement et maintenance des applications web', CURRENT_DATE, CURRENT_DATE + 30, 'OUVERT'), (11, 11, 'Technicien informatique', 'Maintenance du matériel et assistance aux utilisateurs', CURRENT_DATE, CURRENT_DATE + 30, 'OUVERT');

INSERT INTO role_user (role_name) values ('CANDIDAT');
INSERT INTO utilisateur (username, password, id_role) values ('candidat1@mail.com', '$2a$10$y.p1DuAOKG6XCoM.ySMAOeAUQk6rNRFxCxhyKFXbF1G9rLNEVlZW2', (SELECT id FROM role_user WHERE role_name = 'CANDIDAT'));
INSERT INTO candidat (nom, prenom,date_naissance, email, telephone,qualifications) values ('Rasoa', 'Voahangy', '1990-05-15', 'candidat1@mail.com', '034 12 345 67', 'Licence en informatique');