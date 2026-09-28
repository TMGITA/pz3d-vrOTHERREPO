[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..\..").Path
$javac=Get-ChildItem "$root\external\tools\jdk25" -Filter javac.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
$java=Join-Path (Split-Path $javac) java.exe
$game="$root\reference\project-zomboid\binaries\projectzomboid.jar"
$zb="$root\reference\zombiebuddy\binaries\ZombieBuddy.jar"
$cf="$PSScriptRoot\build\controller-fixtures"
New-Item -ItemType Directory -Force $cf | Out-Null
$src=@(Get-ChildItem "$PSScriptRoot\tests\controller-fixtures" -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
$src+="$PSScriptRoot\tests\ControllerBridgeTest.java"
$src+="$PSScriptRoot\tests\InstrumentationAgent.java"
& $javac -cp "$PSScriptRoot\build\classes;$game;$zb" -d $cf $src
if($LASTEXITCODE){throw 'Controller fixture compilation failed'}
& $java '-Xverify:all' '--enable-native-access=ALL-UNNAMED' "-javaagent:$PSScriptRoot\build\test-agent.jar" -cp "$cf;$PSScriptRoot\build\classes;$game;$zb" ControllerBridgeTest | Tee-Object "$PSScriptRoot\build\controller-lifecycle.log"
if($LASTEXITCODE){throw 'Controller lifecycle fixtures failed'}
