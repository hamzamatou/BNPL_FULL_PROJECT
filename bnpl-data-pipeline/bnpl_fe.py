"""Feature engineering BNPL partage entre train_GBMlight, test_rest_dataset, predict_manual (via test_rest)."""

from __future__ import annotations

import numpy as np
import pandas as pd


def feature_engineering_bnpl(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    out["mensualite_bnpl"] = out["montant_demande"] / (out["nbr_mois_remboursement"] + 1)
    out["reste_a_vivre"] = out["revenu_mensuel_net"] - out["charges_mensuelles_totales"]
    out["taux_effort"] = out["charges_mensuelles_totales"] / (out["revenu_mensuel_net"] + 1)
    out["ratio_bnpl_reste"] = out["mensualite_bnpl"] / (out["reste_a_vivre"] + 1)
    out["taux_endettement_global"] = (
        out["charges_mensuelles_totales"] + out["mensualite_bnpl"]
    ) / (out["revenu_mensuel_net"] + 1)
    out["buffer_financier"] = out["reste_a_vivre"] - out["mensualite_bnpl"]
    out["capacite_nette"] = (
        out["revenu_mensuel_net"]
        - out["charges_mensuelles_totales"]
        - out["mensualite_bnpl"]
    )
    out["ratio_mensualite_revenu"] = out["mensualite_bnpl"] / (out["revenu_mensuel_net"] + 1)
    out["charge_totale_ratio"] = out["charges_mensuelles_totales"] / (out["revenu_mensuel_net"] + 1)
    out["stress_financier"] = out["taux_endettement_global"] * out["anciennete_emploi_mois"]
    out["log_revenu"] = np.log1p(out["revenu_mensuel_net"])
    out["log_montant"] = np.log1p(out["montant_demande"])

    # --- Signaux explicites de risque (PD plus elevee sur cas tendus / CDD recent) ---
    _tc = out["type_contrat"].astype(str).str.strip().str.upper()
    is_cdd = _tc.eq("CDD")
    r = out["revenu_mensuel_net"].astype(np.float64)
    c = out["charges_mensuelles_totales"].astype(np.float64)
    anc = out["anciennete_emploi_mois"].clip(lower=0).astype(np.float64)
    te = out["taux_endettement_global"].astype(np.float64)

    out["flag_charges_ge_revenu"] = (c >= r).astype(np.float64)
    out["flag_buffer_negatif"] = (out["buffer_financier"] <= 0).astype(np.float64)
    out["flag_capacite_negatif"] = (out["capacite_nette"] <= 0).astype(np.float64)
    out["flag_cdd_anciennete_faible"] = (is_cdd & (anc < 12)).astype(np.float64)
    out["flag_cdd_moins_6_mois"] = (is_cdd & (anc < 6)).astype(np.float64)
    out["pression_endettement_jeune_anciennete"] = te / np.sqrt(anc + 1.0)
    out["interaction_cdd_taux_endettement"] = is_cdd.astype(np.float64) * te
    out["cdd_tenure_inverse_risque"] = is_cdd.astype(np.float64) * te / (anc + 1.0)
    out["score_risque_combine"] = (
        5.0 * out["flag_charges_ge_revenu"]
        + 4.0 * out["flag_buffer_negatif"]
        + 12.0 * out["flag_cdd_anciennete_faible"]
        + 3.0 * out["flag_capacite_negatif"]
        + 2.5 * out["pression_endettement_jeune_anciennete"]
        + 4.0 * out["interaction_cdd_taux_endettement"]
        + 6.0 * out["cdd_tenure_inverse_risque"]
        + 20.0 * out["flag_cdd_moins_6_mois"]
    )

    return out
