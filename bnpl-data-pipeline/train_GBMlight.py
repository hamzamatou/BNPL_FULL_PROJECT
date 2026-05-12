import pandas as pd
import numpy as np
import lightgbm as lgb
import joblib

from sklearn.model_selection import train_test_split
from sklearn.metrics import roc_auc_score, classification_report, precision_recall_curve
from sklearn.calibration import CalibratedClassifierCV
from sklearn.preprocessing import LabelEncoder

from bnpl_fe import feature_engineering_bnpl


def apply_feature_engineering(base_df: pd.DataFrame) -> pd.DataFrame:
    """Meme pipeline que l'entrainement (module bnpl_fe)."""
    return feature_engineering_bnpl(base_df)


def interpret_risk_demo(proba_defaut: float, threshold: float) -> tuple[str, str, str]:
    """Interpretation prudente (evite « solide » pour des P(defaut) > ~15%)."""
    p, t = proba_defaut, max(threshold, 1e-9)
    if p < 0.08:
        qual = "Risque estime faible", "P(defaut) sous 8%"
    elif p < 0.15:
        qual = "Risque estime modere", "Entre 8% et 15%"
    elif p < 0.25:
        qual = "Risque estime notable", "Entre 15% et 25%"
    elif p < 0.40:
        qual = "Risque estime eleve", "Entre 25% et 40%"
    else:
        qual = "Risque estime tres eleve", "Au-dela de 40%"
    if p >= t:
        vs = f"Au-dessus du seuil operationnel ({t:.1%})"
    elif p >= 0.95 * t:
        vs = "Tres proche du seuil operationnel (marge faible)"
    elif p >= 0.70 * t:
        vs = "Sous le seuil avec marge moderee"
    else:
        vs = "Nettement sous le seuil operationnel"
    return qual[0], qual[1], vs

# =========================
# 1. LOAD DATA
# =========================

DATA_PATH = r"C:\Users\ASUS\Desktop\uib-bnpl\bnpl-data-pipeline\out\dataset_bnpl_tunisien_merged.csv"
df = pd.read_csv(DATA_PATH)

print("Dataset:", df.shape)

# =========================
# 2. TYPE CLEANING
# =========================

for col in df.columns:
    if df[col].dtype == "object" and col != "TARGET":
        try:
            df[col] = pd.to_numeric(df[col])
        except:
            pass

# =========================
# 3. FEATURE ENGINEERING
# =========================

df = feature_engineering_bnpl(df)

# =========================
# 4. OUTLIER CLIPPING SAFE
# =========================

num_cols = df.select_dtypes(include=[np.number]).columns

lower = df[num_cols].quantile(0.01)
upper = df[num_cols].quantile(0.99)

df[num_cols] = df[num_cols].clip(lower=lower, upper=upper, axis=1)

# Sauvegarde pour eval hors train (meme clip que l'entrainement)
clip_lower = lower.to_dict()
clip_upper = upper.to_dict()
num_cols_clip = list(num_cols)

# =========================
# 5. ENCODING CATEGORICAL
# =========================

cat_cols = df.select_dtypes(include=["object", "string"]).columns

encoders: dict[str, LabelEncoder] = {}
for col in cat_cols:
    if col != "TARGET":
        le = LabelEncoder()
        df[col] = le.fit_transform(df[col].astype(str))
        encoders[col] = le

# =========================
# 6. FEATURES
# =========================

# Eviter redondance brute+log: on garde log_montant (plus stable) et on retire montant_demande brut
if "montant_demande" in df.columns:
    df = df.drop(columns=["montant_demande"])

features = [col for col in df.columns if col != "TARGET"]

X = df[features]
y = df["TARGET"]

# =========================
# 7. SPLIT DATA
# =========================

X_train, X_temp, y_train, y_temp = train_test_split(
    X, y, test_size=0.3, stratify=y, random_state=42
)

X_val, X_test, y_val, y_test = train_test_split(
    X_temp, y_temp, test_size=0.5, stratify=y_temp, random_state=42
)

# =========================
# 8. LIGHTGBM MODEL
# =========================

scale_pos_weight = y_train.value_counts()[0] / y_train.value_counts()[1]

model = lgb.LGBMClassifier(
    n_estimators=2500,
    learning_rate=0.02,
    num_leaves=63,
    subsample=0.8,
    colsample_bytree=0.8,
    reg_alpha=0.1,
    reg_lambda=0.1,
    scale_pos_weight=scale_pos_weight,
    random_state=42
)

# ---------- Contraintes monotones (legeres) ----------
# Trop de contraintes + nouvelles features = arbres quasi bloques (meilleure iteration ~2).
# On ne garde que des signes tres robustes; le reste est laisse libre.
feature_names = list(X_train.columns)
constraint_map = {f: 0 for f in feature_names}
constraint_map["taux_endettement_global"] = 1
constraint_map["reste_a_vivre"] = -1
constraint_map["buffer_financier"] = -1
constraint_map["capacite_nette"] = -1
constraint_map["mensualite_bnpl"] = 1
constraint_map["score_risque_combine"] = 1
constraint_map["cdd_tenure_inverse_risque"] = 1
constraint_map["flag_cdd_moins_6_mois"] = 1
monotone_constraints = [constraint_map[f] for f in feature_names]

