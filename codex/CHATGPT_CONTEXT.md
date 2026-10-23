# CHATGPT_CONTEXT — SIGRH, bloc 4 : contrats et onboarding

Date du récapitulatif : 22 octobre 2026.
Document de reprise pour Codex dans VS Code. Lire ensuite INSTRUCTIONS.md.
Ce document résume la conversation de conception du projet RH, les décisions finales et l'état réel du travail. Il ne constitue pas une preuve que l'application a déjà été implémentée.
Quand SIGRH.sql est mentionné, on fait référence à 2 fichiers dans le dépôt: (database/SIGRH.sql + database/22-10-2026_01_bloc4_tables_manquantes.sql).

## 1. Objectif et manière de travailler

Projet universitaire réalisé en groupe : SIGRH, système de gestion des ressources humaines, avec PostgreSQL.
Le travail de l'utilisateur porte sur le bloc 4 « Contrats et onboarding ».

Exigence importante du groupe : rester aussi simple que possible, garder les colonnes nécessaires et éviter un moteur générique de workflow ou de nouvelles tables sans besoin concret.
L'utilisateur veut comprendre le code ; donner des explications claires sur les choix et les contrôles métier.
L'interface et les messages métier doivent rester cohérents avec le français du projet.

Seule la base PostgreSQL a été examinée ici. Le langage du backend, le framework, le frontend, la structure du dépôt, les routes existantes et les commandes de compilation ne sont pas confirmés.
(l'application est écrite en Java avec Spring-boot et thymeleaf, pas besoin de lire quoi que ce soit pour vérifier ça)
Inspecter le dépôt réel, ses consignes et les composants existants avant de coder. Ne pas imposer une nouvelle architecture en supposant qu'un autre projet de l'utilisateur utilise la même stack.
(il n'y a pas de consignes au préalable non plus, ne pas chercher pour des consignes)
## 2. Contexte du projet et frontière entre blocs

| Bloc | Responsabilité |
| --- | --- |
| 1 | Besoins en personnel, demandes de recrutement et validations RH/finance/direction. |
| 2 | Offres, candidats, candidatures, tests, entretiens et évaluations. |
| 3 | Proposition d'embauche, réponse du candidat, puis création du dossier employé, du matricule/identifiant, du premier contrat, des documents et de la première affectation. |
| 4 | Évolution des contrats et affectations, attribution et exécution des tâches d'intégration, suivi d'onboarding et traçabilité. |

Un candidat n'est pas encore un employé. Un candidat peut avoir plusieurs candidatures.
Le bloc 4 réutilise les données créées par le bloc 3 ; il ne crée pas une seconde table d'employés ni une seconde version des contrats ou affectations.

Chaîne de référence utilisée pour une première embauche :
besoin -> besoin_poste -> offre_emploi -> candidature -> proposition_embauche -> contrat -> affectation/document_contrat.
La demande et les validations sont rattachées au besoin ; le candidat est rattaché à la candidature.
Après acceptation de la proposition, l'application crée l'employé et le contrat initial.
Un renouvellement ultérieur ne nécessite pas une nouvelle candidature ou une nouvelle proposition.

## 3. Fonctionnalités demandées par l'utilisateur

Les trois fonctionnalités d'origine sont :
1. Gestion des contrats : consulter le contrat actuel, changer de contrat, renouveler ; modifications réservées au responsable habilité.
2. Gestion des affectations : consulter l'affectation, changer le poste ou le département ; modifications réservées au responsable habilité.
3. Gestion des tâches et suivi d'onboarding : assignation par l'assignateur, changement de statut par le responsable de la tâche, achèvement automatique de l'étape lorsque ses tâches nécessaires sont terminées.

La table fonctionnelle détaillée retenue est la suivante :

| Fonctionnalité | Résultat attendu | Accès métier attendu | Tables |
| --- | --- | --- | --- |
| Contrats | Contrat actuel, historique, renouvellement ou passage CDD/CDI, signature et documents. | Employé : ses données ; responsable RH habilité : gestion des employés autorisés. | contrat, employe, poste, proposition_embauche si contrat initial, document_contrat, mouvement_rh. |
| Affectations | Poste/département/lieu actuels et historique ; clôture d'une période puis nouvelle affectation. | Employé : ses données ; responsable habilité : changements. | affectation, contrat, poste, departement, employe, mouvement_rh. |
| Attribution des tâches | Création, définition de l'étape et du salarié concerné, attribution et réassignation. | Assignateur habilité ; responsable : consultation de ses tâches. | onboarding_tache, contrat, employe, mouvement_rh. |
| Exécution des tâches | A_FAIRE, EN_COURS, BLOQUEE, TERMINEE ; enregistrement du résultat métier correspondant. | Responsable actuel de la tâche. | onboarding_tache, tables métier de l'étape, mouvement_rh. |
| Suivi d'onboarding | Six étapes, tâches restantes, progression et blocages ; état recalculé automatiquement. | Employé : son suivi ; RH/manager : selon périmètre réellement autorisé. | onboarding_tache, contrat, vues suivi_onboarding_etape et suivi_onboarding. |
| Historique | Qui a fait quoi, quand, avec état avant/après. | RH ou rôle explicitement habilité. | mouvement_rh et objets référencés. |

L'assignateur et le responsable d'une tâche sont deux personnes potentiellement différentes.
La création d'une tâche ne donne pas à son assignateur le droit implicite de la terminer à la place du responsable.
Les noms précis des rôles autorisés et le périmètre des managers restent à rapprocher des règles du dépôt ; la politique détaillée n'a pas été fournie dans cette conversation.

## 4. Sens des éléments d'onboarding

| Étape | Sens retenu |
| --- | --- |
| SIGNATURE_CONTRAT | Signature du contrat d'entrée confirmée, avec date_signature ; le PDF signé peut être dans document_contrat. |
| BADGE | Badge d'identification ou d'accès effectivement remis à l'employé. Le port autour du cou n'est pas une exigence informatique. |
| COMPTE | Compte informatique utilisable : messagerie, réseau, ERP, intranet, etc. |
| MATERIEL | Chaque matériel nécessaire effectivement remis ; acheter un ordinateur ne signifie pas l'avoir remis. |
| FORMATION | Chaque formation nécessaire effectivement terminée ; une inscription seule ne suffit pas. |
| INSCRIPTION_PAIE | Prise en charge de l'employé confirmée par le dispositif/service de paie, avec date de prise en charge. |

Le mot « compte » a été interprété comme compte informatique dans le contexte du sujet ; le texte original complet n'a pas été fourni pour lever toute ambiguïté.
CompteEmploye n'est pas une table de coordonnées bancaires.

Un compte informatique nécessite généralement une authentification, mais le bloc 4 suit sa mise à disposition ; il ne stocke pas son mot de passe.
Exemple : l'IT crée soa@entreprise.mg dans la messagerie externe, transmet de façon appropriée l'accès à Soa et enregistre l'activation dans compte_employe.
Le compte permettant d'entrer dans l'application RH est déjà représenté par utilisateur ; son password contient une empreinte de mot de passe, pas le mot de passe de la messagerie.

salaire_mensuel appartient au contrat : il représente le salaire mensuel de base, considéré brut, en ariary dans ces scripts.
Avoir un salaire dans contrat ne signifie pas être inscrit à la paie et ne signifie pas avoir été payé.
inscription_paie suit la confirmation d'inscription. Calcul des bulletins, cotisations, primes et virements : hors périmètre actuel.
Dans un éventuel module de paie complet, l'employé serait le bénéficiaire et les contrats applicables fourniraient les conditions de rémunération pour une période.

## 5. Décisions de modèle : utiliser la dernière version

| Sujet | Décision finale |
| --- | --- |
| Identifiant d'employé | employe.id est VARCHAR(10), par exemple E001 ou B4N001. Les références doivent être des chaînes, jamais des entiers. Il n'existe pas de colonne matricule séparée dans SIGRH.sql actuel. |
| Identifiants techniques | Les autres clés des tables courantes sont SERIAL / INTEGER. |
| Proposition d'embauche | contrat.id_proposition est nullable et UNIQUE. Premier contrat lié à une proposition acceptée ; renouvellement possible avec NULL. |
| Contrat précédent | contrat.id_contrat_precedent est une FK nullable vers contrat ; même employé, aucun cycle. |
| Signature | contrat.date_signature existe. Pas de table contrat_signature nécessaire dans la version simple. |
| Origine d'affectation | affectation.id_contrat est conservé. Il remplit le rôle envisagé sous le nom id_contrat_origine. |
| Département d'affectation | Pas de affectation.id_departement : le département se déduit de poste.id_departement. |
| Département de l'employé | SIGRH.sql conserve employe.id_departement ; synchroniser cette copie lors d'une mobilité effective. Elle ne doit pas contredire l'affectation réelle. |
| Poste | Un poste appartient à un seul département. Ne pas déplacer rétroactivement un poste déjà utilisé dans les historiques ; créer/utiliser le poste correspondant dans le nouveau département. |
| Documents | Réutiliser document_contrat existante. L'ancienne idée d'une table générale document est abandonnée dans les derniers scripts. |
| Paie | Le nom final est inscription_paie ; PaieContrat/PaieEmploye étaient des noms discutés, pas les tables finales. |
| Onboarding | Pas de tables onboarding ni onboarding_etape dans la version finale simplifiée. |
| Tâches | onboarding_tache.id_contrat référence le contrat d'entrée. Le salarié se retrouve par contrat.id_employe ; pas de id_employe dupliqué dans la tâche. |
| Étapes | Six catégories fixes dans onboarding_tache.etape. Toutes les tâches créées sont nécessaires. |
| Historique transversal | Ajouter mouvement_rh ; il ne remplace pas les anciennes lignes de contrat et affectation. |

Les propositions initiales plus riches comportaient des pilotes, échéances, colonnes obligatoire, codes de référentiels, identifiants de responsables partout, statuts de ressources supplémentaires et date_fin_effective.
Ces propositions ont été simplifiées. En particulier, date_fin_effective N'EXISTE PAS dans le SIGRH.sql actuel : ne pas écrire du code qui la suppose présente.

L'ancien fichier bloc4_minimal.sql était un premier essai autonome, avec des références d'employés entières. Il ne constitue plus le schéma de référence.
Ne pas mélanger les anciennes propositions en texte, cet ancien script et les fichiers finaux.

## 6. Pourquoi onboarding et onboarding_etape ont été retirées

L'utilisateur a demandé de limiter les tables et les colonnes.
Le contrat d'entrée fournit déjà un identifiant stable pour un parcours d'intégration ; la colonne etape fournit les catégories fixes.
Les vues calculent les états sans deuxième statut à maintenir.

Ce choix ne signifie pas que ces tables seraient conceptuellement inutiles :
- onboarding serait utile pour une instance d'intégration indépendante, un pilote, des dates propres ou plusieurs parcours différents ;
- onboarding_etape serait utile pour des étapes configurables, des modèles différents ou des dispenses.
Ces besoins ne sont pas le périmètre final retenu.

Un renouvellement ordinaire ne crée pas de nouveau parcours et ne déplace pas les tâches vers le dernier contrat.
Une réembauche distincte peut partir d'un nouveau contrat d'entrée.
La vue actuelle reconnaît les contrats sans précédent comme entrées, et inclut aussi un contrat ayant explicitement des tâches. L'application doit éviter de créer ces tâches sur un simple renouvellement.

## 7. Tables réellement utilisées et propriété des données

### 7.1 Déjà dans SIGRH.sql

| Table | Particularité pour le bloc 4 |
| --- | --- |
| contrat | Partagée avec le bloc 3 : clauses, salaire, dates, signature, statut, proposition, précédent. |
| affectation | Partagée avec le bloc 3 : salarié, contrat d'origine, poste, lieu et période. |
| document_contrat | Plusieurs documents par contrat : type, référence de stockage, date d'ajout. |
| employe | Identifiant texte et département courant conservé par le groupe. |
| poste / departement | Référentiels partagés ; département déduit du poste. |
| utilisateur / role_user | Authentification et rôle existants, à réutiliser. |
| proposition_embauche | Origine du premier contrat, conditions proposées et réponse du candidat. |

Les autres tables des blocs 1-3 sont également présentes dans le script de base ; elles servent notamment aux FK des données de test. Le DDL exact figure en annexe.

### 7.2 Neuf tables ajoutées dans 01_bloc4_tables_manquantes.sql

| Table | Données minimales et sémantique |
| --- | --- |
| compte_employe | Employé, système, identifiant, date_activation. Une ligne constate un compte opérationnel. UNIQUE(systeme, identifiant). |
| badge_employe | Employé, numéro, remise, restitution éventuelle. Un badge non restitué ne peut pas être détenu deux fois. |
| materiel | id, nom. Une ligne = un objet physique ; deux ordinateurs donnent deux lignes. |
| materiel_employe | Employé, objet, réception, restitution éventuelle. Pas deux détentions non clôturées du même objet. |
| formation | id, nom. Catalogue simple. |
| formation_employe | Employé, formation, date_completion nullable. NULL = pas encore terminée. Pas d'unicité définitive employé/formation, pour permettre de la refaire. |
| inscription_paie | Employé, date_inscription, date_debut_prise_en_charge. Une ligne = confirmation, pas simple demande. |
| onboarding_tache | Contrat d'entrée, étape, libellé, assignateur, responsable, statut et date_completion. |
| mouvement_rh | Employé concerné, acteur, table et ligne concernées, action, horodatage, états JSONB avant/après. |

Les dates métier sont DATE ; les nouvelles complétions de tâches et traces sont TIMESTAMPTZ.
Les anciens horodatages de SIGRH.sql sont souvent TIMESTAMP sans fuseau ; ne pas les traiter comme des DATE.
Utiliser un type décimal pour NUMERIC(14,2).

## 8. Authentification et accès : détails à préserver

utilisateur.id est INTEGER ; utilisateur.id_employee est VARCHAR(10) et référence employe.id.
Attention au nom exact : id_employee dans utilisateur, mais id_employe dans les tables métier.
role_user.role_name contient le rôle, relié à utilisateur.id_role.

Les références id_assignateur, id_responsable et id_acteur du complément SQL sont des identifiants D'EMPLOYÉS en VARCHAR(10), pas des identifiants numériques de utilisateur.
Résoudre l'utilisateur connecté vers utilisateur.id_employee côté serveur.
Ne jamais accepter l'acteur ou l'assignateur d'une écriture sur simple déclaration du client.

Les rôles utilisés dans les fixtures sont ROLE_RH, ROLE_MANAGER, ROLE_FINANCE et ROLE_DIRECTION.
La présence d'un rôle dans la base ne prouve pas les autorisations actuelles du code.
Le schéma ne possède pas de lien hiérarchique manager-équipe ; examiner comment le dépôt définit déjà ce périmètre.
Les nouvelles recrues du jeu de test n'ont pas de compte utilisateur. Ne pas prétendre que leur accès salarié est déjà testé.

## 9. Calcul exact du suivi

Statuts de tâche autorisés :
A_FAIRE, EN_COURS, BLOQUEE, TERMINEE.

La contrainte impose :
- TERMINEE : date_completion obligatoire ;
- tout autre statut : date_completion NULL.
Il n'existe ni ANNULEE ni NON_APPLICABLE ni colonne obligatoire dans le schéma final.

suivi_onboarding_etape présente les six catégories pour chaque parcours :
1. Aucune tâche : A_CONFIGURER.
2. Toutes les tâches terminées : TERMINEE.
3. Sinon au moins une bloquée : BLOQUEE.
4. Sinon au moins une tâche différente de A_FAIRE : EN_COURS.
5. Sinon : A_FAIRE.

suivi_onboarding :
1. Une étape A_CONFIGURER : A_CONFIGURER.
2. Toutes les étapes TERMINEE : TERMINE.
3. Sinon une étape BLOQUEE : BLOQUE.
4. Sinon une étape différente de A_FAIRE : EN_COURS.
5. Sinon : A_FAIRE.

Les pluriels et terminaisons diffèrent : tâche/étape TERMINEE et BLOQUEE, parcours TERMINE et BLOQUE.
Les vues exposent notamment id_contrat, id_employe et les compteurs ; une vue n'est pas une table à modifier.

Exemple : deux tâches MATERIEL, « Remettre ordinateur » et « Remettre téléphone ».
Une seule terminée ne termine pas l'étape. Réouvrir l'une remet l'étape et le parcours en état incomplet.
Une catégorie manquante reste A_CONFIGURER : zéro tâche ne signifie jamais succès.

La complétion exige le résultat métier réel. L'état affiché est ensuite calculé depuis les tâches, pas en recontrôlant en permanence les ressources actuelles.
Exemple : le PC de Soa a été remis en avril puis restitué en septembre ; son onboarding d'avril reste historiquement terminé.
Ne pas réouvrir automatiquement une ancienne intégration parce qu'un matériel a ensuite été rendu.

## 10. Historisation et atomicité

Exigence du sujet : « Tous les mouvements doivent être traçables ».

Contrats successifs : garder les anciennes lignes ; ne pas transformer l'ancien CDD en CDI en écrasant ses conditions.
Affectations : clôturer l'ancienne période et créer la nouvelle.
Dates de fin considérées inclusives : pour un changement le jour D, ancienne affectation jusqu'à D-1, nouvelle à partir de D.

mouvement_rh ajoute une trace pour chaque création/modification/suppression autorisée :
- CREATION : valeurs_avant NULL, valeurs_apres non NULL ;
- MODIFICATION : les deux non NULL ;
- SUPPRESSION : valeurs_avant non NULL, valeurs_apres NULL.

id_ligne est TEXT : accepte un identifiant numérique converti en chaîne ou un employé comme B4N001.
table_concernee + id_ligne est une référence polymorphe de journal, pas une FK vers toutes les tables.
id_employe_concerne peut être NULL pour un objet partagé de catalogue.
id_acteur peut être NULL pour un traitement automatique réel ; conserver l'acteur humain initiateur quand il existe.

Exemples : changement de statut, réassignation, changement d'échéance si une telle colonne était ajoutée plus tard, création d'un contrat, clôture d'une affectation, remise/restitution.
La version actuelle ne contient pas d'échéance de tâche.

L'application doit enregistrer la mutation métier ET son journal dans la même transaction.
Aucun trigger d'audit n'a été installé dans les scripts finaux.
Les traces des fixtures sont insérées explicitement ; leur présence ne prouve pas que les futurs écrans journalisent automatiquement.
Pas de modification ou suppression ordinaire du journal, pas d'historique limité au seul dernier statut.
Ne pas journaliser password ni les données d'authentification sensibles.

## 11. Documents et workflows

document_contrat sert notamment au PDF généré, au contrat signé et aux pièces associées.
date_signature décrit le fait métier ; un document stocké permet de consulter la pièce.
reference_fichier référence le stockage ; elle ne contient pas les octets du fichier.
type_document n'a pas de CHECK dans SIGRH.sql : CONTRAT_SIGNE est utilisable en plus des exemples du commentaire initial.
Le commentaire parle d'une référence unique, mais aucune contrainte UNIQUE ne l'impose actuellement.
Garder les documents historiques ; ne pas remplacer silencieusement l'ancien PDF signé.

« Workflow » désigne ici les actions, responsables et règles de progression.
Exemple : création RH -> attribution IT -> en cours -> remise effective -> tâche terminée -> étape recalculée.
Les étapes peuvent progresser en parallèle ; badge et messagerie ne sont pas nécessairement séquentiels.
Aucune table workflow ni moteur configurable n'est requis par le modèle final.

## 12. Fichiers de référence et données de test

Ordre pour une base neuve :
1. SIGRH.sql : schéma fourni par le groupe, blocs 1-3 et tables communes.
2. 01_bloc4_tables_manquantes.sql : neuf tables supplémentaires et deux vues.
3. 02_bloc4_donnees_test.sql : fixtures du bloc 4 et prérequis minimaux.

Ce sont des scripts de création/chargement, pas des migrations idempotentes à rejouer sur n'importe quelle base.
Le complément ne recrée pas les tables partagées.
Le seed refuse le préfixe B4 déjà présent pour éviter un second chargement.
Il ne supprime pas les données existantes et récupère les identifiants SERIAL via RETURNING.
Il peut compléter une base ayant déjà d'autres données ; ne jamais présumer que ses contrats ont les identifiants 1, 2, 3.

| Employé | Scénario au 22/10/2026 | Attendu |
| --- | --- | --- |
| B4N001, Soa | CDD 01/04/2026–30/09/2026, puis CDI dès 01/10/2026 ; mobilité Informatique -> Projets. | Un parcours sur le CDD d'entrée, TERMINE, 6/6 étapes. Deux contrats, une seule inscription paie. |
| B4N002, Rija | CDD signé dès 15/10/2026 ; changement de lieu le 20/10 ; ordinateur reçu, téléphone bloqué ; formation en cours, paie non confirmée. | BLOQUE, 3/6 étapes : signature, badge et compte terminés. |
| B4N003, Lova | CDI futur dès 01/11/2026, BROUILLON ; affectation future. | A_FAIRE, 0/6 étape. Ne pas confondre affectation prévue et affectation actuelle. |

Volumes propres au jeu :
4 contrats, 5 affectations, 7 document_contrat, 22 tâches, 67 traces CREATION + 16 MODIFICATION = 83 mouvements.
Les dates des scénarios sont fixes ; les résultats « actuel/futur » doivent être testés avec une horloge maîtrisée, pas supposés inchangés pour toutes les dates futures.
Les documents référencés sont fictifs : les INSERT ne génèrent pas de PDF.

Comptes d'acteurs de test :
| Login | Employé lié | Rôle |
| --- | --- | --- |
| b4_rh | B4RH001 | ROLE_RH |
| b4_it | B4IT001 | ROLE_MANAGER |
| b4_formation | B4FOR001 | ROLE_MANAGER |
| b4_paie | B4PAY001 | ROLE_FINANCE |
| b4_direction | B4DIR001 | ROLE_DIRECTION |

Mot de passe synthétique commun : Bloc4Test2026! ; les valeurs stockées sont des empreintes bcrypt.
Il ne s'agit pas de comptes de production. Les recrues B4N001/2/3 n'ont pas de login dans ce seed.

## 13. Avancement confirmé et limites

### Réalisé dans cette conversation
- Analyse des processus et définition des fonctionnalités.
- Alignement avec les tables actuelles du groupe.
- Conservation explicite de document_contrat et affectation.id_contrat.
- Création de 01_bloc4_tables_manquantes.sql et 02_bloc4_donnees_test.sql.
- Exécution avec le SIGRH.sql fourni dans PostgreSQL embarqué (PGlite).
- 72 vérifications réussies, sur base neuve puis base contenant des lignes préexistantes.
- Vérifications des scénarios, des FK/CHECK, des ressources, des empreintes de test, du refus de rechargement, de la réouverture et de l'atomicité d'une mutation avec son audit.

### Non confirmé
- Chargement effectif des scripts dans la base de travail de l'utilisateur.
- État réel du dépôt : code des collaborateurs et éventuels écrans existants non inspectés.
- Implémentation des services, contrôleurs, routes, écrans, formulaires et autorisations.
- Audit automatique de toutes les opérations de l'application.
- Stockage effectif, génération et téléchargement des PDF.
- Tests applicatifs et tests de permissions de bout en bout.
Les 72 contrôles SQL ne valident pas ces éléments.

### Limites connues à traiter explicitement
- Le CHECK du contrat empêche seulement de se référencer soi-même ; il ne bloque pas tous les cycles ni un précédent appartenant à un autre employé.
- Les FK d'affectation n'assurent pas que le contrat appartient au salarié indiqué.
- SIGNE n'impose pas date_signature au niveau SQL actuel.
- L'index des affectations ne contrôle que date_fin IS NULL, pas tous les chevauchements ni l'entrée en vigueur.
- Les index de badge/matériel bloquent deux détentions non clôturées, pas tous les chevauchements historiques.
- Le modèle sans date_fin_effective/avenant n'est pas un module complet de rupture anticipée ou de changements contractuels légaux.
- La checklist est fixe, sans dispense, facultatif ni tâche annulée.
- La tâche n'a pas de FK vers le matériel, compte ou formation précis : la complétion doit recevoir et vérifier une preuve métier explicite. Le détail de cette association côté service reste à implémenter.
- L'existence d'un enregistrement de ressource pour un salarié ne suffit pas toujours à prouver le résultat exact d'une tâche d'un autre parcours.

## 14. Suite attendue

L'utilisateur veut maintenant implémenter les fonctionnalités, dans l'architecture existante et avec le modèle simple.
Lire INSTRUCTIONS.md pour les étapes détaillées, règles d'accès, opérations transactionnelles, écrans, tests et critères de fin.
Ne pas réintroduire silencieusement les variantes abandonnées.
Les éventuelles nouvelles décisions d'implémentation doivent être signalées comme telles, pas présentées comme déjà prises dans la conversation.

### SIGRH.sql : schéma partagé du groupe

(database/SIGRH.sql + database/22-10-2026_01_bloc4_tables_manquantes.sql)
