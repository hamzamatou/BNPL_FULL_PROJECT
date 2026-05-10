"""
app/services/service_recommendation.py

Service de recommandations IA — Phase 2 BNPL.
Appelé par routes.py uniquement après validation de cohérence (Phase 1) réussie.

Responsabilités de ce fichier :
  1. Calculs métier purs (règle 40%, score solvabilité, montant max, durée min)
  2. Délégation de l'appel LLM à call_ollama_recommendation() (fichier ollama)
  3. Construction du RecommandationResult retourné au router

Règle métier (taux d'endettement BCT, 40 %) :
  (mensualites_credits_existants + mensualite_bnpl) / revenu_mensuel_net ≤ 40%

  Plafond de la nouvelle mensualité BNPL :
  plafond_bnpl = max(0, 0.40 × revenu_mensuel_net − mensualites_credits_existants)

  mensualite_bnpl = montant_financement / duree_mois  → doit être ≤ plafond_bnpl

  revenu_disponible (informatif API) :
  revenu_mensuel_net − mensualites_credits_existants
  (≠ base du plafond BCT ; le plafond utilise le revenu net entier.)
"""

from __future__ import annotations

import math
from dataclasses import dataclass, asdict
from typing import Optional

from app.services.ollama_service import call_ollama_recommendation   # ← fonction ajoutée dans ollama.py

TAUX_BNPL_MAX = 0.40


# ──────────────────────────────────────────────────────────────────────────────
# Schémas
# ──────────────────────────────────────────────────────────────────────────────

@dataclass
class DossierFinancier:
    """
    Données financières du dossier transmises par le router.
    charges_mensuelles_totales inclut déjà les enfants (300 TND/enfant/mois).
    """
    revenu_mensuel_net: float
    charges_mensuelles_totales: float       # loyer + enfants + autres charges fixes
    mensualites_credits_existants: float    # remboursements mensuels des crédits en cours
    encours_credits: float                  # capital restant dû
    anciennete_emploi_mois: int
    montant_financement: float
    duree_mois: int


@dataclass
class RecommandationResult:
    """Résultat sérialisable retourné au router."""
    conforme: bool
    mensualite_bnpl: float
    revenu_disponible: float  # revenu net − mensualités crédits existants (informatif)
    plafond_bnpl: float
    montant_max_acceptable: Optional[float]   # None si conforme
    duree_minimale_mois: Optional[int]        # None si conforme
    score_solvabilite: str
    evaluation: str
    recommandations: list[str]
    texte_complet: str                        # évaluation + recommandations formatées
    raw_llm_response: Optional[str]           # brut Ollama pour debug

    def to_dict(self) -> dict:
        return asdict(self)


# ──────────────────────────────────────────────────────────────────────────────
# Calculs métier (purs, sans I/O)
# ──────────────────────────────────────────────────────────────────────────────

def _mensualite_bnpl(montant: float, duree_mois: int) -> float:
    if duree_mois <= 0:
        raise ValueError("duree_mois doit être > 0")
    return round(montant / duree_mois, 3)


def _revenu_disponible(dossier: DossierFinancier) -> float:
    """Informatif : revenu après déduction des mensualités des crédits en cours uniquement."""
    return round(dossier.revenu_mensuel_net - dossier.mensualites_credits_existants, 3)


def _plafond_mensualite_bnpl_bct(revenu_mensuel_net: float, mensualites_credits_existants: float) -> float:
    """
    Mensualité BNPL maximale autorisée (règle BCT 40 %) :
    40 % × revenu_net − mensualités des crédits déjà engagées (plancher à 0).
    """
    return round(
        max(0.0, TAUX_BNPL_MAX * revenu_mensuel_net - mensualites_credits_existants),
        3,
    )


def _montant_max(plafond: float, duree_mois: int) -> float:
    """Montant maximal finançable = plafond_bnpl × duree_mois"""
    if plafond <= 0:
        return 0.0
    return round(plafond * duree_mois, 3)


def _duree_min(montant: float, plafond: float) -> int:
    """Durée minimale pour que le montant demandé soit conforme."""
    if plafond <= 0:
        return 0
    return math.ceil(montant / plafond)


def _score_solvabilite(dossier: DossierFinancier, mensualite: float) -> str:
    """
    Score qualitatif basé sur le taux de charge global après ajout du BNPL.
    taux = (charges_totales + crédits existants + mensualité BNPL) / revenu_net
    """
    total = (
        dossier.charges_mensuelles_totales
        + dossier.mensualites_credits_existants
        + mensualite
    )
    taux = total / dossier.revenu_mensuel_net if dossier.revenu_mensuel_net > 0 else 1.0

    if taux < 0.30 and dossier.anciennete_emploi_mois >= 24:
        return "Excellent"
    if taux < 0.40 and dossier.anciennete_emploi_mois >= 12:
        return "Bon"
    if taux < 0.55:
        return "Moyen"
    return "Faible"


# ──────────────────────────────────────────────────────────────────────────────
# Fallback texte (si Ollama échoue ou retourne JSON invalide)
# ──────────────────────────────────────────────────────────────────────────────

