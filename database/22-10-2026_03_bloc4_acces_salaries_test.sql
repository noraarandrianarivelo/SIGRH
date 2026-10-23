-- OPTIONNEL, base de démonstration uniquement, après les fixtures du bloc 4.
-- Accès personnels sans donner le rôle manager aux recrues.
-- Mêmes empreintes synthétiques et mot de passe que les acteurs : Bloc4Test2026!
BEGIN;
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
COMMIT;
