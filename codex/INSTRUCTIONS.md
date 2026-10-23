# INSTRUCTIONS — Implémenter le bloc 4 de SIGRH

Date : 22 octobre 2026.
À utiliser avec CHATGPT_CONTEXT.md et les fichiers SQL compagnons.

## 1. Mission

Implémenter réellement, dans le dépôt existant, les fonctionnalités proposées par l'utilisateur :
1. Consultation et gestion des contrats successifs.
2. Consultation et gestion des affectations.
3. Attribution et réassignation des tâches d'onboarding.
4. Exécution des tâches par leur responsable avec enregistrement des résultats métier.
5. Suivi automatique de l'onboarding par employé et contrat d'entrée.
6. Consultation de l'historique et des documents contractuels.

- Utiliser le même style que les autres pages (en se réferrant à `sigrh/src/main/resources/static/css/recrutement.css`)

Le résultat doit être utilisable depuis l'interface et protégé dans le serveur.
Ne pas s'arrêter à des modèles, à des routes sans écrans, à des boutons sans traitement ou à une proposition de code.
Réutiliser ce qui existe déjà ; compléter les fonctionnalités partielles plutôt que les dupliquer.
Rester simple : pas de moteur de workflow générique, nouvelle application parallèle, nouvelle architecture ou nouvelle famille de tables.

