param(
    [string]$ResearchJava = 'C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid\jre64\bin\java.exe'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
# Only standalone decompilers are run. No game/mod entry point or Java agent is loaded.
& $ResearchJava -jar external/tools/cfr-0.152.jar reference/pz3d/42.20.4/media/java/client/PZ3D-0.2.2.jar --outputdir reference/pz3d/decompiled --silent true 2> research/evidence/cfr-pz3d-errors.txt
if ($LASTEXITCODE -ne 0) { throw 'PZ3D CFR failed' }
& $ResearchJava -jar external/tools/vineflower-1.12.0.jar --folder --thread-count=4 --log-level=WARN '-e=reference/project-zomboid/binaries/projectzomboid.jar' '-e=reference/zombiebuddy/binaries/ZombieBuddy.jar' reference/pz3d/42.20.4/media/java/client/PZ3D-0.2.2.jar reference/pz3d/vineflower *> research/evidence/vineflower-pz3d.log
if ($LASTEXITCODE -ne 0) { throw 'PZ3D Vineflower failed' }
& $ResearchJava -jar external/tools/cfr-0.152.jar reference/project-zomboid/binaries/selected-research-classes.jar --outputdir reference/project-zomboid/decompiled --silent true --extraclasspath reference/project-zomboid/binaries/projectzomboid.jar 2> research/evidence/cfr-vanilla-errors.txt
if ($LASTEXITCODE -ne 0) { throw 'Vanilla CFR failed' }
& $ResearchJava -jar external/tools/cfr-0.152.jar reference/project-zomboid/binaries/additional-research-classes.jar --outputdir reference/project-zomboid/decompiled --silent true --extraclasspath reference/project-zomboid/binaries/projectzomboid.jar 2> research/evidence/cfr-additional-errors.txt
if ($LASTEXITCODE -ne 0) { throw 'Additional vanilla CFR failed' }
& $ResearchJava -jar external/tools/cfr-0.152.jar reference/zombiebuddy/binaries/selected-research-classes.jar --outputdir reference/zombiebuddy/decompiled --silent true --extraclasspath reference/zombiebuddy/binaries/ZombieBuddy.jar 2> research/evidence/cfr-zb-errors.txt
if ($LASTEXITCODE -ne 0) { throw 'ZombieBuddy CFR failed' }
