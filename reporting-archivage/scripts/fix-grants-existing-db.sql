-- Si la base uib_reporting_archivage existe déjà mais les API renvoient 500 :
-- exécuter ce script puis redémarrer reporting-archivage

ALTER DATABASE uib_reporting_archivage OWNER TO uib_user;

\c uib_reporting_archivage

GRANT ALL ON SCHEMA public TO uib_user;
GRANT CREATE ON SCHEMA public TO uib_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO uib_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO uib_user;
