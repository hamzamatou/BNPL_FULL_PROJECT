-- Données de démo — tableau de bord analyste (base uib_reporting_archivage)
-- Remplacer 5 par l'id JWT de l'analyste (table users dans uib_gestion_utilisateur)

INSERT INTO decision_financement_historique (
    demande_id, reference_demande, type_decision, libelle,
    acteur_user_id, acteur_email, acteur_role,
    date_decision, date_enregistrement
) VALUES
    (101, 'DEM-2026-101', 'PRISE_EN_CHARGE', 'Prise en charge du dossier',
     5, 'analyste.uib1@bnpl.local', 'ANALYSTE_BANCAIRE', NOW() - INTERVAL '2 days', NOW()),
    (102, 'DEM-2026-102', 'ACCEPTEE', 'Financement accepté',
     5, 'analyste.uib1@bnpl.local', 'ANALYSTE_BANCAIRE', NOW() - INTERVAL '1 day', NOW()),
    (103, 'DEM-2026-103', 'REFUSEE', 'Dossier refusé',
     5, 'analyste.uib1@bnpl.local', 'ANALYSTE_BANCAIRE', NOW() - INTERVAL '5 hours', NOW()),
    (104, 'DEM-2026-104', 'DEMANDE_COMPLEMENTS', 'Compléments demandés',
     5, 'analyste.uib1@bnpl.local', 'ANALYSTE_BANCAIRE', NOW() - INTERVAL '1 hour', NOW());

SELECT type_decision, COUNT(*) AS nb
FROM decision_financement_historique
WHERE acteur_user_id = 5
GROUP BY type_decision;
