[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot\..\..").Path
& "$root\experiments\pz3d-adapter\Build.ps1"
$javac=Get-ChildItem "$root\external\tools\jdk25" -Filter javac.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
$jar=Join-Path (Split-Path $javac) jar.exe
$game="$root\reference\project-zomboid\42.21.0\projectzomboid.jar"
$pz="$root\reference\pz3d\0.3.0\PZ3D-0.3.0.jar"
$zb="$root\reference\zombiebuddy\binaries\ZombieBuddy.jar"
$classes="$PSScriptRoot\build\classes"
$adapter="$root\experiments\pz3d-adapter\build\classes"
New-Item -ItemType Directory -Force $classes | Out-Null
$sources=Get-ChildItem "$PSScriptRoot\src" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$xr="$root\experiments\openxr-diagnostic\lib\lwjgl-openxr-3.4.1.jar"
& $javac -Xlint:all -cp "$adapter;$game;$pz;$zb;$xr" -d $classes $sources
if($LASTEXITCODE -ne 0) { throw 'Harness compilation failed.' }
$package="$PSScriptRoot\dist\PZ3DVRTest"
New-Item -ItemType Directory -Force "$package\42.20.4\media\java\client","$package\common" | Out-Null
Copy-Item "$PSScriptRoot\mod\42.20.4\*" "$package\42.20.4" -Recurse -Force
& $jar --create --file "$package\42.20.4\media\java\client\PZ3DVRTest.jar" -C $adapter pzvr -C $classes pzvr
if($LASTEXITCODE -ne 0) { throw 'Harness packaging failed.' }
& python "$PSScriptRoot\Bundle-XR.py" "$package\42.20.4\media\java\client\PZ3DVRTest.jar"
if($LASTEXITCODE -ne 0) { throw 'OpenXR dependency bundling failed.' }
if(Test-Path "$PSScriptRoot\README.md") { Copy-Item "$PSScriptRoot\README.md" "$package\README.md" -Force }
Copy-Item "$PSScriptRoot\SimulatedRuntime.ps1","$PSScriptRoot\RuntimeKeepalive.py","$PSScriptRoot\HeadsetWindow.py","$PSScriptRoot\THIRD_PARTY.md" $package -Force
Copy-Item "$PSScriptRoot\licenses" $package -Recurse -Force
$hash=Get-FileHash "$package\42.20.4\media\java\client\PZ3DVRTest.jar" -Algorithm SHA256
"$($hash.Hash.ToLowerInvariant())  42.20.4/media/java/client/PZ3DVRTest.jar" | Set-Content "$PSScriptRoot\dist\SHA256.txt" -Encoding ASCII
Copy-Item "$PSScriptRoot\dist\SHA256.txt" "$package\SHA256.txt" -Force
$version=(Select-String -LiteralPath "$PSScriptRoot\mod\42.20.4\mod.info" -Pattern '^modversion=(.+)$').Matches.Groups[1].Value
$archive="$PSScriptRoot\dist\PZ3DVRTest-$version.zip"
Compress-Archive -Path $package -DestinationPath $archive -Force
Write-Host "Reviewable unsigned package: $archive"
