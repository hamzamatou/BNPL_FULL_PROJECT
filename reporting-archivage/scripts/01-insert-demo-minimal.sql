-- INSERT minimal — exécuter TOUT le script (F5) sur uib_reporting_archivage
-- Si erreur "relation n'existe pas" → voir 00-diagnostic.sql

BEGIN;

INSERT INTO action_demande_historique (
    demande_id, reference_demande, type_action, libelle,
    statut_avant, statut_apres, acteur_email, acteur_role,
    date_action, date_enregistrement
) VALUES (
    101, 'DEM-2026-001', 'CREATION', 'Test création demande',
    '—', 'BROUILLON', 'admin@uib.bnpl', 'ADMIN',
    NOW(), NOW()
);

INSERT INTO action_document_historique (
    demande_id, reference_demande, type_action, libelle,
    acteur_email, date_action, date_enregistrement
) VALUES (
    101, 'DEM-2026-001', 'UPLOAD', 'Test upload document',
    'admin@uib.bnpl', NOW(), NOW()
);

INSERT INTO acces_plateforme_historique (
    user_id, user_email, user_role, type_acces, description,
    adresse_ip, suspect, date_acces, date_enregistrement
) VALUES (
    1, 'admin@uib.bnpl', 'ADMIN', 'CONNEXION', 'Test connexion',
    '127.0.0.1', false, NOW(), NOW()
);

INSERT INTO decision_financement_historique (
    demande_id, reference_demande, type_decision, libelle,
    acteur_email, acteur_role, date_decision, date_enregistrement
) VALUES (
    101, 'DEM-2026-001', 'ACCEPTEE', 'Test décision acceptée',
    'admin@uib.bnpl', 'ADMIN', NOW(), NOW()
);

INSERT INTO dossier_archive (
    demande_id, reference_demande, statut_final, snapshot_json,
    date_cloture, date_archivage, date_enregistrement
) VALUES (
    99, 'DEM-TEST-099', 'ACCEPTEE', '{"test":true}',
    NOW(), NOW(), NOW()
);

COMMIT;

SELECT 'action_demande' AS t, COUNT(*) FROM action_demande_historique
UNION ALL SELECT 'action_document', COUNT(*) FROM action_document_historique
UNION ALL SELECT 'acces', COUNT(*) FROM acces_plateforme_historique
UNION ALL SELECT 'decisions', COUNT(*) FROM decision_financement_historique
UNION ALL SELECT 'archives', COUNT(*) FROM dossier_archive;
