# SIGRH

Application Java 17+ / Spring Boot 4.1.1, Thymeleaf, Spring Security et PostgreSQL. Les pages du recrutement et celles du personnel partagent `sigrh/src/main/resources/static/css/recrutement.css`.

## Installer et démarrer

Maven 3.9+ et PostgreSQL sont nécessaires. Depuis la racine :

```sh
mvn -f sigrh/pom.xml compile
mvn -f sigrh/pom.xml spring-boot:run
```

L'application écoute sur http://localhost:8080. La connexion PostgreSQL est définie dans `sigrh/src/main/resources/application.properties`. Pour utiliser une autre base, définir `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` et `SPRING_DATASOURCE_PASSWORD`. Hibernate ne génère aucun DDL (`ddl-auto=none`).

Sur une **base neuve et distincte**, charger dans cet ordre (adapter la connexion) :

```sh
createdb sigrh_demo
psql -v ON_ERROR_STOP=1 -d sigrh_demo -f database/SIGRH.sql
psql -v ON_ERROR_STOP=1 -d sigrh_demo -f database/22-10-2026_01_bloc4_tables_manquantes.sql
psql -v ON_ERROR_STOP=1 -d sigrh_demo -f database/22-10-2026_02_bloc4_donnees_test.sql
```

Ces scripts ne remettent pas la base à zéro. Ne pas les rejouer sur une installation existante. Pour une base déjà installée, vérifier les objets présents et appliquer seulement le complément manquant. Les anciens scripts `bloc4.sql` ne sont pas la référence de cette implémentation. Les collaborateurs restent responsables des données initiales des blocs 1–3.

Les PDF sont stockés sous `sigrh/uploads/contrats` lorsque le lancement est effectué depuis le module Maven. Configurer un chemin absolu persistant avec `SIGRH_CONTRAT_UPLOAD_DIR`. Ce dossier ne doit pas être exposé comme ressources statiques. Ajouts PDF uniquement, 10 Mio maximum, noms UUID, contrôle de signature `%PDF-`, téléchargement protégé et protection contre les chemins sortants/liens symboliques. Les anciennes pièces ne sont pas remplacées. Un échec transactionnel supprime le nouveau fichier ; un arrêt brutal du processus entre écriture et commit peut nécessiter le nettoyage manuel d'un fichier sans référence.

## Essayer le bloc 4

Depuis l'accueil, ouvrir **Personnel, contrats et intégration** (`/personnel`) ou **Mes tâches** (`/personnel/taches`). L'ancienne entrée `/rh/contrats` redirige vers ce dossier commun.

Comptes synthétiques : `b4_rh`, `b4_it`, `b4_formation`, `b4_paie`, `b4_direction`. Mot de passe commun : `Bloc4Test2026!`. Les fixtures contiennent des empreintes BCrypt compatibles avec l'authentification existante.

Pour tester l'accès salarié sans donner de rôle manager aux recrues, charger **optionnellement dans la base de démonstration** :

```sh
psql -v ON_ERROR_STOP=1 -d sigrh_demo -f database/22-10-2026_03_bloc4_acces_salaries_test.sql
```

Cela ajoute `ROLE_SALARIE` et `b4_soa`, `b4_rija`, `b4_lova`, avec le même mot de passe synthétique. Aucun nouveau mécanisme d'authentification n'est requis : tout compte lié à un employé peut consulter son propre dossier.

Au **22 octobre 2026** :

- Soa (`B4N001`) : deux contrats conservés, CDI courant, ancienne intégration sur le CDD, 6/6 étapes terminées, une inscription paie.
- Rija (`B4N002`) : 3/6 étapes terminées ; ordinateur reçu, téléphone bloqué. Avec `b4_it`, ouvrir la tâche téléphone, sélectionner l'exemplaire physique 004 et enregistrer la réception réelle. Avec `b4_formation`, terminer l'inscription existante avec sa date réelle. Avec `b4_paie`, confirmer la prise en charge. Le parcours se recalcule à chaque lecture.
- Lova (`B4N003`) : contrat brouillon et affectation future au 01/11/2026, 0/6. Les autres dates d'exécution produisent naturellement des situations différentes.

Sur la fiche RH : modifier seulement un brouillon, préparer un renouvellement depuis un contrat antérieur, enregistrer sa signature, ajouter des pièces, changer l'affectation aujourd'hui, configurer plusieurs tâches par étape, corriger leur libellé ou réassigner. Les catalogues permettent d'ajouter/corriger matériels physiques et formations. La restitution d'un badge/matériel conserve l'attribution et l'intégration historique.

Un responsable traite sa tâche depuis **Mes tâches**. Pour terminer, il confirme le résultat précis, soit sélectionné parmi ses réalisations existantes, soit créé par le formulaire métier. Une formation inscrite sans date ne suffit pas ; un salaire ne prouve pas l'inscription paie. Réouvrir efface la date de complétion courante et conserve les traces précédentes. Un rejeu d'une complétion déjà terminée ne crée rien.

Les chemins PDF des fixtures sont fictifs : l'écran affiche « Document indisponible ». Charger un véritable PDF pour essayer l'ajout et le téléchargement. Le bloc 3 ne comporte actuellement aucun générateur PDF à réutiliser ; cette livraison gère les pièces importées.

## Droits et choix d'implémentation

| Action | Autorisation serveur |
| --- | --- |
| Liste globale, gestion contrats/affectations, attribution, catalogues, restitutions, journal transversal | `ROLE_RH` lié à un employé |
| Consultation de son dossier et de ses documents | Employé lié au compte, uniquement ses données |
| Lire/exécuter une tâche et changer son état | Son responsable actuel, relu sous verrou transactionnel |
| Valider une signature | Responsable RH ou Direction, conformément au droit de signature existant |
| Valider une inscription paie | Responsable RH ou Finance |
| Accéder aux pièces au titre d'une tâche de signature | Responsable de cette tâche, contrat d'entrée exact, documents contrat PDF/signé uniquement |

