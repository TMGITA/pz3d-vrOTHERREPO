[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path "$PSScriptRoot\..\..").Path
$manifest = Get-Content "$PSScriptRoot\dependencies.json" -Raw | ConvertFrom-Json
function Get-VerifiedFile($url, $path, $checksum) {
    if ((Test-Path -LiteralPath $path) -and (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -eq $checksum) { return }
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$path.partial"
    if ((Get-FileHash -LiteralPath "$path.partial" -Algorithm SHA256).Hash -ne $checksum) { throw "Checksum mismatch: $url" }
    Move-Item -LiteralPath "$path.partial" -Destination $path -Force
}
New-Item -ItemType Directory -Force "$PSScriptRoot\lib", "$workspace\external\tools" | Out-Null
foreach ($item in $manifest.libraries) { Get-VerifiedFile $item.url (Join-Path "$PSScriptRoot\lib" $item.file) $item.sha256 }
$archive = "$workspace\external\tools\temurin25-jdk.zip"
Get-VerifiedFile $manifest.jdk.url $archive $manifest.jdk.sha256
if (!(Get-ChildItem "$workspace\external\tools\jdk25" -Filter javac.exe -Recurse -ErrorAction SilentlyContinue)) {
    Expand-Archive -LiteralPath $archive -DestinationPath "$workspace\external\tools\jdk25" -Force
}
Write-Host 'Workspace-local dependencies ready. No system installation or PATH changes.'
