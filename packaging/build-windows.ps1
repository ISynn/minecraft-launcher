# Construit la version Windows du launcher : un dossier autonome (Java inclus) et son archive .zip.
# Le joueur n'a rien à installer : il décompresse et lance « PokeIsland Launcher.exe ».
#
# Usage (depuis la racine du projet) :
#   powershell -ExecutionPolicy Bypass -File packaging\build-windows.ps1 [-Jdk "C:\Program Files\Java\jdk-24"]
#
# Le JDK doit contenir le dossier « jmods » (JDK 24 d'Oracle ou d'Adoptium, par exemple) : jlink en a besoin
# pour assembler un Java réduit aux seuls modules utilisés. java.exe est conservé dans ce Java réduit : jlink
# s'en sert pour générer l'archive CDS de base, sans laquelle l'archive de démarrage rapide ne peut être créée.
# jdk.localedata (limité au français et à l'anglais) : sans lui, les dates s'afficheraient en anglais (« Sep »).
#
# -Branding <dossier> ajoute à l'archive les visuels officiels (background, logo) pour une démonstration privée :
#   ils appartiennent à PokeIsland, ne sont jamais versionnés et ne doivent pas être diffusés publiquement.
param([string]$Jdk = $env:JAVA_HOME, [string]$Branding = "")
$ErrorActionPreference = "Stop"

$root = Split-Path $PSScriptRoot
Set-Location $root
if (-not $Jdk -or -not (Test-Path (Join-Path $Jdk "jmods"))) {
    throw "JDK avec jmods introuvable ($Jdk). Indique-le avec -Jdk `"C:\Program Files\Java\jdk-24`"."
}
$version = ([xml](Get-Content pom.xml -Raw)).project.version          # ex. 2.0.0-alpha
$numeric = ($version -split "-")[0]                                    # jpackage n'accepte que des chiffres
$name = "PokeIsland Launcher"
$dist = Join-Path $root "target\dist"

$log = Join-Path $env:TEMP "pokeisland-build.log"
Write-Output "1/4 Compilation et tests (détails : $log)"
cmd /c "mvn -B clean package > `"$log`" 2>&1"
if ($LASTEXITCODE -ne 0) { throw "La compilation ou les tests ont échoué : voir $log" }
Select-String -Path $log -Pattern "Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$" | Select-Object -Last 1 |
    ForEach-Object { Write-Output ("    " + $_.Line.Replace("[INFO] ", "")) }

Write-Output "2/4 Dépendances"
cmd /c "mvn -B -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/libs >> `"$log`" 2>&1"
if ($LASTEXITCODE -ne 0) { throw "Copie des dépendances impossible : voir $log" }
# Les artefacts JavaFX sans suffixe de plateforme sont vides (sans module-info) : jlink les refuserait.
Get-ChildItem target\libs -Filter "javafx-*.jar" | Where-Object { $_.Name -notmatch "-win\.jar$" } | Remove-Item

Write-Output "3/4 Image de l'application (jlink + jpackage)"
& (Join-Path $Jdk "bin\jpackage.exe") `
    --type app-image `
    --dest $dist `
    --name $name `
    --app-version $numeric `
    --vendor "Prototype non officiel" `
    --description "PokeIsland Launcher - prototype non officiel" `
    --icon packaging\pokeisland.ico `
    --module-path "target\launcher-$version.jar;target\libs" `
    --module fr.launcher/fr.launcher.Main `
    --add-modules jdk.localedata `
    --jlink-options "--strip-debug --no-header-files --no-man-pages --generate-cds-archive --include-locales=fr,en" `
    --java-options "--enable-native-access=javafx.graphics,fr.launcher" `
    --java-options "-Dprism.forceGPU=true" `
    --java-options "-XX:+UseSerialGC" `
    --java-options "-Xmx256m" `
    --java-options "-XX:+AutoCreateSharedArchive" `
    --java-options '-XX:SharedArchiveFile=$APPDIR\launcher.jsa'
if ($LASTEXITCODE -ne 0) { throw "jpackage a échoué." }

Write-Output "4/4 Archive"
$zip = Join-Path $dist "PokeIsland-Launcher-$version-windows.zip"
if ($Branding) {
    if (-not (Test-Path $Branding)) { throw "Dossier de visuels introuvable : $Branding" }
    $target = Join-Path (Join-Path $dist $name) "branding"
    New-Item -ItemType Directory -Force $target | Out-Null
    Get-ChildItem $Branding -File | Where-Object { $_.Extension -in ".png", ".jpg", ".jpeg" } |
        Copy-Item -Destination $target
    $zip = Join-Path $dist "PokeIsland-Launcher-$version-windows-demo.zip"
    Write-Output "    visuels officiels ajoutés (archive de démonstration privée)"
}
Compress-Archive -Path (Join-Path $dist $name) -DestinationPath $zip -Force
$size = (Get-ChildItem (Join-Path $dist $name) -Recurse -File | Measure-Object Length -Sum).Sum / 1MB
Write-Output ("Terminé : {0} ({1:N0} Mo décompressé, {2:N0} Mo en .zip)" -f $zip, $size, ((Get-Item $zip).Length / 1MB))