Un manager n'a pas de périmètre d'équipe défini dans ce dépôt : aucune consultation globale ou de salaire n'est ajoutée à ce rôle. L'IT et la formation interviennent selon l'attribution. La Direction conserve l'opération de signature existante ; elle ne reçoit pas la gestion générale des contrats. Un compte sans employé lié reçoit un refus explicite sur le bloc 4. Les formulaires du bloc 4 et l'ancienne route de signature exigent un jeton CSRF ; les anciens fetch du bloc 1 gardent leur configuration existante.

Les services utilisent `JdbcTemplate` avec le même `DataSource` et les mêmes transactions Spring que JPA. Les requêtes sont paramétrées ; les quelques noms de tables variables proviennent de listes fermées côté serveur. Les identifiants employés restent des chaînes et le salaire un `BigDecimal`. Le mapping JPA du contrat comprend désormais son précédent. Aucune nouvelle table ni colonne n'est nécessaire par rapport aux scripts de référence.

`PersonnelService` contrôle les contrats successifs, les périodes inclusives et la mobilité. `OnboardingService` gère les six réalisations et lit exclusivement les vues pour la progression. `DocumentContratService` protège le stockage. `RessourcePersonnelService` gère catalogues/restitutions. `MouvementRhService` trace chaque ligne modifiée par le bloc 4 dans la transaction métier, avec snapshots JSONB et `_preuve: {table,id}` pour une complétion. Les snapshots historiques dépourvus de `_preuve` restent lisibles. Aucun endpoint d'édition/suppression du journal n'est exposé.

Ordre de verrouillage : ligne stable employé, puis contrat/tâche et ressource précise. Les opérations d'un même salarié sont sérialisées ; le matériel physique est verrouillé, le numéro de badge reçoit un verrou transactionnel PostgreSQL et les index d'unicité restent actifs. Les périodes datées sont aussi contrôlées. Les responsables sont choisis parmi les employés possédant un compte ; l'acteur et l'assignateur viennent du compte connecté. Un même résultat référencé dans les nouvelles traces ne peut pas valider deux tâches distinctes.

La mobilité se fait aujourd'hui : ancienne fin hier, nouvelle période aujourd'hui, département de l'employé synchronisé. Les périodes incohérentes ou une seconde mobilité le même jour sont refusées explicitement. Les affectations futures déjà présentes restent consultables ; aucune planification automatique partielle n'est ajoutée.

## Tests

```sh
mvn -f sigrh/pom.xml test
mvn -f sigrh/pom.xml package
```

Le test historique `SigrhApplicationTests` utilise une base PostgreSQL configurée. Les nouveaux tests PostgreSQL sont **désactivés par défaut** pour éviter d'écrire dans la base habituelle. Pour les exécuter, préparer une base de test isolée avec les trois scripts de référence, puis :

```sh
mvn -f sigrh/pom.xml \
  -Dsigrh.bloc4.integration=true \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55434/sigrh_bloc4_test \
  -Dspring.datasource.username=sigrh_test \
  -Dspring.datasource.password= \
  test
```

Les tests d'intégration fixent l'horloge au 22/10/2026. Ils vérifient les fixtures, dates, chaînes et renouvellements sans proposition, mobilité, tâches multiples, preuves précises, réouverture/rejeu, autorisations, rendu Thymeleaf, CSRF et login BCrypt. Des tests à deux threads couvrent les mobilités/renouvellements concurrents, la double remise de matériel et la réassignation face à une complétion. Des échecs simulés du journal vérifient l'annulation métier et la compensation du fichier. Les tests ordinaires sont annulés ; les tests de concurrence créent de petites données avec des identifiants aléatoires et les conservent dans la **base isolée jetable**.

## Limites du modèle et de l'intégration

Pas d'avenant, rupture anticipée, dispense, échéance de tâche ou inventaire complet. Un renouvellement exige la fin naturelle du précédent ; il ne termine pas prématurément le contrat en vigueur. Les six catégories et toutes leurs tâches sont nécessaires. Le sens exact du matériel est confirmé par le responsable, car le catalogue n'a pas de catégorie structurée. Les preuves réutilisées sont vérifiées pour le salarié et la période de préparation du parcours ; les anciennes traces sans référence de preuve ne permettent pas de reconstruire automatiquement cette association.

L'audit ajouté couvre les mutations du bloc 4 et l'ancienne signature, pas toutes les écritures historiques des blocs 1–3. La prise d'effet automatique des affectations futures importées et la génération PDF du bloc 3 ne sont pas implémentées dans le dépôt. Le stockage n'est pas un système de signature électronique ni un scanner antivirus. Les pages ont des tests de rendu serveur ; une vérification visuelle de navigateur reste distincte.

## Validation de cette livraison

Le 22/10/2026, `compile`, `test-compile`, puis `package` ont été exécutés contre un cluster PostgreSQL 18 temporaire, port 55434, avec les trois scripts de référence. Résultat final : **58 tests, 0 échec, 0 erreur, 0 ignoré**, dont 21 tests d'intégration du bloc 4. Le WAR produit est `sigrh/target/sigrh-0.0.1-SNAPSHOT.war`. `git diff --check` ne signale pas d'erreur d'espacement.

Un défaut préexistant de `ValidationService.creerPostesProposes` a été corrigé : la boucle inutile sur l'effectif provoquait une exception lorsque celui-ci était absent du test. Chaque ligne de besoin résout désormais son poste une fois, indépendamment de l'effectif.
