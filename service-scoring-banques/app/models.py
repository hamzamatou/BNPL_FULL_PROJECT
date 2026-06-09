from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class DossierScoringInput:
    revenu_mensuel_net: float
    charges_mensuelles_totales: float
    montant_demande: float
    duree_mois: int
    anciennete_emploi_mois: int
    type_contrat: str
    nb_incidents_paiement: int = 0
    score_centrale_risque: float = 50.0

    @property
    def mensualite_estimee(self) -> float:
        if self.duree_mois <= 0:
            return self.montant_demande
        return self.montant_demande / self.duree_mois

    @property
    def reste_a_vivre(self) -> float:
        return self.revenu_mensuel_net - self.charges_mensuelles_totales - self.mensualite_estimee

    @property
    def taux_endettement(self) -> float:
        if self.revenu_mensuel_net <= 0:
            return 1.0
        return (self.charges_mensuelles_totales + self.mensualite_estimee) / self.revenu_mensuel_net


@dataclass(frozen=True)
class CritereScore:
    code: str
    libelle: str
    poids_pct: float
    note: float
    contribution: float


@dataclass(frozen=True)
class ScoringBanqueResult:
    banque: str
    banque_libelle: str
    score_interne: int
    eligible: bool
    seuil_eligibilite: int
    criteres: tuple[CritereScore, ...]
    indicateurs: dict[str, Any]
