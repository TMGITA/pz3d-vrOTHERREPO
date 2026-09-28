$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..\..").Path
$javac=Get-ChildItem "$root\external\tools\jdk25" -Filter javac.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
$java=Join-Path (Split-Path $javac) java.exe
$game="$root\reference\project-zomboid\42.21.0\projectzomboid.jar"
$zb="$root\reference\zombiebuddy\binaries\ZombieBuddy.jar"
$classes="$PSScriptRoot\build\classes"
$fixtures="$PSScriptRoot\build\turn-fixtures"
New-Item -ItemType Directory -Force $fixtures | Out-Null
$sources=@(Get-ChildItem "$PSScriptRoot\tests\turn-fixtures" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
$sources+="$PSScriptRoot\tests\TurnTest.java","$PSScriptRoot\tests\InstrumentationAgent.java"
& $javac -cp "$classes;$game;$zb" -d $fixtures $sources
if($LASTEXITCODE -ne 0){throw 'Turning fixture compilation failed'}
& $java '-Xverify:all' "-javaagent:$PSScriptRoot\build\test-agent.jar" -cp "$fixtures;$classes;$game;$zb" TurnTest | Tee-Object "$PSScriptRoot\build\turn-test.log"
if($LASTEXITCODE -ne 0){throw 'Turning fixture checks failed'}
