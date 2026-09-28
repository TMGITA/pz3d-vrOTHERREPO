[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..\..").Path
$javac=Get-ChildItem "$root\external\tools\jdk25" -Filter javac.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
$java=Join-Path (Split-Path $javac) java.exe
$jar=Join-Path (Split-Path $javac) jar.exe
$game="$root\reference\project-zomboid\42.21.0\projectzomboid.jar"
$pz="$root\reference\pz3d\0.3.0\PZ3D-0.3.0.jar"
$zb="$root\reference\zombiebuddy\binaries\ZombieBuddy.jar"
$classes="$PSScriptRoot\build\classes"
New-Item -ItemType Directory -Force $classes | Out-Null
$sources=@(Get-ChildItem "$PSScriptRoot\src","$PSScriptRoot\tools" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
& $javac -Xlint:all -cp "$game;$zb" -d $classes $sources
if($LASTEXITCODE -ne 0) { throw 'Adapter compilation failed.' }
& $java -cp "$classes;$game;$zb" OfflineAdapter $game $pz $zb "$PSScriptRoot\build\instrumented"
if($LASTEXITCODE -ne 0) { throw 'Offline transform failed.' }
& $jar --create --file "$PSScriptRoot\build\pz3d-pair-hooks.jar" -C $classes pzvr
if($LASTEXITCODE -ne 0) { throw 'Hook packaging failed.' }
Write-Host 'Offline adapter built. Nothing installed or launched.'
