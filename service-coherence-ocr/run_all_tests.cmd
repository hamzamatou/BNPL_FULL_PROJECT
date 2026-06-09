@echo off
REM Genere les JPG de demo puis lance tous les scenarios test_micro.mjs
cd /d "%~dp0"

echo === Generation des documents de test ===
.\venv\Scripts\python.exe generate_test_docs.py
if errorlevel 1 (
  echo Echec generate_test_docs.py
  exit /b 1
)

echo.
echo === Verification service Flask :8090 ===
curl -s -o nul -w "HTTP %%{http_code}" http://localhost:8090/health
echo.
curl -s http://localhost:8090/health
echo.

echo.
echo === Lancement test_micro --all ===
node test_micro.mjs --all
exit /b %ERRORLEVEL%
