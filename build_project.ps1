$ErrorActionPreference = "Stop"

# Configuration
$gradleVersion = "8.4"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$gradleDest = "$PSScriptRoot\gradle-dist"
$gradleZip = "$gradleDest\gradle.zip"
$gradleHome = "$gradleDest\gradle-$gradleVersion"
$gradleBin = "$gradleHome\bin\gradle.bat"

# 1. Setup Gradle
Write-Host "Checking for Gradle..." -ForegroundColor Cyan
if (-not (Test-Path "$gradleBin")) {
    Write-Host "Gradle not found. Downloading Gradle $gradleVersion..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $gradleDest | Out-Null
    
    # Download
    Invoke-WebRequest -Uri $gradleUrl -OutFile $gradleZip
    
    # Unzip
    Write-Host "Extracting Gradle..." -ForegroundColor Yellow
    Expand-Archive -Path $gradleZip -DestinationPath $gradleDest -Force
    
    # Cleanup
    Remove-Item $gradleZip
    Write-Host "Gradle setup complete." -ForegroundColor Green
} else {
    Write-Host "Using local Gradle at $gradleBin" -ForegroundColor Green
}

# 2. Check Android SDK
$localProperties = "$PSScriptRoot\local.properties"
if (-not (Test-Path $localProperties)) {
    Write-Host "ERROR: local.properties not found. Please ensure it exists and points to your SDK." -ForegroundColor Red
    exit 1
}

# 3. Run Build
Write-Host "Building APK..." -ForegroundColor Cyan
& $gradleBin assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build Successful!" -ForegroundColor Green
    $apkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
    
    if (Test-Path $apkPath) {
        Write-Host "APK located at: $apkPath" -ForegroundColor White
        
        # 4. Install
        Write-Host "Attempting to install to connected device..." -ForegroundColor Cyan
        $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
        
        if (Test-Path $adb) {
            & $adb install -r $apkPath
        } else {
             Write-Host "ADB not found automatically. You can install the APK manually." -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "Build Failed." -ForegroundColor Red
}
