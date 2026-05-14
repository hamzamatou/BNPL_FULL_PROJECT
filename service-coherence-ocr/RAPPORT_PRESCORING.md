# Rapport de documentation — Prescoring BNPL (service-coherence-ocr)

**Version :** 1.0  
**Périmètre :** module prescoring intégré au microservice Flask *service-coherence-ocr*, réutilisation du pipeline `bnpl-data-pipeline` (LightGBM, forêt d’isolation optionnelle, SHAP optionnel).

---

## 1. Objectif métier

Le **prescoring** expose une estimation de **probabilité de défaut (PD)** et un **score interne 0–1000** à partir de données financières et contractuelles déclarées, pour :

- alimenter un parcours **avant** ou **en complément** des contrôles OCR / cohérence pièces ;
- fournir aux **analystes crédit** des **alertes métier** (règles sur saisie brute), une **zone de risque** (feu tricolore), et un **texte d’explication** (SHAP + contexte).

Le prescoring **ne remplace pas** la décision humaine ni la politique produit ; il s’appuie sur un modèle calibré sur un historique d’entraînement.

---

## 2. Architecture technique

| Élément | Rôle |
|--------|------|
| `app/services/prescoring_service.py` | Chargement du bundle `.pkl`, prétraitement, inférence, assemblage JSON. |
| `app/api/routes.py` | Routes HTTP `GET /prescoring/ready` et `GET /prescoring/prescore`. |
| `app/config.py` | `BNPL_PIPELINE_DIR`, `BNPL_MODEL_PATH` (variables d’environnement). |
| Dépôt `bnpl-data-pipeline` | Ajout dynamique au `sys.path` : `test_rest_dataset.preprocess_full`, `predict_manual`, `shap_tools`. |
| Fichier `bnpl_model_production.pkl` | Bundle **joblib** produit par `train_GBMlight.py` (éventuellement enrichi IF par `train_GBMlight_isoforest.py --merge`). |

Le même processus Flask (`python run.py`, port **8090** par défaut) sert les routes cohérence OCR **et** prescoring.

---

## 3. Prérequis et configuration

### 3.1 Dépendances Python

Voir `requirements.txt` du service : en plus du stack cohérence OCR, le prescoring nécessite notamment **numpy**, **pandas**, **joblib**, **scikit-learn**, **lightgbm**, **shap** (pour les explications SHAP si le bundle contient un explainer).

### 3.2 Chemins

| Variable | Description | Défaut |
|----------|-------------|--------|
| `BNPL_PIPELINE_DIR` | Répertoire racine du dépôt **bnpl-data-pipeline** (imports Python). | Frère de `service-coherence-ocr` sous `uib-bnpl/bnpl-data-pipeline`. |
| `BNPL_MODEL_PATH` | Chemin absolu du fichier **joblib** du modèle. | `{BNPL_PIPELINE_DIR}/bnpl_model_production.pkl`. |

Exemple `.env` (commentaires dans `.env.example`) :

```env
BNPL_PIPELINE_DIR=C:/chemin/vers/bnpl-data-pipeline
BNPL_MODEL_PATH=C:/chemin/vers/bnpl_model_production.pkl
```

### 3.3 Modèle attendu

Le `.pkl` doit contenir au minimum : `model` (LightGBM calibré), `threshold`, `features`, encodeurs, bornes de clip, etc. (cf. `train_GBMlight.py`).

- **Forêt d’isolation** : si `isolation_forest` et `if_scaler` sont absents, le champ JSON `foret` vaut `null` (comportement normal tant que le merge IF n’a pas été fait).
- **SHAP** : si `shap_explainer` est absent ou si la librairie `shap` n’est pas installée, les explications détaillées SHAP sont indisponibles ; le reste de la réponse reste utilisable.

---

## 4. API — Endpoints

### 4.1 Santé prescoring

**`GET /prescoring/ready`**

- **200** : fichier modèle présent (prêt à inférer après premier chargement en mémoire).
- **503** : modèle introuvable ou erreur de chargement signalée dans le corps JSON.

### 4.2 Prescoring dossier

**`GET /prescoring/prescore`** — tous les paramètres en **query string** (pas de POST).

| Paramètre | Type | Obligatoire |
|-----------|------|-------------|
| `revenu_mensuel_net` | nombre | oui |
| `revenu_annuel` | nombre | oui |
| `charges_mensuelles_totales` | nombre | oui |
| `montant_demande` | nombre | oui (> 0) |
| `nbr_mois_remboursement` | nombre | oui (> 0) |
| `anciennete_emploi_mois` | nombre | oui (≥ 0) |
| `type_contrat` | texte | oui (ex. `CDI`, `CDD`) |