Les règles métier confirmées sont dans CHATGPT_CONTEXT.md. Les propositions techniques de ce fichier (noms de services, routes, détails d'interface) sont à adapter aux conventions du dépôt.

## 2. Commencer par examiner le dépôt

Avant les changements :
- Lire les consignes du dépôt et les éventuels AGENTS.md applicables.
- Identifier backend, frontend, accès aux données, authentification, stockage, migrations, formulaires, navigation et tests.
- Chercher contrat, affectation, document_contrat, onboarding_tache, utilisateur, id_employee et role_name.
- Vérifier ce que les blocs 1-3 ont déjà implémenté : création de l'employé, du contrat initial, des documents et de l'affectation. 
(Les blocs 1-3 doivent être déja implémenté par les collaborateurs)
- Comparer le schéma réel et les migrations avec SIGRH.sql et 01_bloc4_tables_manquantes.sql.
- Identifier les commandes réelles de compilation, tests et lancement dans README et fichiers de configuration.
(il n'y a pas de fichier README, mais crèes-en)
- Repérer la manière dont un utilisateur connecté est relié à son employé et comment sont calculés les périmètres d'accès.

Ne pas déduire le framework à partir d'un autre projet évoqué avec ChatGPT.
Si le dépôt utilise du rendu serveur, compléter ses pages et formulaires ; si c'est une API avec frontend séparé, compléter les deux.
Ne pas installer un nouveau frontend ou remplacer l'authentification pour cette fonctionnalité.

Après cette inspection, résumer brièvement l'existant, les composants à compléter et les commandes de validation, puis avancer sur l'implémentation.

## 3. Respecter les scripts et les types

Pour une base de test neuve :
~~~text
SIGRH.sql
01_bloc4_tables_manquantes.sql
02_bloc4_donnees_test.sql
~~~

Ces fichiers ne sont pas des scripts de remise à zéro et ne sont pas à rejouer sans contrôle sur une base déjà installée.
Si le projet possède des migrations, ajouter uniquement les objets absents via son mécanisme habituel.
Ne pas exécuter DROP, TRUNCATE ou recréation des tables partagées pour faciliter les tests. Utiliser une base de test isolée.

Points impératifs :
- employe.id et toutes ses FK sont des chaînes VARCHAR(10).
- utilisateur.id est numérique ; utilisateur.id_employee est l'identifiant de l'employé.
- Les assignateurs, responsables et acteurs des nouvelles tables sont des identifiants d'employés, pas utilisateur.id.
- Les autres clés sont INTEGER/SERIAL selon le DDL.
- salaire_mensuel est NUMERIC(14,2), salaire mensuel de base en MGA ; utiliser un type décimal.
- Ne pas confondre DATE, TIMESTAMP et TIMESTAMPTZ ; conserver les dates métier sans décalage de jour.
- Réutiliser contrat, affectation, document_contrat, employe, poste, departement, utilisateur et role_user.
- Ne pas recréer contrat_signature, PaieContrat, PaieEmploye, onboarding ou onboarding_etape.
- Ne pas créer une table document concurrente de document_contrat.
- Ne pas supposer une colonne date_fin_effective, obligatoire, date_echeance ou commentaire dans onboarding_tache.
- Les vues de suivi sont en lecture seule. Aucun UPDATE de statut de parcours ou d'étape.
- Éviter toute génération automatique de DDL par un ORM qui réécrirait les tables existantes.

## 4. Politique d'accès

### 4.1 Identité et portée

Résoudre l'identité côté serveur depuis la session/le mécanisme d'authentification du dépôt.
Le lien réel est :
~~~sql
SELECT u.id, u.id_employee, r.role_name
FROM utilisateur u
JOIN role_user r ON r.id = u.id_role
WHERE u.id = $1;
~~~

Adapter la liaison du paramètre à la bibliothèque utilisée.
Ne jamais faire confiance à id_acteur ou id_assignateur envoyés par le client.
Si l'utilisateur humain n'a pas d'employé lié, ne pas lui attribuer un acteur arbitraire ni utiliser NULL pour masquer cette absence.

Un employé consulte ses propres informations lorsque le parcours salarié existe.
Un responsable de tâche voit les informations nécessaires à cette tâche, sans obtenir automatiquement le salaire, les autres contrats ou tout le journal du salarié.
Contrôler chaque objet et chaque action, y compris les téléchargements et les endpoints secondaires.

### 4.2 Matrice minimale

La conversation confirme les responsabilités, mais pas toute la politique de rôles du dépôt.
Réutiliser ses autorisations explicites. À défaut de règle existante, appliquer la politique minimale ci-dessous et la documenter :

| Action | Politique minimale |
| --- | --- |
| Consulter ses contrats, affectations et suivi | Utilisateur lié à l'employé concerné, sur ses propres données. |
| Gérer les contrats et affectations | ROLE_RH, ou rôle déjà explicitement habilité par le projet. |
| Configurer la checklist, créer/réassigner les tâches | Assignateur habilité, par défaut RH. |
| Changer le statut d'une tâche | Son responsable ACTUEL, après relecture transactionnelle de l'attribution. |
| Valider le résultat d'une tâche | Responsable actuel disposant de la capacité métier correspondante ; une tâche ne contourne pas les droits de signature/paie existants. |
| Consulter une liste RH globale et le journal transversal | RH ou rôle explicitement habilité. |
| Consulter en tant que manager | Seulement le périmètre défini par le code existant, sans accès global implicite aux salaires. |

ROLE_MANAGER, ROLE_FINANCE et ROLE_DIRECTION ne doivent pas recevoir automatiquement les droits de gestion des contrats.
Le rôle finance peut traiter une inscription à la paie qui lui est légitimement attribuée sans devenir administrateur de tous les contrats.
Les fixtures IT/formation utilisent ROLE_MANAGER : leur accès aux tâches doit dépendre de l'attribution, pas d'identifiants de test codés en dur.

Ne pas attribuer le rôle manager à une recrue uniquement pour simuler un compte salarié.
Si l'accès salarié n'existe pas encore, compléter le minimum nécessaire dans les conventions d'authentification du dépôt et signaler ce complément.

Les boutons masqués ne remplacent pas les vérifications serveur.
Dans une API, utiliser les réponses d'authentification, de refus, de validation et de conflit prévues par le projet ; ne pas masquer une erreur par un succès HTTP.
Tous les messages affichés à l'utilisateur doivent être compréhensibles en français.

## 5. Préparer la couche transactionnelle et l'audit

Mettre en place un petit service/helper commun pour mouvement_rh, réutilisé par les opérations métier.
Ne pas créer une couche de workflow générique ni un système d'événements distribué.

Pour chaque mutation :
1. Vérifier l'authentification et les autorisations.
2. Commencer la transaction selon le framework existant.
3. Relire/verrouiller les lignes nécessaires.
4. Revalider les préconditions après verrouillage.
5. Capturer l'état avant.
6. Écrire les changements métier.
7. Capturer l'état après.
8. Insérer une trace par ligne métier modifiée.
9. Valider la transaction.
10. Relire le suivi et retourner/afficher le résultat réel.

Si une validation, une mutation ou l'insertion du journal échoue, toutes les écritures DB de l'opération doivent être annulées.

Champs à fournir au journal :
- id_employe_concerne : salarié réel, ou NULL pour un catalogue partagé ;
- id_acteur : employé relié à l'utilisateur connecté, sauf véritable automatisme ;
- table_concernee : nom contrôlé par le code ;
- id_ligne : clé convertie en texte ;
- action : CREATION, MODIFICATION ou SUPPRESSION ;
- valeurs_avant et valeurs_apres : respecter les CHECK et conserver les champs pertinents de la ligne ;
- date_mouvement : horodatage serveur/DB, sans confiance dans une date fournie par le navigateur.

Ne pas sérialiser un graphe ORM complet avec relations cycliques, password ou données de session.
Le journal doit conserver aussi les réassignations et changements de données, pas seulement les statuts.
Pas d'endpoint ordinaire de modification ou suppression du journal.
Les anciennes lignes métier restent conservées ; ne pas exposer un CRUD destructif par défaut.

Pour éviter les courses :
- Sérialiser les opérations contractuelles/d'affectation d'un même employé en verrouillant une ligne stable du salarié avant les contrôles de périodes.
- À la complétion d'une tâche, verrouiller/recharger la tâche et vérifier à nouveau son responsable.
- Protéger les remises de matériel par la contrainte SQL et les verrous appropriés.
- Garder un ordre de verrouillage cohérent entre services pour éviter les interblocages.
- Traduire les violations d'unicité en messages de conflit compréhensibles.

## 6. Fonctionnalité : contrats et documents

### 6.1 Consultation

Sur la fiche d'un salarié, afficher :
- le contrat en vigueur ;
- le contrat futur/préparé lorsqu'il existe, clairement distingué ;
- la liste chronologique des contrats antérieurs ;
- type, dates, salaire, horaires, lieu, conditions, date de signature et statut ;
- le lien vers le précédent et les documents accessibles.

Ne pas définir « actuel » par le plus grand id.
Vérifier le statut, la signature et les dates d'effet. Un contrat signé pour le mois prochain n'est pas encore en vigueur.
Les fins sont inclusives.
Si des données incohérentes donnent plusieurs contrats en vigueur, signaler le conflit et ne pas en choisir un arbitrairement.

### 6.2 Brouillon et renouvellement

Autoriser le responsable habilité à préparer/modifier un contrat BROUILLON.
Pour un renouvellement ou changement de type :
1. Identifier le salarié et le contrat précédent.
2. Vérifier que le précédent est bien à lui et que la chaîne ne comporte pas de cycle.
3. Proposer les conditions existantes comme valeurs initiales du formulaire.
4. Créer UNE NOUVELLE ligne contrat avec id_contrat_precedent.
5. Laisser id_proposition NULL si aucune nouvelle proposition n'existe.
6. Conserver les conditions et documents de l'ancien contrat.
7. Vérifier salaire non négatif, dates et périodes contractuelles applicables.
8. Journaliser la création et les éventuelles clôtures métier réellement effectuées.

Ne pas imposer une nouvelle proposition/candidature pour chaque renouvellement.
Une proposition fournie pour une première embauche doit être acceptée et conforme au flux du bloc 3.
La préparation d'un futur contrat ne doit pas terminer prématurément le contrat encore en vigueur.
Ne pas écraser l'ancien CDD pour le transformer en CDI.
Ne pas modifier les clauses d'un contrat signé depuis un simple formulaire d'édition.

La version minimale n'est pas un module d'avenants ou de rupture anticipée.
Le modèle n'a pas de date_fin_effective : ne pas simuler cette capacité avec un champ inexistant.
Gérer les échéances naturelles et les transitions nécessaires au renouvellement ; signaler un besoin supplémentaire de rupture/avenant avant d'élargir le modèle.

### 6.3 Signature

Prévoir l'enregistrement autorisé de date_signature et la transition vers SIGNE.
Vérifier les données requises et les dates ; la contrainte SQL actuelle ne suffit pas à garantir la présence de la date.
Conserver un document signé quand il est fourni.
Si une tâche SIGNATURE_CONTRAT est concernée, traiter le contrat d'entrée de CETTE tâche, pas le dernier contrat du salarié.
Une signature de renouvellement ne doit pas créer un nouveau parcours d'onboarding.

### 6.4 Documents contractuels

Réutiliser document_contrat :
id, id_contrat, type_document, reference_fichier, date_ajout.

Fonctions attendues :
- lister les documents d'un contrat ;
- consulter/télécharger selon les droits du salarié ou du responsable ;
- ajouter le contrat signé et les pièces nécessaires ;
- réutiliser la génération PDF déjà faite par le bloc 3, lorsqu'elle existe.

Ne pas réimplémenter tout le module de génération du bloc 3 si une fonctionnalité existe déjà.
Les fixtures ne contiennent que des chemins fictifs : afficher proprement une indisponibilité plutôt qu'une erreur serveur brute.

Utiliser le stockage configuré par le projet.
Valider les fichiers et leur taille ; produire une référence de stockage contrôlée, pas un chemin arbitraire fourni par le navigateur.
Vérifier les autorisations de téléchargement côté serveur.
Ne pas remplacer silencieusement un ancien PDF signé.
Journaliser l'ajout de la référence.

Une transaction PostgreSQL ne rend pas le système de fichiers transactionnel :
prévoir une écriture temporaire/compensation simple pour éviter fichiers orphelins ou références invalides en cas d'échec. Réutiliser une solution existante si disponible.

## 7. Fonctionnalité : affectations

### 7.1 Consultation

Afficher le poste, le département déduit du poste, le lieu, les dates et le contrat d'origine.
Présenter l'historique des périodes, et distinguer une affectation future d'une affectation actuelle.
date_fin NULL ne suffit pas à dire « actuelle » : date_debut doit aussi être atteinte.

La relation est :
affectation.id_poste -> poste.id_departement -> departement.
Ne pas réintroduire affectation.id_departement.
Conserver affectation.id_contrat : il indique l'origine, pas forcément le contrat actuel pendant toute la durée de l'affectation.

### 7.2 Changement transactionnel

Pour un changement effectif au jour D :
1. Vérifier le droit de gestion et verrouiller le salarié.
2. Relire l'affectation et les périodes pertinentes.
3. Vérifier le poste cible, le contrat d'origine s'il est fourni et l'identité du salarié.
4. Contrôler l'absence de chevauchement.
5. Clôturer l'ancienne affectation à D-1.
6. Insérer la nouvelle à partir de D, avec son poste, lieu et motif.
7. Mettre employe.id_departement en cohérence avec l'affectation réellement en vigueur.
8. Insérer les traces pour les lignes affectation et employe réellement modifiées.
9. Valider le tout.

Ne pas écraser id_poste/id_contrat de l'ancienne affectation.
Ne pas déplacer le département du poste de référence pour effectuer une mobilité individuelle.
Si D est le même jour que le début de l'ancienne période, ne pas produire une fin antérieure au début : traiter la correction explicitement avec historique ou refuser l'opération incompatible.

L'index uq_affectation_en_cours ne couvre que les lignes sans fin ; contrôler aussi les périodes datées.
Pour rester simple, privilégier les changements effectifs aujourd'hui dans le premier écran.
Si des mobilités futures sont exposées, ne pas mettre immédiatement le département courant du salarié au département futur : prévoir un traitement explicite de la prise d'effet dans l'architecture existante.
Ne pas ajouter une planification automatique incomplète qui rendrait la copie employe.id_departement fausse au jour d'effet.

## 8. Fonctionnalité : configuration et attribution des tâches

### 8.1 Choisir le bon parcours

L'identifiant du parcours est le contrat d'entrée.
Retrouver ce contrat en suivant les précédents du salarié avec contrôle de cycles.
Ne pas choisir simplement le contrat actuel, le dernier id ou le CDI issu du renouvellement.

Prévoir sur la fiche du salarié une section « Onboarding / intégration du [date d'entrée] ».
Le contrat courant et le contrat d'entrée peuvent être différents ; expliquer cette différence dans l'interface si nécessaire.
Conserver les parcours antérieurs lors d'une réembauche distincte.

### 8.2 Configurer la checklist

Présenter les six catégories :
SIGNATURE_CONTRAT, BADGE, COMPTE, MATERIEL, FORMATION, INSCRIPTION_PAIE.

Les vues les affichent même si aucune tâche n'est encore créée.
La configuration doit permettre de créer au moins une tâche pertinente par catégorie et plusieurs tâches si le besoin le demande.
Chaque tâche est nécessaire dans cette version ; ne pas ajouter dans l'UI une dispense ou une case facultative sans modification explicite du modèle.

Formulaire de création :
- contrat d'entrée choisi/validé ;
- étape parmi les six valeurs ;
- libellé précis décrivant le résultat ;
- responsable choisi parmi les employés autorisés à recevoir la tâche.

Le serveur déduit id_assignateur de l'utilisateur connecté.
Vérifier qu'un responsable destiné à agir dans l'application possède un accès utilisateur cohérent, ou afficher clairement que son accès est à préparer.
Ne pas auto-attribuer silencieusement les six tâches à des personnes arbitraires.

Exemples :
- « Activer la messagerie » et « Activer l'accès ERP » : deux tâches COMPTE.
- « Remettre l'ordinateur » et « Remettre le téléphone » : deux tâches MATERIEL.
- Une tâche par formation nécessaire si plusieurs formations sont demandées.

Une réassignation modifie seulement le responsable actuel de la même tâche et ajoute une trace avec avant/après.
Ne pas remplacer id_assignateur à chaque réassignation : il désigne l'assignateur d'origine ; le journal identifie l'auteur de chaque changement.
Permettre les corrections raisonnables du libellé par l'assignateur autorisé, en les traçant.
Ne pas déplacer une tâche déjà avancée vers un autre employé/contrat pour corriger rapidement une erreur.

## 9. Fonctionnalité : exécution et preuve métier

### 9.1 Statuts

Le responsable actuel peut utiliser :
- A_FAIRE -> EN_COURS ;
- A_FAIRE/EN_COURS -> BLOQUEE ;
- BLOQUEE -> EN_COURS/A_FAIRE ;
- un état non terminé -> TERMINEE lorsque le résultat est validé ;
- TERMINEE -> EN_COURS/A_FAIRE pour une réouverture autorisée et tracée.

Adapter l'expérience à ce flux simple ; ne pas créer un moteur d'états.
Une tâche TERMINEE possède date_completion, attribuée côté serveur.
Toute réouverture remet cette date à NULL. Les anciennes complétions restent dans le journal.
Une requête répétée ne doit pas dupliquer une remise, une inscription ou une trace de transition inexistante : gérer le rejeu selon l'état courant.

Ne pas exposer ANNULEE ou NON_APPLICABLE : ces valeurs n'existent pas dans le DDL final.
Ne pas supprimer une tâche terminée pour réinitialiser le parcours.
La suppression de tâches n'est pas une fonctionnalité demandée ; privilégier correction, réassignation ou réouverture.

### 9.2 Complétion : vérifier la réalisation précise

Ne pas offrir un bouton universel qui passe à TERMINEE sans enregistrer/vérifier le résultat.

| Étape | Formulaire/réalisation minimale | Écriture ou contrôle |
| --- | --- | --- |
| SIGNATURE_CONTRAT | Date de signature, contrat d'entrée, document signé si fourni. | contrat et éventuellement document_contrat. |
| BADGE | Numéro, date de remise ou attribution existante explicitement sélectionnée. | badge_employe du bon salarié et du bon contexte. |
| COMPTE | Système, identifiant, date d'activation ou compte opérationnel existant explicitement sélectionné. | compte_employe ; aucune demande de mot de passe externe. |
| MATERIEL | Objet physique choisi et date de réception, ou attribution correspondante existante. | materiel_employe ; disponibilité et périodes vérifiées. |
| FORMATION | Formation et inscription choisies, date de complétion réelle. | formation_employe ; une date prévue/une inscription ne suffit pas. |
| INSCRIPTION_PAIE | Confirmation et dates d'inscription/prise en charge. | inscription_paie ; pas de simple salaire ou dossier envoyé. |

Les données métier et la tâche doivent concerner le même salarié.
Le résultat doit correspondre à l'action réellement demandée : un ordinateur déjà reçu ne prouve pas la remise d'un téléphone.
Ne pas déduire automatiquement un identifiant de ressource en analysant le texte libre du libellé.
Faire sélectionner le résultat précis par le responsable, puis contrôler les références et la réalisation.

Limite du schéma : onboarding_tache n'a pas de FK vers chaque ressource précise.
Solution minimale recommandée, sans table supplémentaire :
- recevoir la référence métier exacte dans la commande de complétion ;
- vérifier/créer cette ligne dans le service ;
- conserver sa référence dans le journal de complétion, par exemple une métadonnée contrôlée _preuve ajoutée à valeurs_apres, en plus des champs de la tâche.
Cette convention est une proposition d'implémentation, pas une colonne du schéma ni une contrainte déjà installée.
L'écran d'historique doit rester compatible avec les snapshots des fixtures qui n'ont pas cette métadonnée.

Exemple de métadonnée, générée par le serveur à partir d'une ligne vérifiée :
~~~json
{
  "_preuve": {
    "table": "materiel_employe",
    "id": "42"
  }
}
~~~
Il s'agit d'un extrait de valeurs_apres ; ne pas remplacer le reste du snapshot par ce seul objet.
Ne pas autoriser le client à déclarer arbitrairement une table et une preuve sans validation.
Le catalogue materiel n'a pas de catégorie structurée : ne pas prétendre garantir « téléphone vs ordinateur » via une colonne inexistante ; l'interface de sélection et la confirmation humaine doivent rendre le choix explicite.

### 9.3 Algorithme d'une complétion

~~~text
Résoudre l'utilisateur connecté et son employé.
Ouvrir une transaction.
Charger le parcours et verrouiller les lignes pertinentes selon un ordre cohérent.
Relire la tâche : elle existe, elle appartient au parcours attendu,
et le demandeur est encore son responsable.
Vérifier ses capacités métier et la transition.
Vérifier les données spécifiques à l'étape et la ressource précise choisie.
Créer ou mettre à jour les faits métier nécessaires, sans doublonner.
Journaliser chaque ligne métier réellement créée/modifiée.
Passer la tâche à TERMINEE avec horodatage serveur.
Journaliser la tâche et sa preuve.
Valider la transaction.
Relire les deux vues et rafraîchir la page/la réponse.
~~~

L'état des vues change à la lecture suivante ; aucun websocket n'est nécessaire pour la version minimale.
Après toute mutation, rafraîchir les compteurs et les listes à partir du serveur.

### 9.4 Réutiliser les faits existants et préserver l'histoire

Le bloc 3 peut déjà avoir fait signer le contrat ou créé l'affectation.
Au démarrage du suivi, proposer de confirmer les réalisations déjà établies plutôt que demander de les refaire.
Ne pas prendre n'importe quel badge, compte ou formation d'un ancien parcours comme preuve du parcours actuel.
Vérifier les dates et le contexte ; demander une sélection/confirmation si l'association n'est pas certaine.

Un matériel rendu après un onboarding réussi ne doit pas faire réouvrir celui-ci.
Le suivi exprime l'achèvement historique des tâches ; la liste des ressources actuelles décrit une autre réalité.

## 10. Écrans métier minimaux et ressources

Réutiliser la navigation et les composants du projet.

| Écran/section | Contenu et actions |
| --- | --- |
| Liste des employés autorisés | Accès à leur fiche, avec filtres simples si déjà prévus. |
| Fiche employé — contrats | Contrat actuel, futur et historique ; préparation/renouvellement, signature, documents. |
| Fiche employé — affectations | Affectation actuelle et historique ; formulaire de changement réservé. |
| Fiche employé — onboarding | Contrat d'entrée, six cartes/lignes d'étapes, compteurs, tâches et blocages. |
| Configuration RH | Ajout de tâches et réassignation avec responsables. |
| Mes tâches | Seulement les tâches dont l'utilisateur connecté est responsable ; filtres par statut. |
| Détail de tâche | Identité du salarié, étape, résultat attendu, état et formulaire métier de complétion. |
| Ressources du salarié | Comptes remis, badges, matériels, formations et confirmation paie. |
| Historique | Filtrage par salarié/objet/action, acteur, date et différences lisibles avant/après. |

Les catalogues materiel et formation doivent être suffisamment gérables pour que les formulaires ne dépendent pas de données codées en dur.
Une ligne materiel représente un exemplaire ; afficher son id/nom pour le distinguer.
Prévoir la restitution minimale des badges et matériels si elle est nécessaire à la réutilisation ; conserver la ligne et sa date de restitution, avec trace.
Ne pas transformer cela en gestion complète d'inventaire, d'achats, de pannes ou d'offboarding.

Pour les formations, autoriser plusieurs inscriptions successives au même cours ; ne pas ajouter un UNIQUE qui empêcherait de le refaire.
Pour la paie, afficher clairement « inscription confirmée » et la prise d'effet ; ne pas afficher « payé » sur cette seule base.
Un renouvellement ne duplique pas le dossier de paie. Une réembauche distincte doit être vérifiée explicitement.

Prévoir les états vides, erreurs de validation, chargements, confirmations de réussite et conflits.
Ne pas afficher de boutons qui échouent systématiquement pour le rôle courant.
Ne pas masquer une liste vide due à une erreur serveur en « aucune donnée ».
Limiter les informations sensibles envoyées au navigateur à celles nécessaires à son rôle.

## 11. Opérations serveur attendues

Noms illustratifs à adapter aux routes réelles ; ce tableau ne prescrit pas un framework ni une API REST si le projet n'en utilise pas.

| Opération | Contrôle principal |
| --- | --- |
| Lister les employés accessibles | Rôle/périmètre ; pas liste globale pour tout compte. |
| Lire les contrats d'un employé et un contrat | Employé concerné ou responsable autorisé ; contrôle par objet. |
| Créer/modifier un brouillon de contrat | Responsable habilité, règles du contrat. |
| Renouveler/passer à un nouveau contrat | Même employé, nouvelle ligne et lien précédent. |
| Enregistrer la signature | Droit métier, données cohérentes et document si fourni. |
| Lister/télécharger/ajouter un document | Droit sur le contrat et stockage contrôlé. |
| Lire/changer une affectation | Droit par salarié et transaction de changement. |
| Lire le suivi et les étapes | Parcours/salarié autorisés ; lecture des vues. |
| Créer/réassigner une tâche | Assignateur habilité, parcours d'entrée et responsable validés. |
| Lire mes tâches | Filtre serveur sur utilisateur.id_employee. |
| Changer l'état d'une tâche | Responsable actuel revalidé après verrouillage. |
| Terminer une tâche avec résultat métier | Autorisation, preuve exacte et transaction commune. |
| Lire/gérer catalogues et remises | Droit RH ou traitement légitime d'une tâche ; pas accès global implicite. |
| Lire le journal | Rôle/périmètre autorisés ; réponse filtrée. |

Utiliser des DTO/formulaires explicites pour éviter qu'un client modifie salaire, propriétaire, rôle ou acteur par des champs supplémentaires.
Utiliser des requêtes paramétrées.
Les services internes de réalisation doivent être utilisables par les responsables légitimes de tâches sans exiger arbitrairement un rôle RH pour toute action ; le contrôle d'accès doit correspondre au cas d'usage.

## 12. Tests et validation demandés

Réutiliser le framework de test du dépôt. Écrire des tests métier utiles, sans multiplier des tests qui ne font que refléter des getters/setters.
Les tests transactionnels/PostgreSQL sont particulièrement importants ici.
Les 72 vérifications antérieures concernent le SQL et les fixtures, pas l'application.

### 12.1 Contrats et affectations
- Identifiants employés alphanumériques conservés, pas convertis en nombres.
- Contrat futur signé distingué du contrat actuel avec une horloge contrôlée.
- Renouvellement sans nouvelle proposition accepté et ancien contrat préservé.
- Contrat précédent d'un autre employé ou chaîne cyclique refusés.
- Salaire négatif, dates inversées et périodes applicables incohérentes refusés.
- Signature sans date refusée dans le service.
- Changement d'affectation : ancienne fin D-1, nouvelle période D, bon contrat, département courant cohérent.
- Échec en cours d'opération : aucun état partiel ni journal isolé.
- Tentatives concurrentes d'affectation/renouvellement : pas deux décisions contradictoires validées.
- Employé non autorisé : accès/refus vérifié aussi sur un id deviné directement.

### 12.2 Onboarding et ressources
- Catégorie sans tâche : A_CONFIGURER.
- Deux tâches matérielles : la première terminée seule ne termine pas l'étape.
- Dernière tâche nécessaire terminée : étape puis parcours recalculés.
- Tâche rouverte : date_completion NULL, progression à nouveau incomplète et trace conservée.
- Le responsable peut agir sur sa tâche ; l'assignateur non responsable ne la termine pas implicitement.
- Après réassignation, l'ancien responsable ne peut plus terminer la tâche.
- Réassignation concurrente avec complétion : revalidation et absence d'état partiel.
- Mauvais salarié, mauvaise attribution ou ressource indisponible : complétion refusée.
- Deux remises simultanées du même matériel/badge : une seule détention courante.
- Date de réception/restitution invalide et chevauchement historique pertinent refusés.
- Inscription de formation seule ou salaire de contrat seul ne valident pas l'étape correspondante.
- Renouvellement : pas de second onboarding automatique, pas de seconde inscription paie automatique.
- Restitution ultérieure : ancien onboarding terminé préservé.
- Rejeu d'une complétion : pas de doublon de ressource ou d'inscription.

### 12.3 Audit, documents et accès
- Toute mutation réussie produit les snapshots attendus ; aucun password n'est journalisé.
- Annulation transactionnelle : mutation et journal annulés ensemble.
- Acteur/assignateur envoyés arbitrairement par le client ignorés ou refusés.
- Journal impossible à modifier/supprimer par l'interface normale.
- Manager/IT assigné : accès à sa tâche sans fuite de salaire ou de documents non nécessaires.
- Téléchargement de document non autorisé ou chemin arbitraire refusé.
- Référence de PDF fictif gérée proprement.
- Les fichiers de test réels nécessaires au test d'upload restent distincts des simples chemins des fixtures.

### 12.4 Parcours de démonstration

Charger les fixtures dans une base de test et vérifier au 22/10/2026 :
| Employé | Contrats/affectations | Suivi |
| --- | --- | --- |
| B4N001 Soa | CDD terminé, CDI courant, mobilité vers Projets, documents historiques. | TERMINE, 6/6 ; tâches sur le CDD d'entrée ; une inscription paie. |
| B4N002 Rija | CDD signé, changement de lieu, ordinateur reçu, téléphone manquant. | BLOQUE, 3/6 ; formation en cours, inscription paie absente. |
| B4N003 Lova | Contrat BROUILLON et affectation future au 01/11/2026. | A_FAIRE, 0/6. |

Les identifiants numériques de ces contrats ne sont pas fixes.
Les comptes b4_rh, b4_it, b4_formation, b4_paie et b4_direction utilisent Bloc4Test2026! dans ce jeu synthétique.
Vérifier l'encodeur de mot de passe existant avant de conclure que le login est intégré : les fixtures bcrypt ont été vérifiées hors authentification applicative.
Les nouvelles recrues n'ont pas de login dans ce seed ; préparer un accès de test adapté si le parcours salarié est testé.

Exécuter aussi le build, les tests existants pertinents et les contrôles frontend/backend du dépôt.
Une erreur découverte dans un autre module doit être distinguée d'une régression introduite ; corriger ce qui bloque l'intégration autorisée sans réécrire les blocs 1-3.

## 13. Ordre de réalisation et critères de fin

Ordre conseillé :
1. Examiner le dépôt et vérifier le schéma et l'identité.
2. Ajouter les mappings/accès aux données nécessaires et le journal transactionnel.
3. Implémenter contrats et documents avec leurs écrans.
4. Implémenter les affectations avec historique et synchronisation.
5. Implémenter la configuration/réassignation et « Mes tâches ».
6. Implémenter les formulaires de réalisation des six étapes et leur atomicité.
7. Afficher les vues et compteurs d'onboarding.
8. Ajouter la consultation du journal.
9. Exécuter les tests, corriger et vérifier le parcours utilisateur.

Critères de fin :
- Les trois fonctionnalités d'origine fonctionnent depuis l'interface.
- Les rôles/périmètres sont contrôlés dans les opérations serveur.
- Les anciens contrats et affectations restent consultables.
- Les documents sont protégés et les données fictives ne provoquent pas d'erreurs brutes.
- Les tâches appartiennent au bon contrat d'entrée ; les résultats métier sont enregistrés avant validation.
- Le suivi est calculé avec les mêmes règles que les vues, sans statut dupliqué.
- Les opérations sont journalisées réellement, pas uniquement dans le seed.
- Les conflits ne laissent ni remise partielle ni historique trompeur.
- Les tests et commandes de lancement sont documentés.
- Toute limite restante est identifiée précisément.

Dans le compte rendu final à l'utilisateur :
indiquer les fonctionnalités réalisées, fichiers/composants modifiés, changements de schéma éventuels, commandes exécutées, résultats des tests, manière d'essayer les parcours et limites non résolues.
Expliquer les choix sans prétendre que des tests SQL prouvent les droits ou l'interface.
Ne pas déclarer une fonctionnalité terminée si elle ne possède qu'un bouton ou un endpoint factice.

