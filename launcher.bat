@echo off

if not exist "%~dp0launcher.ps1" goto NotFound
start PowerShell -NoLogo -NoProfile -ExecutionPolicy unrestricted -File "%~dp0launcher.ps1"  %1 %2 %3


:NotFound
echo "Not Found launcher.ps1 in %~dp0,Please reset your git-as-svn"
PAUSE

