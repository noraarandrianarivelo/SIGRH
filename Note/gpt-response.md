Voici le **script PostgreSQL harmonisé et commenté**, construit à partir de ton fichier :

:chatgpt-content-reference{index="2"}[**SIGRH_modules_1_2_3_postgresql.sql**](sandbox:/workspace/scratch/e87ed68b2e10/output/SIGRH_modules_1_2_3_postgresql.sql)

Il contient **17 tables et 2 vues**. J’ai conservé tes objets métier, avec les ajustements suivants pour les relier correctement :

| Élément | Organisation retenue |
|---|---|
| **Demande de recrutement** | `DemandeCreationEmploi` et `DemandeRecrutement` deviennent une seule table : `demande_recrutement`. |
| **Besoin et postes** | Un besoin peut concerner plusieurs postes. Chaque ligne `besoin_poste` contient son effectif et son budget mensuel unitaire. |
| **Grille salariale** | Elle est définie par poste, avec un salaire minimum et maximum. |
| **Offre d’emploi** | Elle référence une ligne `besoin_poste`, permettant de retrouver précisément le poste et la demande d’origine. |
| **Séance d’évaluation** | Elle référence une **candidature** et un évaluateur provenant de `employe`. Tes deux champs `date_entretien` et `date_test` sont conservés. |
| **Test** | `test_recrutement` conserve le niveau de 1 à 3, la description et l’employé responsable. |
| **Fiche de notes** | Une seule fiche par séance, avec les notes d’entretien et de test, les commentaires et l’avis. |
| **Proposition d’embauche** | Chaque nouvelle proposition constitue une version distincte. Les anciennes conditions et réponses restent conservées. |

Par exemple, la renégociation permet de conserver ces deux lignes :

| Candidature | Version | Salaire proposé | Réponse |
|---|---:|---:|---|
| 123 | 1 | 1 200 000 Ar | Refusée |
| 123 | 2 | 1 350 000 Ar | Acceptée |

Un **index unique partiel** empêche d’avoir plusieurs propositions actives pour la même candidature, selon le mécanisme prévu par PostgreSQL. [postgresql.org](https://www.postgresql.org/docs/current/indexes-partial.html?utm_source=chatgpt.com)

Les deux vues facilitent les consultations :

- **`v_synthese_besoin`** : calcule l’effectif total et le budget mensuel total du besoin.
- **`v_offre_recrutement`** : rassemble l’offre, son poste, son département et sa demande d’origine, sans recopier ces relations dans plusieurs tables.

**Les commentaires distinguent les contraintes déjà assurées par la base et les règles à programmer dans l’application**, notamment les autorisations, l’ordre des validations, la vérification d’une demande validée avant création d’offre et les conditions de finalisation des notes.

Comme tu l’as indiqué, **la création du salarié, le matricule, le contrat et l’affectation sont réservés à l’étape suivante**. L’acceptation d’une proposition ne crée donc pas automatiquement un employé.

Pour exécuter le fichier dans une base existante :

```bash
psql -v ON_ERROR_STOP=1 -d votre_base -f SIGRH_modules_1_2_3_postgresql.sql
```

Le script crée le schéma `sigrh` et doit être exécuté une seule fois. **L’exécution et 23 contrôles ont été validés avec le moteur PostgreSQL embarqué de PGlite**, notamment les relations entre tables, les notes et le parcours refus → nouvelle proposition → acceptation.
