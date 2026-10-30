$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { $mavenPath = $maven.Source }
else {
    $ideaPath = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2'
    $mavenPath = Join-Path $ideaPath 'plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
    if (-not (Test-Path -LiteralPath $mavenPath)) { throw 'Maven не знайдено. Додайте mvn до PATH.' }
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $ideaPath 'jbr' }
}
& $mavenPath -B -ntp clean install
if ($LASTEXITCODE -ne 0) { throw 'Збірка або тести завершилися помилкою.' }
& $mavenPath -B -ntp -f web/pom.xml exec:java
