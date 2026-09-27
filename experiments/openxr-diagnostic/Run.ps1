[CmdletBinding()]
param(
    [ValidateSet('preview', 'probe', 'xr')][string]$Mode = 'preview',
    [ValidateSet('charts', 'scene')][string]$Content = 'charts',
    [ValidateSet('neutral', 'turned')][string]$PreviewPose = 'neutral',
    [ValidateRange(1, 300)][int]$Seconds = 15,
    [switch]$NullRuntime,
    [switch]$Hidden,
    [string]$SteamVR = 'C:\Program Files (x86)\Steam\steamapps\common\SteamVR',
    [string]$Java
)
$ErrorActionPreference = 'Stop'
if ($NullRuntime -and $Mode -eq 'preview') { throw 'NullRuntime applies to probe or xr mode.' }
& "$PSScriptRoot\Build.ps1"
$workspace = (Resolve-Path "$PSScriptRoot\..\..").Path
if (!$Java) { $Java = Get-ChildItem "$workspace\external\tools\jdk25" -Filter java.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName }
$Java = (Resolve-Path -LiteralPath $Java).Path
$run = Join-Path "$PSScriptRoot\runs" ((Get-Date -Format 'yyyyMMdd-HHmmss-fff') + "-$Mode")
New-Item -ItemType Directory -Force $run | Out-Null
$envNames = @('XR_RUNTIME_JSON', 'VR_CONFIG_PATH', 'VR_LOG_PATH', 'VR_PATHREG_OVERRIDE')
$saved = @{}
foreach ($name in $envNames) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$runtimeStarted = $false
$launchTime = Get-Date
$app = $null
$exitCode = 1
try {
    if ($NullRuntime) {
        $SteamVR = (Resolve-Path -LiteralPath $SteamVR).Path
        $existing = Get-Process -ErrorAction SilentlyContinue | Where-Object {
            $_.ProcessName -in @('vrserver', 'vrcompositor', 'vrmonitor', 'vrstartup') -or
            ($_.Path -and $_.Path.StartsWith($SteamVR + '\', [StringComparison]::OrdinalIgnoreCase))
        }
        if ($existing) { throw 'Close the existing SteamVR session before using the isolated null profile.' }
        $config = New-Item -ItemType Directory -Force "$run\steamvr-config"
        $logs = New-Item -ItemType Directory -Force "$run\steamvr-logs"
        @{
            steamvr = @{
                forcedDriver = 'null'; requireHmd = $true; activateMultipleDrivers = $false
                enableHomeApp = $false; enableHomeApp2 = $false; pauseCompositorOnStandby = $false
                startDashboardFromAppLaunch = $false; startOverlayAppsFromDashboard = $false
                startMonitorFromAppLaunch = $false
            }
            driver_null = @{
                enable = $true; serialNumber = 'PZ3D-Diagnostic-Null'; modelNumber = 'PZ3D Diagnostic Null HMD'
                windowWidth = 1280; windowHeight = 720; renderWidth = 768; renderHeight = 768
                displayFrequency = 90.0
            }
        } | ConvertTo-Json -Depth 6 | Set-Content "$config\steamvr.vrsettings" -Encoding ASCII
        @{
            version = 1; jsonid = 'vrpathreg'; runtime = @($SteamVR)
            config = @($config.FullName); log = @($logs.FullName); external_drivers = $null
        } | ConvertTo-Json -Depth 6 | Set-Content "$run\openvrpaths.vrpath" -Encoding ASCII
        $env:XR_RUNTIME_JSON = Join-Path $SteamVR 'steamxr_win64.json'
        $env:VR_CONFIG_PATH = $config.FullName
        $env:VR_LOG_PATH = $logs.FullName
        $env:VR_PATHREG_OVERRIDE = "$run\openvrpaths.vrpath"
        if (!(Test-Path -LiteralPath $env:XR_RUNTIME_JSON)) { throw 'SteamVR OpenXR runtime manifest is missing.' }
        $runtimeStarted = $true
        $launchTime = Get-Date
        Start-Process -FilePath "$SteamVR\bin\win64\vrserver.exe" -WindowStyle Hidden -PassThru | Out-Null
        # The runtime can take a few seconds to create its synthetic HMD.
        Start-Sleep -Seconds 5
    }
    # Quote each argument for Windows command-line parsing. No shell evaluates these arguments.
    $arguments = @('--enable-native-access=ALL-UNNAMED', '-cp', "$PSScriptRoot\build\classes;$PSScriptRoot\lib\*", 'Diagnostic', "--mode=$Mode", "--content=$Content", "--preview-pose=$PreviewPose", "--seconds=$Seconds", "--output=$run", "--hidden=$($Hidden.IsPresent.ToString().ToLowerInvariant())")
    $quoted = ($arguments | ForEach-Object { if ($_ -match '"') { throw 'Double quotes are not supported in paths.' }; '"' + $_ + '"' }) -join ' '
    $app = Start-Process -FilePath $Java -ArgumentList $quoted -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput "$run\stdout.log" -RedirectStandardError "$run\stderr.log" -PassThru
    # Keep a native process handle open so Windows retains the exit code after termination.
    $processHandle = $app.Handle
    # Native runtime calls can block: bound the whole Java process as well as its frame loop.
    if (!$app.WaitForExit(($Seconds + 45) * 1000)) {
        Stop-Process -Id $app.Id -Force
        @{ status = 'PROCESS_TIMEOUT'; exitCode = 3; gameIntegrationValidated = $false; physicalHeadsetValidated = $false } | ConvertTo-Json | Set-Content "$run\runner-result.json"
        $exitCode = 3
    } else {
        $app.WaitForExit()
        $exitCode = $app.ExitCode
        if ($null -eq $exitCode) { $exitCode = 1 }
    }
    Get-Content "$run\stdout.log"
    if ((Get-Item "$run\stderr.log").Length -gt 0) { Get-Content "$run\stderr.log" | Write-Host }
} finally {
    if ($app -and !$app.HasExited) { Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue }
    if ($runtimeStarted) {
        # There was no pre-existing runtime. Stop only SteamVR processes from this launch.
        $owned = Get-Process -ErrorAction SilentlyContinue | Where-Object {
            $_.Path -and
            $_.Path.StartsWith($SteamVR + '\', [StringComparison]::OrdinalIgnoreCase) -and $_.StartTime -ge $launchTime.AddSeconds(-1)
        }
        $owned | Select-Object Id, ProcessName, Path, StartTime | ConvertTo-Json | Set-Content "$run\runtime-processes.json"
        # Stop clients first: a surviving room-setup/monitor client can restart the server.
        $owned | Where-Object { $_.ProcessName -notin @('vrserver', 'vrcompositor') } | Stop-Process -Force -ErrorAction SilentlyContinue
        $owned | Where-Object { $_.ProcessName -eq 'vrcompositor' } | Stop-Process -Force -ErrorAction SilentlyContinue
        $owned | Where-Object { $_.ProcessName -eq 'vrserver' } | Stop-Process -Force -ErrorAction SilentlyContinue
    }
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    Write-Host "Evidence: $run"
}
exit $exitCode
