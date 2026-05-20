-- Exécuter dans pgAdmin sur la base uib_reporting_archivage

SELECT current_database() AS base_connectee;

-- 1) Les 5 tables existent-elles ?
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND (table_name LIKE '%historique%' OR table_name = 'dossier_archive')
ORDER BY 1;

-- Si 0 ligne → tables absentes :
--   1) scripts/fix-grants-existing-db.sql (sur postgres puis sur cette base)
--   2) redémarrer reporting-archivage (mvn spring-boot:run)
--   3) relancer ce diagnostic

-- 2) Comptes actuels
SELECT 'action_demande' AS table_name, COUNT(*) AS nb FROM action_demande_historique
UNION ALL SELECT 'action_document', COUNT(*) FROM action_document_historique
UNION ALL SELECT 'acces', COUNT(*) FROM acces_plateforme_historique
UNION ALL SELECT 'decision', COUNT(*) FROM decision_financement_historique
UNION ALL SELECT 'dossier_archive', COUNT(*) FROM dossier_archive;
