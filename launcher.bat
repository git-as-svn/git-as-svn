@echo off

if not exist "%~dp0launcher.ps1" goto NotFound
PowerShell -NoProfile -NoLogo -ExecutionPolicy unrestricted -Command "[System.Threading.Thread]::CurrentThread.CurrentCulture = ''; [System.Threading.Thread]::CurrentThread.CurrentUICulture = '';& '%~dp0launcher.ps1' %*"
goto :EOF
:NotFound
echo Not Found launcher.ps1 in %~dp0,Please reset your git-as-svn
PAUSE

