[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RunDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
if (!('SceneCaptureCheck' -as [type])) {
    Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
public static class SceneCaptureCheck {
    public static double[] Centre(string path, bool cyan) {
        using (var image = new Bitmap(path)) {
            double xsum=0, ysum=0, count=0;
            for(int y=0;y<image.Height;y++) for(int x=0;x<image.Width;x++) {
                var c=image.GetPixel(x,y);
                if(c.B>250 && (cyan ? c.G>250 && c.R<5 : c.R>250 && c.G<5)) {
                    xsum+=x+.5; ysum+=y+.5; count++;
                }
            }
            if(count<9) throw new Exception("Landmark absent or too small: "+path);
            return new double[]{xsum/count,ysum/count,count};
        }
    }
}
'@
}
$run = (Resolve-Path -LiteralPath $RunDirectory).Path
$report = Get-Content "$run\result.json" -Raw | ConvertFrom-Json
if ($report.content -ne 'scene' -or $report.exitCode -ne 0) { throw 'Expected a successful scene run.' }
$checks = foreach ($eye in 0,1) {
    $file = if ($eye -eq 0) { 'left.png' } else { 'right.png' }
    foreach ($marker in 0,1) {
        $actual = [SceneCaptureCheck]::Centre("$run\$file", $marker -eq 0)
        $expected = $report."eye${eye}Marker${marker}ExpectedPixel"
        $pixelError = [Math]::Max([Math]::Abs($actual[0]-$expected[0]),[Math]::Abs($actual[1]-$expected[1]))
        if ($pixelError -gt 1.0) { throw "Eye $eye marker $marker differs from projected position by $pixelError pixels" }
        @{eye=$eye;marker=$marker;actual=@($actual[0],$actual[1]);expected=$expected;maxErrorPixels=$pixelError;pixels=$actual[2]}
    }
}
@{status='GPU_LANDMARK_CHECKS_OK';checks=@($checks)} | ConvertTo-Json -Depth 6 | Set-Content "$run\scene-checks.json"
Write-Host "Four GPU landmark checks passed: $run"
