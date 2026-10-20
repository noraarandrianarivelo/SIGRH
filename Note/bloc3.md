# Bloc 3 — Sélection et embauche

## 3.1. Objectif

Le candidat retenu reçoit une **proposition d'embauche**.

Attention :

```text
Candidat retenu != Employé
```
 
Le candidat devient salarié seulement après l'acceptation et la réalisation des étapes nécessaires à l'embauche.

---

## 3.2. Proposition d'embauche

La proposition contient les principales conditions proposées au candidat.

Exemple :

```text
Poste : Chauffeur
Département : Logistique
Salaire : 1 200 000 Ar
Date prévue de début : 01/12/2026
Horaire : 08h00 - 17h00
Lieu de travail : Antananarivo
Type de contrat : CDD
```

D'autres informations peuvent être définies selon le modèle de l'entreprise et le contrat :

- rémunération ;
- horaires ;
- lieu de travail ;
- date de début ;
- poste ;
- type de contrat ;
- avantages éventuels ;
- autres conditions.

---

## 3.3. Réponse du candidat

Le candidat peut avoir plusieurs états :

```text
EN_ATTENTE
ACCEPTEE
REFUSEE
```

Exemple :

```text
Proposition envoyée
        ↓
En attente de réponse
        ↓
Acceptée
```

ou :

```text
Proposition envoyée
        ↓
Refusée
```

---

## 3.4. Création du dossier salarié

Une fois la proposition acceptée, l'entreprise peut créer le dossier salarié.

```text
Candidat
   ↓
Proposition acceptée
   ↓
Création du salarié
```

C'est ici que l'on passe conceptuellement de :

```text
CANDIDAT
```

à :

```text
EMPLOYE
```

---

## 3.5. Pourquoi Candidat != Employé ?

Une personne peut être candidate sans jamais devenir salariée.

Exemple :

```text
Rakoto
   │
   ├── Candidature 2025 → refusée
   ├── Candidature 2026 → refusée
   └── Candidature 2027 → retenue → devient salarié
```

Il faut donc conserver les candidatures précédentes.

---

## 3.6. Matricule

Lors de la création du salarié, l'entreprise lui attribue un matricule.

Exemple :

```text
EMP-2026-00125
```

Le matricule devient l'identifiant professionnel du salarié dans l'entreprise.

Un document PDF peut également être généré avec les informations du salarié.

Exemple :

```text
DOSSIER SALARIÉ

Matricule : EMP-2026-00125
Nom : Rakoto
Poste : Chauffeur
Département : Logistique
Date d'entrée : 01/12/2026
```

---

## 3.7. Création du contrat

Après l'acceptation, le contrat est créé.

Le système doit notamment prendre en compte :

- type de contrat ;
- date de début ;
- date de fin lorsqu'elle existe ;
- rémunération ;
- horaires ;
- lieu de travail ;
- poste ;
- conditions prévues par l'entreprise ;
- documents associés.

Le contrat peut ensuite être généré sous forme de document PDF.

---

## 3.8. Affectation

Il faut également créer l'affectation du salarié.

Exemple :

```text
Employé : Rakoto
Poste : Chauffeur
Département : Logistique
Lieu : Antananarivo
Date de début : 01/12/2026
```

Objet :

```text
Affectation
-----------
id
employe
poste
departement
dateDebut
dateFin
...
```

L'affectation doit être historisée.

---