model.set_params(
    monotone_constraints=monotone_constraints,
)

model.fit(
    X_train, y_train,
    eval_set=[(X_val, y_val)],
    eval_metric="auc",
    callbacks=[lgb.early_stopping(100)]
)

# =========================
# 9. CALIBRATION (FIX SKLEARN ERROR)
# =========================

calibrated_model = CalibratedClassifierCV(
    estimator=model,
    method="isotonic",
    cv=3,
)

calibrated_model.fit(X_train, y_train)

# =========================
# 10. EVALUATION
# =========================

proba_val = calibrated_model.predict_proba(X_val)[:, 1]

auc = roc_auc_score(y_val, proba_val)
print("\nAUC:", auc)

# =========================
# 11. OPTIMAL THRESHOLD (PR CURVE)
# =========================

precision, recall, thresholds = precision_recall_curve(y_val, proba_val)

f1 = 2 * (precision * recall) / (precision + recall + 1e-9)
best_idx = np.argmax(f1)

best_threshold = thresholds[max(best_idx - 1, 0)]

print("\nBest threshold:", best_threshold)

# =========================
# 12. FINAL TEST
# =========================

proba_test = calibrated_model.predict_proba(X_test)[:, 1]
final_pred = (proba_test >= best_threshold).astype(int)

print("\nFINAL REPORT:")
print(classification_report(y_test, final_pred))

# =========================
# 12bis. EXEMPLES EXPLICITES
# =========================
print("\n" + "=" * 55)
print("EXEMPLES DE TEST (INFERENCE)")
print("=" * 55)

demos = [
    {
        "profil": "CDI stable - 2200 DT",
        "revenu_mensuel_net": 2200.0,
        "revenu_annuel": 26400.0,
        "charges_mensuelles_totales": 750.0,
        "montant_demande": 3000.0,
        "nbr_mois_remboursement": 12.0,
        "anciennete_emploi_mois": 48.0,
        "type_contrat": "CDI",
    },
    {
        "profil": "CDD charge elevee - 900 DT",
        "revenu_mensuel_net": 900.0,
        "revenu_annuel": 10800.0,
        "charges_mensuelles_totales": 620.0,
        "montant_demande": 2500.0,
        "nbr_mois_remboursement": 6.0,
        "anciennete_emploi_mois": 8.0,
        "type_contrat": "CDD",
    },
    {
        "profil": "CDI moyen - 1500 DT",
        "revenu_mensuel_net": 1500.0,
        "revenu_annuel": 18000.0,
        "charges_mensuelles_totales": 550.0,
        "montant_demande": 2000.0,
        "nbr_mois_remboursement": 12.0,
        "anciennete_emploi_mois": 36.0,
        "type_contrat": "CDI",
    },
]

for d in demos:
    profil = d["profil"]
    row = pd.DataFrame([{k: v for k, v in d.items() if k != "profil"}])
    row = apply_feature_engineering(row)

    for col in cat_cols:
        if col in row.columns and col != "TARGET" and col in encoders:
            le = encoders[col]
            val = str(row.at[row.index[0], col])
            # Valeur hors vocabulaire d'entraînement: fallback sur la classe la plus fréquente (index 0)
            if val not in le.classes_:
                row[col] = int(0)
            else:
                row[col] = int(le.transform([val])[0])

    row = row.reindex(columns=features, fill_value=0.0)
    p_defaut = float(calibrated_model.predict_proba(row)[0, 1])
    q1, q2, vs = interpret_risk_demo(p_defaut, best_threshold)

    print(f"\n  {profil}")
    print(f"    P(defaut)         : {p_defaut:.2%}")
    print(f"    Niveau qualitatif : {q1} - {q2}")
    print(f"    Lecture vs seuil  : {vs}")

# =========================
# 13. FEATURE IMPORTANCE
# =========================

imp = pd.DataFrame({
    "feature": features,
    "importance": model.feature_importances_
}).sort_values(by="importance", ascending=False)

print("\nTOP FEATURES:")
print(imp.head(15))

print("\nFOCUS FEATURES (metier):")
focus = ["buffer_financier", "stress_financier", "reste_a_vivre", "mensualite_bnpl", "nbr_mois_remboursement"]
print(
    imp[imp["feature"].isin(focus)]
    .set_index("feature")
    .reindex(focus)
    .fillna(0)
)

# =========================
# 14. SAVE MODEL
# =========================

joblib.dump({
    "model": calibrated_model,
    "threshold": best_threshold,
    "features": features,
    "monotone_constraints": dict(zip(feature_names, monotone_constraints)),
    "focus_features": [
        "buffer_financier",
        "stress_financier",
        "pression_endettement_jeune_anciennete",
        "score_risque_combine",
        "cdd_tenure_inverse_risque",
        "flag_cdd_anciennete_faible",
        "interaction_cdd_taux_endettement",
        "reste_a_vivre",
        "mensualite_bnpl",
        "nbr_mois_remboursement",
    ],
    "encoders": encoders,
    "clip_lower": clip_lower,
    "clip_upper": clip_upper,
    "num_cols_clip": num_cols_clip,
    "cat_cols": list(cat_cols),
    "split": {"test_size_outer": 0.3, "test_size_inner": 0.5, "random_state": 42},
}, "bnpl_model_production.pkl")

print("\nMODEL SAVED OK")