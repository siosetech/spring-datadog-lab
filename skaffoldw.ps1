<#
.SYNOPSIS
Skaffold Wrapper for Windows
.DESCRIPTION
Downloads Skaffold if it doesn't exist, and passes all arguments to it.
#>

$SKAFFOLD_VERSION = "v2.12.0"
$SKAFFOLD_URL = "https://github.com/GoogleContainerTools/skaffold/releases/download/$SKAFFOLD_VERSION/skaffold-windows-amd64.exe"
$SKAFFOLD_DIR = Join-Path $PSScriptRoot ".skaffold"
$SKAFFOLD_EXE = Join-Path $SKAFFOLD_DIR "skaffold.exe"

if (-not (Test-Path $SKAFFOLD_DIR)) {
    New-Item -ItemType Directory -Force -Path $SKAFFOLD_DIR | Out-Null
}

if (-not (Test-Path $SKAFFOLD_EXE)) {
    Write-Host "Downloading Skaffold $SKAFFOLD_VERSION..."
    Invoke-WebRequest -Uri $SKAFFOLD_URL -OutFile $SKAFFOLD_EXE
    Write-Host "Download complete!"
}

if ($args.Count -gt 0 -and $args[0] -eq "install") {
    exit
}

# Execute skaffold with all passed arguments
& $SKAFFOLD_EXE $args
