# ============================================================================
# seed-demo-tenant.ps1 (v2 — fixed)
#
# Fixes vs. the previous version:
#   1. Migrations were sorted alphabetically as TEXT ("V10" < "V2"), running
#      V14 before V7/V8 even created the tables it depends on. Now sorted
#      numerically by the version number extracted from the filename.
#   2. A failed migration was still being marked "applied" in
#      flyway_schema_history, so it silently never got retried. Now the
#      script checks the actual exit code and ABORTS immediately on any
#      failure, leaving that migration unmarked so a rerun will retry it.
# ============================================================================

$ErrorActionPreference = "Stop"

$CONTAINER = "techdesk-postgres"
$DB_USER   = "techdesk_admin"
$DB_NAME   = "techdesk"
$DB_PASS   = "changeme"
$SCHEMA    = "tenant_demo"

$MIGRATIONS_DIR = "tenant-service\src\main\resources\db\migration\tenant"
$SEED_DATA_FILE = "scripts\seed-demo-data.sql"

function Invoke-Psql {
    param([string]$Sql)
    docker exec -e PGPASSWORD=$DB_PASS $CONTAINER psql -U $DB_USER -d $DB_NAME -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql command failed (exit $LASTEXITCODE): $Sql"
    }
}

function Invoke-PsqlFile {
    param([string]$SetPathSql, [string]$ContainerFilePath)
    docker exec -e PGPASSWORD=$DB_PASS $CONTAINER psql -U $DB_USER -d $DB_NAME -v ON_ERROR_STOP=1 `
        -c $SetPathSql -f $ContainerFilePath
    if ($LASTEXITCODE -ne 0) {
        throw "Migration file failed (exit $LASTEXITCODE): $ContainerFilePath -- fix the underlying issue, then rerun this script (already-applied migrations will be skipped)."
    }
}

function Copy-ToContainer {
    param([string]$LocalPath, [string]$ContainerPath)
    docker cp $LocalPath "${CONTAINER}:${ContainerPath}"
}

# ----------------------------------------------------------------------------
Write-Host "==> Registering $SCHEMA in public.tenants (idempotent)" -ForegroundColor Cyan
Invoke-Psql @"
INSERT INTO public.tenants (name, schema_name, status, admin_email)
VALUES ('Demo Company', '$SCHEMA', 'ACTIVE', 'admin@demo.techdesk.local')
ON CONFLICT (schema_name) DO UPDATE SET status = 'ACTIVE';
"@

# ----------------------------------------------------------------------------
Write-Host "==> Creating schema $SCHEMA (idempotent)" -ForegroundColor Cyan
Invoke-Psql "CREATE SCHEMA IF NOT EXISTS `"$SCHEMA`";"

# ----------------------------------------------------------------------------
Write-Host "==> Bootstrapping flyway_schema_history table for idempotency tracking" -ForegroundColor Cyan
Invoke-Psql "CREATE TABLE IF NOT EXISTS `"$SCHEMA`".flyway_schema_history (script VARCHAR(255) PRIMARY KEY);"

# ----------------------------------------------------------------------------
Write-Host "==> Applying tenant-service migrations against $SCHEMA (correct numeric order)" -ForegroundColor Cyan

# Extract the numeric version from "V14__something.sql" -> 14, and sort by
# that number rather than the filename string (which would put V10 before V2).
$migrationFiles = Get-ChildItem -Path $MIGRATIONS_DIR -Filter "V*.sql" |
    ForEach-Object {
        if ($_.Name -match '^V(\d+)__') {
            [PSCustomObject]@{ File = $_; Version = [int]$Matches[1] }
        }
    } |
    Sort-Object Version

Write-Host "    Order: $($migrationFiles.File.Name -join ', ')" -ForegroundColor DarkGray

foreach ($entry in $migrationFiles) {
    $file = $entry.File
    $filename = $file.Name

    $alreadyApplied = docker exec -e PGPASSWORD=$DB_PASS $CONTAINER psql -U $DB_USER -d $DB_NAME -tA `
        -c "SELECT EXISTS (SELECT 1 FROM `"$SCHEMA`".flyway_schema_history WHERE script = '$filename');"

    if ($alreadyApplied.Trim() -eq "t") {
        Write-Host "    skip (already applied): $filename" -ForegroundColor DarkGray
        continue
    }

    Write-Host "    applying: $filename" -ForegroundColor Yellow
    $containerPath = "/tmp/$filename"
    Copy-ToContainer $file.FullName $containerPath

    # Throws and STOPS THE WHOLE SCRIPT if this migration fails — no more
    # silently marking broken migrations as done.
    Invoke-PsqlFile "SET search_path TO `"$SCHEMA`", public;" $containerPath

    Invoke-Psql "INSERT INTO `"$SCHEMA`".flyway_schema_history (script) VALUES ('$filename') ON CONFLICT DO NOTHING;"
}

# ----------------------------------------------------------------------------
Write-Host "==> Seeding demo users, staff profiles, and tickets" -ForegroundColor Cyan

if (-not (Test-Path $SEED_DATA_FILE)) {
    Write-Host "ERROR: $SEED_DATA_FILE not found. Run this script from the repo root." -ForegroundColor Red
    exit 1
}

$containerSeedPath = "/tmp/seed-demo-data.sql"
Copy-ToContainer $SEED_DATA_FILE $containerSeedPath
Invoke-PsqlFile "SET search_path TO `"$SCHEMA`", public;" $containerSeedPath

# ----------------------------------------------------------------------------
Write-Host "`n==> Done. Demo tenant '$SCHEMA' is ready." -ForegroundColor Green
Write-Host "    Log in with X-Tenant-ID: $SCHEMA" -ForegroundColor Green
Write-Host "    Company admin: admin@demo.techdesk.local / Demo1234!" -ForegroundColor Green
Write-Host "    (all seeded users share this password)" -ForegroundColor Green
