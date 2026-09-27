[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
& "$PSScriptRoot\Build.ps1"
$root=(Resolve-Path "$PSScriptRoot\..\..").Path
$javac=Get-ChildItem "$root\external\tools\jdk25" -Filter javac.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
$java=Join-Path (Split-Path $javac) java.exe
$game="$root\reference\project-zomboid\binaries\projectzomboid.jar"
$pz="$root\reference\pz3d\42.20.4\media\java\client\PZ3D-0.2.2.jar"
$zb="$root\reference\zombiebuddy\binaries\ZombieBuddy.jar"
$fixture="$PSScriptRoot\build\fixtures"
$patched="$PSScriptRoot\build\fixtures-patched"
New-Item -ItemType Directory -Force $fixture,$patched | Out-Null
$sources=Get-ChildItem "$PSScriptRoot\tests" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
& $javac -Xlint:all -cp "$PSScriptRoot\build\classes;$game;$zb" -d $fixture $sources
if($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed.' }
$testClasspath="$fixture;$PSScriptRoot\build\classes;$game;$zb"
& $java -cp $testClasspath FixtureTransform $fixture $patched
if($LASTEXITCODE -ne 0) { throw 'Fixture transformation failed.' }
& $java -Xverify:all -cp "$patched;$testClasspath" AdapterTest $game $pz $zb "$PSScriptRoot\build" | Tee-Object "$PSScriptRoot\build\test-results.txt"
if($LASTEXITCODE -ne 0) { throw 'Adapter tests failed.' }
