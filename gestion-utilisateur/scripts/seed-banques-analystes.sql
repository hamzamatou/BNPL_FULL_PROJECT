-- =============================================================================
-- UIB BNPL — Banques partenaires + analystes (base uib_gestion_utilisateur)
-- Aligné sur les codes retournés par service-scoring-banques : UIB, EL_AMEN, EL_BARAKA
--
-- Connexion :
--   psql -h localhost -U uib_user -d uib_gestion_utilisateur -f seed-banques-analystes.sql
--
-- Mot de passe commun des comptes ci-dessous (dev) : password
-- Hash BCrypt Spring (strength 10) pour "password"
-- Après insertion, les analystes sont ACTIVE (routage BNPL sans activation OTP).
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Banques
-- -----------------------------------------------------------------------------
INSERT INTO banque (nom_banque, code_banque, email, telephone, adresse)
SELECT v.nom_banque, v.code_banque, v.email, v.telephone, v.adresse
FROM (VALUES
    ('Union Internationale de Banques', 'UIB',        'contact@uib.tn',       '+216 71 100 001', 'Avenue de la Bourse, Tunis'),
    ('Banque El Amen',                  'EL_AMEN',    'contact@elamen.tn',    '+216 71 200 002', 'Centre-ville, Tunis'),
    ('Banque El Baraka',                'EL_BARAKA',  'contact@elbaraka.tn',  '+216 71 300 003', 'Lac 1, Tunis')
) AS v(nom_banque, code_banque, email, telephone, adresse)
WHERE NOT EXISTS (
    SELECT 1 FROM banque b WHERE upper(b.code_banque) = upper(v.code_banque)
);

-- -----------------------------------------------------------------------------
-- 2. Analystes (role ANALYSTE_BANCAIRE, status ACTIVE)
-- -----------------------------------------------------------------------------
-- Hash : $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi  →  password

-- UIB
INSERT INTO users (
    email, telephone, role, status, date_creation,
    nom, prenom, password, poste, banque_id
)
SELECT
    v.email, v.telephone, 'ANALYSTE_BANCAIRE', 'ACTIVE', now(),
    v.nom, v.prenom,
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    v.poste, b.id
FROM (VALUES
    ('analyste.uib1@bnpl.local',   '+216 20 100 101', 'Mrad',   'Sami',   'Analyste crédit'),
    ('analyste.uib2@bnpl.local',   '+216 20 100 102', 'Gharbi', 'Amira',  'Analyste crédit')
) AS v(email, telephone, nom, prenom, poste)
CROSS JOIN banque b
WHERE upper(b.code_banque) = 'UIB'
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

-- El Amen
INSERT INTO users (
    email, telephone, role, status, date_creation,
    nom, prenom, password, poste, banque_id
)
SELECT
    v.email, v.telephone, 'ANALYSTE_BANCAIRE', 'ACTIVE', now(),
    v.nom, v.prenom,
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    v.poste, b.id
FROM (VALUES
    ('analyste.amen1@bnpl.local',  '+216 20 200 201', 'Ben Ali', 'Karim',  'Analyste crédit'),
    ('analyste.amen2@bnpl.local',  '+216 20 200 202', 'Trabelsi','Nadia',  'Analyste crédit')
) AS v(email, telephone, nom, prenom, poste)
CROSS JOIN banque b
WHERE upper(b.code_banque) = 'EL_AMEN'
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

-- El Baraka
INSERT INTO users (
    email, telephone, role, status, date_creation,
    nom, prenom, password, poste, banque_id
)
SELECT
    v.email, v.telephone, 'ANALYSTE_BANCAIRE', 'ACTIVE', now(),
    v.nom, v.prenom,
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    v.poste, b.id
FROM (VALUES
    ('analyste.baraka1@bnpl.local','+216 20 300 301', 'Jebali', 'Hichem', 'Analyste crédit'),
    ('analyste.baraka2@bnpl.local','+216 20 300 302', 'Mansour','Leila',  'Analyste crédit')
) AS v(email, telephone, nom, prenom, poste)
CROSS JOIN banque b
WHERE upper(b.code_banque) = 'EL_BARAKA'
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

COMMIT;

-- Vérification
SELECT b.code_banque, b.nom_banque, u.id, u.email, u.nom, u.prenom, u.status
FROM banque b
LEFT JOIN users u ON u.banque_id = b.id AND u.role = 'ANALYSTE_BANCAIRE'
ORDER BY b.code_banque, u.email;
