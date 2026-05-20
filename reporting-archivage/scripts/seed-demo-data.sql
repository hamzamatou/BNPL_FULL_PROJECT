-- =============================================================================
-- Données de démo pour l'écran Angular « Reporting & pilotage »
-- À exécuter dans pgAdmin sur la base uib_reporting_archivage
-- (après fix-grants-existing-db.sql + redémarrage reporting-archivage)
-- =============================================================================

-- Vérifier que les tables existent
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
    'action_demande_historique',
    'action_document_historique',
    'acces_plateforme_historique',
    'decision_financement_historique',
    'dossier_archive'
  )
ORDER BY 1;

-- Optionnel : repartir de zéro
-- TRUNCATE action_demande_historique, action_document_historique,
--   acces_plateforme_historique, decision_financement_historique, dossier_archive
--   RESTART IDENTITY CASCADE;

-- -----------------------------------------------------------------------------
-- Actions sur demandes (onglet + dashboard)
-- -----------------------------------------------------------------------------
INSERT INTO action_demande_historique (
    demande_id, reference_demande, type_action, libelle,
    statut_avant, statut_apres, acteur_user_id, acteur_email, acteur_role,
    date_action, date_enregistrement
) VALUES
(101, 'DEM-2026-001', 'CREATION', 'Création de la demande BNPL',
 '—', 'BROUILLON', 10, 'client@uib.bnpl', 'CLIENT',
 NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),
(101, 'DEM-2026-001', 'SCORING', 'Scoring IA exécuté',
 'BROUILLON', 'EN_ANALYSE', NULL, 'system@uib.bnpl', 'SYSTEM',
 NOW() - INTERVAL '90 minutes', NOW() - INTERVAL '90 minutes'),
(102, 'DEM-2026-002', 'PRISE_EN_CHARGE', 'Prise en charge par l''analyste',
 'EN_ANALYSE', 'EN_COURS', 2, 'analyste@uib.bnpl', 'ANALYSTE_BANCAIRE',
 NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour'),
(102, 'DEM-2026-002', 'ACCEPTION', 'Demande acceptée',
 'EN_COURS', 'ACCEPTEE', 2, 'analyste@uib.bnpl', 'ANALYSTE_BANCAIRE',
 NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes'),
(103, 'DEM-2026-003', 'REFUS', 'Refus pour dossier incomplet',
 'EN_COURS', 'REFUSEE', 1, 'admin@uib.bnpl', 'ADMIN',
 NOW() - INTERVAL '15 minutes', NOW() - INTERVAL '15 minutes');

-- -----------------------------------------------------------------------------
-- Actions documents
-- -----------------------------------------------------------------------------
INSERT INTO action_document_historique (
    demande_id, reference_demande, document_id, object_key, type_document,
    type_action, libelle, acteur_user_id, acteur_email, acteur_role,
    date_action, date_enregistrement
) VALUES
(101, 'DEM-2026-001', 501, 'demandes/101/cin.pdf', 'CIN',
 'UPLOAD', 'Upload CIN client', 10, 'client@uib.bnpl', 'CLIENT',
 NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
(101, 'DEM-2026-001', 502, 'demandes/101/bulletin.pdf', 'BULLETIN_SALAIRE',
 'VERIFICATION_OCR', 'Vérification OCR bulletin', NULL, 'system@uib.bnpl', 'SYSTEM',
 NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),
(102, 'DEM-2026-002', 503, 'demandes/102/contrat.pdf', 'CONTRAT',
 'CONSULTATION', 'Consultation contrat par analyste', 2, 'analyste@uib.bnpl', 'ANALYSTE_BANCAIRE',
 NOW() - INTERVAL '45 minutes', NOW() - INTERVAL '45 minutes');

-- -----------------------------------------------------------------------------
-- Accès plateforme (1 suspect pour le compteur dashboard)
-- -----------------------------------------------------------------------------
INSERT INTO acces_plateforme_historique (
    user_id, user_email, user_role, type_acces, description,
    adresse_ip, endpoint, methode_http, suspect,
    date_acces, date_enregistrement
) VALUES
(1, 'admin@uib.bnpl', 'ADMIN', 'CONNEXION', 'Connexion réussie OTP',
 '192.168.1.10', '/api/auth/verify-otp', 'POST', false,
 NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours'),
(10, 'client@uib.bnpl', 'CLIENT', 'APPEL_API', 'Consultation statut demande',
 '192.168.1.55', '/api/demandes/101', 'GET', false,
 NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour'),
(NULL, 'unknown@evil.test', 'UNKNOWN', 'ECHEC_AUTH', 'Tentatives OTP invalides',
 '203.0.113.99', '/api/auth/verify-otp', 'POST', true,
 NOW() - INTERVAL '20 minutes', NOW() - INTERVAL '20 minutes');

-- -----------------------------------------------------------------------------
-- Décisions financement
-- -----------------------------------------------------------------------------
INSERT INTO decision_financement_historique (
    demande_id, reference_demande, type_decision, libelle,
    acteur_user_id, acteur_email, acteur_role, etape_workflow,
    date_decision, date_enregistrement
) VALUES
(101, 'DEM-2026-001', 'ROUTAGE', 'Routage vers file analyste',
 NULL, 'camunda@uib.bnpl', 'SYSTEM', 'Task_Routage',
 NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
(102, 'DEM-2026-002', 'ACCEPTEE', 'Financement accepté — 5000 TND / 12 mois',
 2, 'analyste@uib.bnpl', 'ANALYSTE_BANCAIRE', 'Task_Decision',
 NOW() - INTERVAL '25 minutes', NOW() - INTERVAL '25 minutes'),
(103, 'DEM-2026-003', 'REFUSEE', 'Refus — pièces manquantes',
 1, 'admin@uib.bnpl', 'ADMIN', 'Task_Decision',
 NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '10 minutes');

-- -----------------------------------------------------------------------------
-- Archives (onglet Archives)
-- -----------------------------------------------------------------------------
INSERT INTO dossier_archive (
    demande_id, reference_demande, client_id, cin_client, statut_final,
    montant, duree_mois, type_produit, snapshot_json, documents_metadata_json,
    archive_par_user_id, archive_par_email,
    date_cloture, date_archivage, date_enregistrement
) VALUES
(99, 'DEM-2025-099', 50, '12345678', 'ACCEPTEE',
 3500.00, 6, 'BNPL_AUTO',
 '{"demandeId":99,"client":"Dupont","statut":"ACCEPTEE"}',
 '[{"type":"CIN","objectKey":"archives/99/cin.pdf"}]',
 1, 'admin@uib.bnpl',
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
(98, 'DEM-2025-098', 51, '87654321', 'REFUSEE',
 2000.00, 3, 'BNPL_RETAIL',
 '{"demandeId":98,"client":"Martin","statut":"REFUSEE"}',
 '[]',
 2, 'analyste@uib.bnpl',
 NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

-- Contrôle rapide
SELECT 'action_demande' AS t, COUNT(*) FROM action_demande_historique
UNION ALL SELECT 'action_document', COUNT(*) FROM action_document_historique
UNION ALL SELECT 'acces', COUNT(*) FROM acces_plateforme_historique
UNION ALL SELECT 'decisions', COUNT(*) FROM decision_financement_historique
UNION ALL SELECT 'archives', COUNT(*) FROM dossier_archive;
