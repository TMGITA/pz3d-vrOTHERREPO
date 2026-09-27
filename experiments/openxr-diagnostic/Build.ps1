[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path "$PSScriptRoot\..\..").Path
$compiler = Get-ChildItem "$workspace\external\tools\jdk25" -Filter javac.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
if (!$compiler) { throw 'Run Setup.ps1 first to download the portable compiler and libraries.' }
$manifest = Get-Content "$PSScriptRoot\dependencies.json" -Raw | ConvertFrom-Json
foreach ($item in $manifest.libraries) {
    $path = Join-Path "$PSScriptRoot\lib" $item.file
    if (!(Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $item.sha256) {
        throw "Missing or altered dependency: $path. Run Setup.ps1."
    }
}
New-Item -ItemType Directory -Force "$PSScriptRoot\build\classes" | Out-Null
$sources = Get-ChildItem "$PSScriptRoot\src" -Filter *.java | Select-Object -ExpandProperty FullName
& $compiler --release 17 -Xlint:all -cp "$PSScriptRoot\lib\*" -d "$PSScriptRoot\build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $LASTEXITCODE" }
Write-Host 'Diagnostic compiled.'
