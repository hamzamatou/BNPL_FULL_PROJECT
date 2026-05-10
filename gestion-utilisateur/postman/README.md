# Tests Postman (UIB BNPL)

## Import

1. Ouvrir Postman → **Import**
2. Fichiers :
   - `UIB-BNPL-gestion-utilisateur.postman_collection.json`
   - `UIB-BNPL-local.postman_environment.json`
3. En haut à droite : sélectionner l’environnement **UIB BNPL — local**

## Ordre conseillé

1. Démarrer **PostgreSQL**, puis **gestion-utilisateur** (`:8080`), puis **gestion-demande** (`:8081`) si besoin.
2. **Login admin** — le test enregistre automatiquement `token` dans l’environnement.
3. **Liste des utilisateurs** — vérifie le JWT `ADMIN`.
4. **Register — nouveau commerçant** — une fois (adapter email / ICE si doublon).
5. **Login commerçant** — met à jour `token` avec le rôle commerçant.
6. **POST /api/clients** — puis mettre l’`id` retourné dans la variable d’environnement **`client_id`**.
7. **GET identité client (internal)** — uniquement header `X-Internal-Api-Key` (pas de Bearer).

## Variables d’environnement

| Variable | Rôle |
|----------|------|
| `base_url_utilisateur` | `http://localhost:8080` |
| `base_url_demande` | `http://localhost:8081` |
| `token` | Rempli après login (script) |
| `internal_api_key` | Aligné sur `internal.api.key` (properties) |
| `client_id` | Id client après création |

## Admin par défaut (bootstrap)

Si `app.admin.bootstrap.*` n’a pas été modifié :

- Email : `admin@uib.bnpl`
- Mot de passe : `ChangeMeAdmin2026!`
