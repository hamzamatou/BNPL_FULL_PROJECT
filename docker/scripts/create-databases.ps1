# Crée les bases manquantes sur un volume Postgres déjà existant
# Usage : .\docker\scripts\create-databases.ps1

$ErrorActionPreference = "Stop"
$adminDb = "uib_gestion_demande"

$databases = @(
  "uib_bnpl",
  "uib_notification",
  "uib_gestion_demisateur",
  "uib_gestion_demande",
  "uib_gestion_utilisateur",
  "uib_camunda"
)

# Corriger typo
$databases = @(
  "uib_bnpl",
  "uib_notification",
  "uib_gestion_demande",
  "uib_gestion_utilisateur",
  "uib_camunda",
  "uib_reporting_archivage"
)

Write-Host "Connexion via base existante: $adminDb"
foreach ($db in $databases) {
  $sql = "SELECT 1 FROM pg_database WHERE datname = '$db'"
  $exists = docker exec uib-postgres psql -U uib_user -d $adminDb -tAc $sql 2>$null
  if ($exists -match "1") {
    Write-Host "  OK deja presente: $db"
  } else {
    Write-Host "  Creation: $db"
    docker exec uib-postgres psql -U uib_user -d $adminDb -c "CREATE DATABASE $db;"
  }
}
Write-Host "Termine."
