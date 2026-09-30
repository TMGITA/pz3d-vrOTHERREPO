[CmdletBinding()]
param(
    [ValidateSet('Start','Stop','Status')][string]$Action='Status',
    [string]$SteamVR='C:\Program Files (x86)\Steam\steamapps\common\SteamVR',
    [int]$WindowX=0,
    [int]$WindowY=0
)
$ErrorActionPreference='Stop'
$stateFile="$PSScriptRoot\build\simulated-runtime\active.json"
if($Action -eq 'Start') {
    $SteamVR=(Resolve-Path -LiteralPath $SteamVR).Path
    $existing=Get-Process -ErrorAction SilentlyContinue | Where-Object {
        $_.ProcessName -in @('vrserver','vrcompositor','vrmonitor','vrstartup') -or
        ($_.Path -and $_.Path.StartsWith($SteamVR+'\',[StringComparison]::OrdinalIgnoreCase))
    }
    if($existing) { throw 'Close the existing SteamVR session before starting the simulated profile.' }
    $run="$PSScriptRoot\build\simulated-runtime\$(Get-Date -Format yyyyMMdd-HHmmss-fff)"
    New-Item -ItemType Directory -Force "$run\config","$run\logs" | Out-Null
    @{
        steamvr=@{ forcedDriver='null'; requireHmd=$true; activateMultipleDrivers=$false
            enableHomeApp=$false; enableHomeApp2=$false
            startDashboardFromAppLaunch=$false; startOverlayAppsFromDashboard=$false; startMonitorFromAppLaunch=$false }
        power=@{ pauseCompositorOnStandby=$false; turnOffScreensTimeout=86400.0; turnOffControllersTimeout=86400.0; returnToWatchdogTimeout=0.0 }
        dashboard=@{ enableDashboard=$false; autoShowGameTheater=$false }
        driver_null=@{ enable=$true; serialNumber='PZ3D-XR-Null'; modelNumber='PZ3D simulated HMD'
            windowX=$WindowX; windowY=$WindowY
            windowWidth=1280; windowHeight=720; renderWidth=768; renderHeight=768; displayFrequency=90.0 }
    } | ConvertTo-Json -Depth 6 | Set-Content "$run\config\steamvr.vrsettings" -Encoding ASCII
    @{version=1;jsonid='vrpathreg';runtime=@($SteamVR);config=@("$run\config");log=@("$run\logs");external_drivers=$null} |
        ConvertTo-Json -Depth 6 | Set-Content "$run\openvrpaths.vrpath" -Encoding ASCII
    $variables=@('VR_CONFIG_PATH','VR_LOG_PATH','VR_PATHREG_OVERRIDE'); $saved=@{}
    foreach($name in $variables) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
    try {
        $env:VR_CONFIG_PATH="$run\config"; $env:VR_LOG_PATH="$run\logs"; $env:VR_PATHREG_OVERRIDE="$run\openvrpaths.vrpath"
        $server=Start-Process -FilePath "$SteamVR\bin\win64\vrserver.exe" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$run\server.out" -RedirectStandardError "$run\server.err"
        Start-Sleep -Seconds 2
        if($server.HasExited) { throw "Simulated runtime exited; inspect $run\logs" }
        $python=(Get-Command python -ErrorAction Stop).Source
        $arguments=@("$PSScriptRoot\RuntimeKeepalive.py",$SteamVR,$run,[string]$server.Id)
        $quoted=($arguments | ForEach-Object { if($_ -match '"') { throw 'Unsupported quote in path.' }; '"'+$_+'"' }) -join ' '
        $keeper=Start-Process -FilePath $python -ArgumentList $quoted -WindowStyle Hidden -PassThru -RedirectStandardOutput "$run\keepalive.out" -RedirectStandardError "$run\keepalive.err"
        @{serverId=$server.Id;serverStart=$server.StartTime.ToUniversalTime().ToString('o');keeperId=$keeper.Id;keeperStart=$keeper.StartTime.ToUniversalTime().ToString('o');steamVR=$SteamVR;run=$run} |
            ConvertTo-Json | Set-Content $stateFile -Encoding UTF8
        $deadline=(Get-Date).AddSeconds(12)
        while(!(Test-Path "$run\keepalive.ready") -and !$keeper.HasExited -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
        if(!(Test-Path "$run\keepalive.ready") -or $keeper.HasExited) { throw "Runtime keepalive failed; inspect $run\keepalive.err" }
        Write-Host "Simulated SteamVR is ready and will stay available while you load the game. Use Ctrl+Shift+Scroll Lock in PZ3D. No game was launched by this script."
        Write-Host "Profile/logs: $run"
        Write-Host "After testing, use this script with -Action Stop."
    } finally { foreach($name in $variables) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') } }
    exit
}
if(!(Test-Path -LiteralPath $stateFile)) { Write-Host 'No simulated runtime started by this helper.'; exit }
$state=Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if($Action -eq 'Stop' -and $state.keeperId) {
    $keeper=Get-Process -Id $state.keeperId -ErrorAction SilentlyContinue
    if($keeper -and $keeper.StartTime.ToUniversalTime().ToString('o') -eq $state.keeperStart) {
        'stop' | Set-Content -LiteralPath "$($state.run)\keepalive.stop"
        if(!$keeper.WaitForExit(3000)) { Stop-Process -Id $keeper.Id -Force }
    }
}
$server=Get-Process -Id $state.serverId -ErrorAction SilentlyContinue
if(!$server -or $server.Path -ne "$($state.steamVR)\bin\win64\vrserver.exe" -or
    $server.StartTime.ToUniversalTime().ToString('o') -ne $state.serverStart) {
    Write-Host 'The recorded simulated server is no longer running; no processes changed.'; exit
}
if($Action -eq 'Status') {
    $keeper=if($state.keeperId) { Get-Process -Id $state.keeperId -ErrorAction SilentlyContinue } else { $null }
    if(!$keeper -or $keeper.StartTime.ToUniversalTime().ToString('o') -ne $state.keeperStart) { Write-Warning 'Runtime keepalive is absent; restart the helper before loading the game.' }
    Write-Host "Simulated server running (PID $($server.Id)); logs: $($state.run)\logs"; exit
}
$started=$server.StartTime.AddSeconds(-1)
$owned=Get-Process -ErrorAction SilentlyContinue | Where-Object {
    $_.Path -and $_.Path.StartsWith($state.steamVR+'\',[StringComparison]::OrdinalIgnoreCase) -and $_.StartTime -ge $started
}
$owned | Where-Object { $_.ProcessName -notin @('vrserver','vrcompositor') } | Stop-Process -Force -ErrorAction SilentlyContinue
$owned | Where-Object { $_.ProcessName -eq 'vrcompositor' } | Stop-Process -Force -ErrorAction SilentlyContinue
Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
Write-Host 'Simulated SteamVR stopped. Configuration and logs remain in the workspace.'
