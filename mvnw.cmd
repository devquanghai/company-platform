@echo off
setlocal
set "BASE_DIR=%~dp0"
for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"') do set "DIST_URL=%%B"
set "MAVEN_VERSION=3.9.11"
if defined MAVEN_USER_HOME (set "WRAPPER_ROOT=%MAVEN_USER_HOME%\wrapper\dists") else (set "WRAPPER_ROOT=%USERPROFILE%\.m2\wrapper\dists")
set "MAVEN_HOME=%WRAPPER_ROOT%\apache-maven-%MAVEN_VERSION%\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%WRAPPER_ROOT%\apache-maven-%MAVEN_VERSION%" mkdir "%WRAPPER_ROOT%\apache-maven-%MAVEN_VERSION%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip='%TEMP%\apache-maven-%MAVEN_VERSION%.zip'; Invoke-WebRequest -Uri '%DIST_URL%' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath '%WRAPPER_ROOT%\apache-maven-%MAVEN_VERSION%' -Force; Remove-Item $zip"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
