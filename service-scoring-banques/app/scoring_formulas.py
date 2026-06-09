"""Formules de sous-scores (0-100) — une variante par banque."""

import math

from app.models import CritereScore, DossierScoringInput, ScoringBanqueResult
from app.criteria import BANQUE_EL_AMEN, BANQUE_EL_BARAKA, CRITERES


def _clamp(value: float, low: float = 0.0, high: float = 100.0) -> float:
    return max(low, min(high, value))


def _contract_base(type_contrat: str) -> float:
    key = (type_contrat or "").strip().upper()
    return {"CDI": 88.0, "CDD": 58.0, "INTERIM": 38.0, "SAISONNIER": 42.0}.get(key, 48.0)


# --- EL AMEN : strict sur endettement et incidents, RAV linéaire ---


def _amen_revenus(d: DossierScoringInput) -> float:
    rav = d.reste_a_vivre
    if rav < 150:
        return 0.0
    return _clamp((rav - 150.0) / 7.5)


def _amen_endettement(d: DossierScoringInput) -> float:
    t = d.taux_endettement
    if t <= 0.33:
        return 100.0
    if t >= 0.55:
        return 0.0
    return _clamp(100.0 - ((t - 0.33) / 0.22) * 100.0)


def _amen_stabilite(d: DossierScoringInput) -> float:
    base = _contract_base(d.type_contrat)
    bonus = min(25.0, d.anciennete_emploi_mois * 0.65)
    if (d.type_contrat or "").upper() == "CDI":
        bonus += 8.0
    return _clamp(base + bonus)


def _amen_historique(d: DossierScoringInput) -> float:
    malus_incidents = 28.0 * min(d.nb_incidents_paiement, 4)
    malus_centrale = (100.0 - _clamp(d.score_centrale_risque)) * 0.55
    return _clamp(100.0 - malus_incidents - malus_centrale)


# --- EL BARAKA : plus tolérant endettement, favorise ancienneté, RAV log ---


def _baraka_revenus(d: DossierScoringInput) -> float:
    if d.revenu_mensuel_net <= 0:
        return 0.0
    ratio = d.reste_a_vivre / max(d.mensualite_estimee, 1.0)
    part_log = 22.0 * math.log1p(max(rav := d.reste_a_vivre, 0.0) / 250.0)
    part_ratio = _clamp(ratio * 12.0, 0.0, 45.0)
    return _clamp(35.0 + part_log + part_ratio)


def _baraka_endettement(d: DossierScoringInput) -> float:
    t = d.taux_endettement
    if t <= 0.38:
        return 100.0
    if t >= 0.62:
        return 0.0
    return _clamp(100.0 - ((t - 0.38) / 0.24) * 100.0)


def _baraka_stabilite(d: DossierScoringInput) -> float:
    base = _contract_base(d.type_contrat) * 0.92
    bonus = min(35.0, d.anciennete_emploi_mois * 1.05)
    return _clamp(base + bonus)


def _baraka_historique(d: DossierScoringInput) -> float:
    malus_incidents = 16.0 * min(d.nb_incidents_paiement, 5)
    malus_centrale = (100.0 - _clamp(d.score_centrale_risque)) * 0.35
    bonus_centrale = 5.0 if d.score_centrale_risque >= 70 else 0.0
    return _clamp(100.0 - malus_incidents - malus_centrale + bonus_centrale)


_FORMULES = {
    BANQUE_EL_AMEN: (
        _amen_revenus,
        _amen_endettement,
        _amen_stabilite,
        _amen_historique,
    ),
    BANQUE_EL_BARAKA: (
        _baraka_revenus,
        _baraka_endettement,
        _baraka_stabilite,
        _baraka_historique,
    ),
}

_SEUILS = {BANQUE_EL_AMEN: 58, BANQUE_EL_BARAKA: 52}

_LIBELLES = {
    BANQUE_EL_AMEN: "Banque El Amen",
    BANQUE_EL_BARAKA: "Banque El Baraka",
}


def calculer_score_banque(banque: str, dossier: DossierScoringInput) -> ScoringBanqueResult:
    if banque not in _FORMULES:
        raise ValueError(f"Banque inconnue: {banque}")

    fonctions = _FORMULES[banque]
    criteres_out: list[CritereScore] = []
    total = 0.0

    for (code, libelle, poids), fn in zip(CRITERES, fonctions):
        note = _clamp(fn(dossier))
        contribution = note * (poids / 100.0)
        total += contribution
        criteres_out.append(
            CritereScore(
                code=code,
                libelle=libelle,
                poids_pct=poids,
                note=round(note, 2),
                contribution=round(contribution, 2),
            )
        )

    score_int = int(round(total))
    seuil = _SEUILS[banque]

    return ScoringBanqueResult(
        banque=banque,
        banque_libelle=_LIBELLES[banque],
        score_interne=score_int,
        eligible=score_int >= seuil,
        seuil_eligibilite=seuil,
        criteres=tuple(criteres_out),
        indicateurs={
            "revenu_mensuel_net": dossier.revenu_mensuel_net,
            "charges_mensuelles_totales": dossier.charges_mensuelles_totales,
            "mensualite_estimee": round(dossier.mensualite_estimee, 2),
            "reste_a_vivre": round(dossier.reste_a_vivre, 2),
            "taux_endettement_pct": round(dossier.taux_endettement * 100, 2),
            "anciennete_emploi_mois": dossier.anciennete_emploi_mois,
            "type_contrat": dossier.type_contrat,
            "nb_incidents_paiement": dossier.nb_incidents_paiement,
            "score_centrale_risque": dossier.score_centrale_risque,
        },
    )


def reponse_api_banque(result: ScoringBanqueResult) -> dict:
    """Réponse minimale d'une API banque (score et critères restent internes)."""
    return {"accepte": result.eligible}
