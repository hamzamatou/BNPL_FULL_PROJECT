# Service Coherence OCR (US08)

Microservice Python Flask pour:
- OCR de documents (Tesseract)
- Extraction structurée via Ollama
- Contrôles de cohérence (règles métier V1)

## Endpoints

- `GET /health`
- `POST /coherence/check` (multipart/form-data)
  - `declared_data`: JSON string (ex: `{"nom":"...","cin":"...","montant":12000,"aUnLoyer":true}`)
  - Fichiers supportes: **images** (`jpg/jpeg/png/...`) et **PDF 1 page**.
    - PDF multi-pages => `400` (`Document invalide`, detail explicite).
  - Documents (meme logique que creation demande):
    - `cin` (obligatoire)
    - `fiche_paie_m1` (obligatoire)
    - `fiche_paie_m2` (obligatoire)
    - `fiche_paie_m3` (obligatoire)
    - `attestation_travail` (obligatoire)
    - `justificatif_loyer` (obligatoire si `aUnLoyer=true`)
    - `devis` (obligatoire si `montant > 10000`)

## Prérequis : Tesseract (OCR)

Sans **Tesseract**, l’appel `POST /coherence/check` renvoie **500** avec le message *tesseract is not installed or it's not in your PATH*.

1. **Installer Tesseract pour Windows** (ex. build [UB Mannheim](https://github.com/UB-Mannheim/tesseract/wiki) — installeur `.exe`).
2. Pendant l’installation, inclure les données de langue **français**, **arabe**, **anglais** si proposé (le service utilise `fra+ara+eng` dans le code OCR).
3. Soit cocher l’option qui ajoute Tesseract au **PATH**, soit renseigner dans **`.env`** le chemin vers l’exécutable. Sous Windows, utilisez plutôt des **/** pour éviter que `\t` dans le chemin soit interprété comme une tabulation :
   ```env
   TESSERACT_CMD=C:/Program Files/Tesseract-OCR/tesseract.exe
   ```
4. Fermer et rouvrir le terminal, puis **redémarrer** `python run.py` après toute modification du `.env`.

Vérification : dans un terminal, `tesseract --version` doit répondre (ou le même chemin que dans `TESSERACT_CMD`).

## Démarrage local

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
REM editer .env : OLLAMA_* et surtout TESSERACT_CMD si besoin
python run.py
```

## Exemple cURL

```bash
curl -X POST http://localhost:8090/coherence/check ^
  -F "declared_data={\"nom\":\"ALI\",\"prenom\":\"SAMI\",\"cin\":\"12345678\",\"revenu_mensuel\":\"2500\",\"montant\":12000,\"aUnLoyer\":true}" ^
  -F "cin=@C:\tmp\cin.jpg" ^
  -F "fiche_paie_m1=@C:\tmp\pay1.jpg" ^
  -F "fiche_paie_m2=@C:\tmp\pay2.jpg" ^
  -F "fiche_paie_m3=@C:\tmp\pay3.jpg" ^
  -F "attestation_travail=@C:\tmp\attestation.jpg" ^
  -F "justificatif_loyer=@C:\tmp\loyer.jpg" ^
  -F "devis=@C:\tmp\devis.jpg"
```

## Test terminal (Node >= 18)

Aucune dependance npm : `fetch` + `FormData` integres. Les JPG sont lus dans **`test-docs/`** (voir `test-docs/FICHIERS_REQUIS.txt`).

**Saisie au clavier** (nom, prénom, CIN, revenu, montant, puis loyer / devis selon les réponses) — **comportement par défaut** si vous ne passez pas de scénario :

```bash
cd service-coherence-ocr
node test_micro.mjs
npm run test:micro
```

Sous Windows (cmd ou PowerShell) :

```bat
run_test.cmd
```

**Scénarios préréglés** (données fixes, comme avant) :

```bash
node test_micro.mjs ok
node test_micro.mjs missing-doc
node test_micro.mjs no-loyer-no-devis
run_test.cmd ok
npm run test:micro:ok
```

Options : `--base-url URL`, `--docs-dir CHEMIN`, `--timeout SEC`, `--save fichier.json`, `--debug` (ajoute `?debug=true` et renvoie les details OCR/extraction), `-i` / `--interactive` pour forcer la saisie même si un argument parasite est présent.

Sans devis alors que le montant saisi est > 10000, le script **ramène le montant à 10000** pour respecter l’API (sinon le devis serait obligatoire).

**Générer des JPG de démo** (texte synthétique lisible par Tesseract ; loyer bilingue FR + arabe RTL) :

```bash
pip install -r requirements.txt
python generate_test_docs.py
```

Sinon, copiez vos scans dans `test-docs/` en respectant les noms du fichier `FICHIERS_REQUIS.txt`.

## Performance (`POST /coherence/check`)

Chaque document declenche au moins un **OCR** puis souvent un **appel Ollama** (sauf court-circuit CIN). Le temps total est surtout la **somme des appels LLM** sur une seule instance Ollama.

Le service applique deja quelques optimisations (voir `.env.example`) :

- **Un appel LLM par dossier** (`COHERENCE_LLM_BATCH`, defaut actif) : tous les textes OCR du dossier sont envoyes en **un seul** prompt Ollama ; en cas d’echec partiel ou JSON invalide, repli **document par document**. C’est le gain le plus important sur le temps d’une requete `/coherence/check`.
- **Regex avant LLM** (`COHERENCE_REGEX_FIRST`, defaut **true**) : heuristiques d’abord, **pas d’Ollama** sur un document si une valeur est trouvee. Mettez `false` pour **LLM d’abord** puis regex en secours. La CIN peut encore eviter le LLM via `COHERENCE_SKIP_LLM_FOR_CIN`.
- **OCR en parallele** sur les fichiers de la requete (`COHERENCE_OCR_WORKERS`, defaut 4) : plusieurs processus Tesseract peuvent tourner en meme temps.
- **CIN sans LLM** si 8 chiffres sont detectes dans le texte OCR (`COHERENCE_SKIP_LLM_FOR_CIN`, defaut actif).
- **Image redimensionnee** avant OCR si le scan est tres grand (`OCR_IMAGE_MAX_EDGE`, defaut 2200 px sur le plus grand cote ; `0` pour desactiver).
- **`OLLAMA_NUM_PREDICT`** : borne la longueur de generation (defaut 512), adapte au JSON court.
- **Modele** : defaut applicatif `llama3.2:3b` ; un modele plus lourd ameliore parfois la precision au prix de la latence. **GPU** et `ollama serve` deja demarre restent les leviers les plus importants hors code.
"# IA_BNPL"  
