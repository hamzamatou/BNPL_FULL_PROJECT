"""Grille commune des critères de scoring interne (poids métier BNPL)."""

CRITERES = (
    ("revenus_capacite", "Revenus et capacité de remboursement", 35.0),
    ("taux_endettement", "Taux d'endettement", 30.0),
    ("stabilite_pro", "Stabilité professionnelle", 20.0),
    ("historique_bancaire", "Historique bancaire et incidents", 15.0),
)

BANQUE_UIB = "UIB"
BANQUE_EL_AMEN = "EL_AMEN"
BANQUE_EL_BARAKA = "EL_BARAKA"