**Codes HTTP :** `200` succès, `400` paramètres invalides ou manquants, `503` modèle indisponible, `500` erreur interne.

**Exemple (PowerShell / navigateur) :**

```
http://localhost:8090/prescoring/prescore?revenu_mensuel_net=2200&revenu_annuel=26400&charges_mensuelles_totales=750&montant_demande=3000&nbr_mois_remboursement=12&anciennete_emploi_mois=48&type_contrat=CDI
```

---

## 5. Structure de la réponse JSON

L’ordre des champs est volontairement **aligné sur la lecture analyste** (forêt → PD → score → zone → alertes → texte → décision).

| Champ | Description |
|-------|-------------|
| `foret` | `null` si pas d’IF dans le bundle ; sinon objet avec `atypique` (bool), `score_echantillon`, `predict_sklearn` (+1 inlier, -1 outlier). |
| `pd_pct` | PD en **pourcentage** (0–100), arrondie affichage. |
| `score` | Score **0–1000** = `1000 × (1 − PD)` avec PD plafonnée dans [0, 1]. |
| `zone` | Feu tricolore basé sur la **PD** : vert ≤ 30 %, orange &gt; 30 % et ≤ 60 %, rouge &gt; 60 % (`code`, `couleur` hex, `libelle`). |
| `alertes` | Liste de chaînes : règles **métier sur saisie brute** (capacité, cohérence annuel/mensuel, CDD, etc.). |
| `explications` | Liste de **phrases** pour affichage analyste (forêt, zone, niveau qualitatif, lecture seuil, facteurs SHAP si disponibles, note de prudence). |
| `defaut` | `true` si PD **≥ seuil opérationnel** du `.pkl` (décision automatique modèle). |
| `seuil_pd_pct` | Seuil opérationnel en **pourcentage**. |

### 5.1 Cohérence saisie brute vs valeurs « moteur »

Les **alertes** utilisent les **entrées brutes** (ex. mensualité BNPL estimée `montant / (nbr_mois + 1)`).

Le **modèle** et le **SHAP** utilisent les variables après **feature engineering** et **clip** quantiles d’entraînement (`preprocess_full` dans `test_rest_dataset.py`). Les valeurs citées dans les explications peuvent donc **différer** des seules saisies (ex. durée en mois relevée au plancher historique). Le texte d’explication le rappelle lorsque les blocs SHAP sont présents.

### 5.2 Zone couleur vs classe `defaut`

- **`zone`** : grille **affichage** (vert / orange / rouge) selon **PD** (0–30 %, 30–60 %, &gt; 60 %).
- **`defaut`** : règle **opérationnelle** du modèle (PD comparée au **seuil** stocké dans le bundle, typiquement ~20–25 % selon calibration).

Les deux peuvent sembler « décalées » pour un analyste : c’est attendu (grilles différentes).

---

## 6. Chaîne d’entraînement côté `bnpl-data-pipeline` (rappel)

1. `train_GBMlight.py` : préparation données, clip, encodage, LightGBM + calibration, seuil, export `bnpl_model_production.pkl`, explainer SHAP si `shap` installé.
2. Optionnel : `train_GBMlight_isoforest.py` sans `--no-merge` : ajoute IF + scaler au même bundle.

Sans étape 2, `foret` reste `null`. Sans SHAP à l’entraînement, les explications SHAP dans `explications` sont limitées à un message d’indisponibilité.

---

## 7. Limites et bonnes pratiques

- Ne pas interpréter la PD comme une probabilité « réelle » hors domaine d’application du modèle.
- Croiser systématiquement avec **pièces**, **incidents**, **politique interne** et **alertes** métier.
- Vérifier **`/prescoring/ready`** avant exposition publique (CI/CD, load balancer health).

---

## 8. Références fichiers

- Service : `service-coherence-ocr/app/services/prescoring_service.py`, `app/api/routes.py`, `app/config.py`.
- Pipeline : `bnpl-data-pipeline/test_rest_dataset.py`, `predict_manual.py`, `shap_tools.py`, `train_GBMlight.py`, `train_GBMlight_isoforest.py`.

---

*Document généré pour le projet UIB BNPL — prescoring intégré au service cohérence OCR.*
