@echo off
REM Lance test_micro.mjs (Node 18+). Meme dossier que ce fichier.
cd /d "%~dp0"
node "%~dp0test_micro.mjs" %*
exit /b %ERRORLEVEL%
