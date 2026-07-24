# Cleanup secrets and build artifacts (PowerShell)
# Usage: run from repository root in PowerShell (as current user)
# 1) Backup repository
# 2) Remove tracked secret files and build artifacts from working tree and disk
# 3) Rewrite history with git-filter-repo (if needed)

Write-Host "STEP 0: Ensure you are at repo root"
Set-Location -Path "$PSScriptRoot\.."

# Backup
Write-Host "Creating git bundle backup in parent directory..."
git bundle create ..\spring-datadog-lab.bundle --all

# Remove from index if tracked
Write-Host "Removing cluster-keys.json and target artifacts from index (if tracked)"
# Attempt to remove cluster-keys.json from index; if not present, report and continue
git rm --cached -f cluster-keys.json -r 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host "cluster-keys.json not tracked or already removed" }

# Remove all files in target/ from index (handles multi-module layout)
Get-ChildItem -Path . -Recurse -Directory -Filter target -ErrorAction SilentlyContinue | ForEach-Object {
    $targetPath = $_.FullName
    Write-Host "Checking index for: $targetPath"
    git rm --cached -r --ignore-unmatch "$targetPath" 2>$null
}

# Commit removals
git add .gitignore 2>$null
if (-not (git diff --cached --quiet)) {
    git commit -m "chore: remove tracked secrets and build artifacts\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
} else {
    Write-Host "No staged changes to commit"
}

# Delete files from working tree (disk) to avoid accidental re-commit
Write-Host "Deleting cluster-keys.json and target folders from disk (if present)"
Remove-Item -LiteralPath .\cluster-keys.json -Force -ErrorAction SilentlyContinue
Get-ChildItem -Path . -Recurse -Directory -Filter target -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "Removing: $($_.FullName)"
    Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
}

# If secrets were committed in history, rewrite history using git-filter-repo
Write-Host "If secrets exist in commit history, run git-filter-repo to purge them."
Write-Host "Checking for git-filter-repo..."
try {
    git filter-repo --version >$null 2>$null
    $hasFilterRepo = $true
} catch {
    $hasFilterRepo = $false
}

if (-not $hasFilterRepo) {
    Write-Host "git-filter-repo not found. Installing via pip (user)."
    python -m pip install --user git-filter-repo
    if ($LASTEXITCODE -ne 0) { Write-Host "Failed to install git-filter-repo; please install it manually and rerun script."; exit 1 }
}

Write-Host "Running git-filter-repo to remove sensitive paths from all history..."
# Adjust path-glob patterns if your project layout differs
git filter-repo --force --invert-paths --paths cluster-keys.json --path-glob '*/target/**' --path-glob 'target/**' --path-glob 'cluster-keys.json' 2>&1 | Write-Host

# Cleanup reflogs and perform GC
Write-Host "Cleaning reflog and running aggressive GC..."
git reflog expire --expire=now --all
git gc --prune=now --aggressive

Write-Host "Done. Verify with: git log --stat --all -- cluster-keys.json"
Write-Host "If satisfied, you can now push to remote (if any). If remote exists, use --force to overwrite remote history and coordinate with collaborators."