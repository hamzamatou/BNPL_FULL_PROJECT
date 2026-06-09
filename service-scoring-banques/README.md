# Service scoring interne banques (simulation)

Simule les API de scoring **El Amen** et **El Baraka** (formules différentes, critères **non exposés**) et l’orchestration de **routage**.

## Principes

- **UIB** : toujours routée par défaut (première entrée de `banquesRoutees`).
- **Partenaires** : ajoutés seulement si leur API interne répond `accepte: true`.
- **Score et critères** : calculés en interne, **jamais** renvoyés dans les réponses HTTP.
- **Pas** de banque recommandée, pas de message, pas de détail par critère.

## Grille interne (non affichée)

| Critère | Poids |
|---------|-------|
| Revenus et capacité de remboursement | 35 % |
| Taux d'endettement | 30 % |
| Stabilité professionnelle | 20 % |
| Historique bancaire | 15 % |

## Lancer

```bash
cd service-scoring-banques
pip install -r requirements.txt
python run.py
```

Port **8092**.

## Réponses API

### `GET /banques/el-amen/score-interne`

```json
{ "accepte": true }
```

### `GET /banques/el-baraka/score-interne`

```json
{ "accepte": false }
```

### `GET` ou `POST /routage/evaluer`

```json
{
  "banquesRoutees": ["UIB", "EL_BARAKA", "EL_AMEN"]
}
```

UIB est toujours présent ; les codes partenaires dépendent des réponses `accepte` internes.

## Paramètres (query ou JSON body)

`revenu_mensuel_net`, `charges_mensuelles_totales`, `montant_demande`, `duree_mois`, `anciennete_emploi_mois`, `type_contrat`, `nb_incidents_paiement`, `score_centrale_risque`

## Tests

```bash
pytest tests/ -q
```
