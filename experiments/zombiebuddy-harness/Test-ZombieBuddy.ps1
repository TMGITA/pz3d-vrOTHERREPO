$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..\..").Path
$javac=Get-ChildItem "$root\external\tools\jdk25" -Filter javac.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
$java=Join-Path (Split-Path $javac) java.exe
$classes="$PSScriptRoot\build\classes"
$adapter="$root\experiments\pz3d-adapter\build\classes"
$gpu="$PSScriptRoot\build\gpu-test"
$game="$root\reference\project-zomboid\42.21.0\projectzomboid.jar"
$pz="$root\reference\pz3d\0.3.0\PZ3D-0.3.0.jar"
$original="$root\reference\zombiebuddy\binaries\ZombieBuddy.jar"
$fix="$root\reference\zombiebuddy\42.21-temporary-fix\ZombieBuddy.jar"
& $javac -cp "$adapter" -d $gpu "$PSScriptRoot\tests\gpu\VersionGateVariantsTest.java"
if($LASTEXITCODE -ne 0){throw 'Gate fixture compile failed'}
& $java -cp "$gpu;$adapter" VersionGateVariantsTest $game $pz $original $fix | Tee-Object "$PSScriptRoot\build\zb-variants.log"
if($LASTEXITCODE -ne 0){throw 'Gate variant checks failed'}
& $java '-Xverify:all' "-javaagent:$PSScriptRoot\build\test-agent.jar" -cp "$gpu;$classes;$adapter;$game;$pz;$fix" RealBinaryTransformTest $game $pz $fix | Tee-Object "$PSScriptRoot\build\zb-fix-transform.log"
if($LASTEXITCODE -ne 0){throw 'Temporary fix loader compatibility checks failed'}