def _fallback_texte(
    mensualite: float,
    plafond: float,
    conforme: bool,
    montant_max: Optional[float],
    duree_mois: int,
    duree_min: Optional[int],
    montant_financement: float,
    score: str,
) -> tuple[str, list[str]]:
    """Retourne (evaluation, recommandations) sans LLM."""
    if conforme:
        evaluation = (
            f"Dossier conforme : mensualité BNPL {mensualite} TND "
            f"≤ plafond {plafond} TND. Score : {score}."
        )
        recommandations = [
            "Vérifiez que toutes les pièces justificatives sont jointes et lisibles.",
            "Assurez-vous que les 3 fiches de paie couvrent les 3 derniers mois consécutifs.",
        ]
    else:
        evaluation = (
            f"Dossier NON conforme : mensualité BNPL {mensualite} TND "
            f"> plafond autorisé {plafond} TND."
        )
        opt2 = (
            f"Option 2 : allonger la durée à {duree_min} mois pour conserver {montant_financement} TND."
            if (duree_min is not None and duree_min > 0)
            else "Option 2 : le plafond mensuel BCT est atteint ou dépassé par les crédits existants — "
            "réduire le montant ou les mensualités de crédits en cours avant d'ajouter un BNPL."
        )
        recommandations = [
            f"Option 1 : réduire le montant demandé à {montant_max} TND sur {duree_mois} mois.",
            opt2,
        ]
    return evaluation, recommandations


def _build_texte_complet(evaluation: str, recommandations: list[str]) -> str:
    lignes = "\n".join(f"• {r}" for r in recommandations if r.strip())
    return f"{evaluation}\n\n{lignes}".strip()


# ──────────────────────────────────────────────────────────────────────────────
# Point d'entrée — appelé par routes.py
# ──────────────────────────────────────────────────────────────────────────────

def generer_recommandations(dossier: DossierFinancier) -> RecommandationResult:
    """
    Génère les recommandations IA pour un dossier cohérent.
    À appeler UNIQUEMENT après validation Phase 1 (cohérence) réussie.

    Usage dans routes.py :
        from app.services.service_recommendation import generer_recommandations, DossierFinancier

        @router.post("/recommandations/{demande_id}")
        def recommandations(demande_id: str, payload: RecommandationRequest):
            dossier = DossierFinancier(**payload.dict())
            result  = generer_recommandations(dossier)
            return result.to_dict()
    """
    # ── 1. Calculs métier ──────────────────────────────────────────────────────
    mensualite     = _mensualite_bnpl(dossier.montant_financement, dossier.duree_mois)
    revenu_dispo   = _revenu_disponible(dossier)
    plafond        = _plafond_mensualite_bnpl_bct(
        dossier.revenu_mensuel_net,
        dossier.mensualites_credits_existants,
    )
    conforme       = mensualite <= plafond
    mnt_max        = None if conforme else _montant_max(plafond, dossier.duree_mois)
    duree_min_val: Optional[int] = None
    if not conforme and plafond > 0:
        duree_min_val = _duree_min(dossier.montant_financement, plafond)
    score          = _score_solvabilite(dossier, mensualite)

    # ── 2. Appel Ollama ────────────────────────────────────────────────────────
    raw_response: Optional[str] = None
    evaluation    = ""
    recommandations: list[str] = []

    try:
        result = call_ollama_recommendation(
            revenu_mensuel_net=dossier.revenu_mensuel_net,
            charges_mensuelles_totales=dossier.charges_mensuelles_totales,
            mensualites_credits_existants=dossier.mensualites_credits_existants,
            encours_credits=dossier.encours_credits,
            anciennete_emploi_mois=dossier.anciennete_emploi_mois,
            montant_financement=dossier.montant_financement,
            duree_mois=dossier.duree_mois,
            revenu_disponible=revenu_dispo,
            plafond_bnpl=plafond,
            mensualite_bnpl=mensualite,
            conforme=conforme,
            montant_max=mnt_max,
            duree_min=duree_min_val,
            score_solvabilite=score,
        )
        raw_response = result.get("raw")
        data         = result.get("parsed", {})

        evaluation      = (data.get("evaluation") or "").strip()
        recommandations = [
            r for r in (data.get("recommandations") or [])
            if isinstance(r, str) and r.strip()
        ]

    except Exception:
        pass   # on tombe sur le fallback ci-dessous

    # ── 3. Fallback si LLM vide ou en erreur ──────────────────────────────────
    if not evaluation or not recommandations:
        evaluation, recommandations = _fallback_texte(
            mensualite, plafond, conforme, mnt_max,
            dossier.duree_mois, duree_min_val,
            dossier.montant_financement, score,
        )

    return RecommandationResult(
        conforme=conforme,
        mensualite_bnpl=mensualite,
        revenu_disponible=revenu_dispo,
        plafond_bnpl=plafond,
        montant_max_acceptable=mnt_max,
        duree_minimale_mois=duree_min_val if not conforme else None,
        score_solvabilite=score,
        evaluation=evaluation,
        recommandations=recommandations,
        texte_complet=_build_texte_complet(evaluation, recommandations),
        raw_llm_response=raw_response,
    )
