-- =============================================================================
-- reporting-archivage — PostgreSQL (à exécuter en superuser, ex. postgres / pgAdmin)
-- Erreur fréquente sans ces droits : « droit refusé pour le schéma public » → HTTP 500
-- =============================================================================

-- 1) Créer la base avec uib_user comme propriétaire (recommandé)
CREATE DATABASE uib_reporting_archivage
    WITH OWNER = uib_user
    ENCODING = 'UTF8'
    TEMPLATE = template0;

-- 2) Si la base existe déjà, exécuter plutôt :
-- ALTER DATABASE uib_reporting_archivage OWNER TO uib_user;

-- 3) Droits sur le schéma public (PostgreSQL 15+)
\c uib_reporting_archivage

GRANT ALL ON SCHEMA public TO uib_user;
GRANT CREATE ON SCHEMA public TO uib_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO uib_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO uib_user;

-- Les tables sont créées au démarrage Spring (spring.jpa.hibernate.ddl-auto=update)
