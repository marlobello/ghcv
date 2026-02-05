# Windows Setup Script for GHCV Project
# Run this script before working with the project

Write-Host "Setting up environment for GHCV project..." -ForegroundColor Green
Write-Host ""

# Set JAVA_HOME to Android Studio's JBR
$javaHome = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    Write-Host "✓ JAVA_HOME set to: $env:JAVA_HOME" -ForegroundColor Green
    
    # Verify Java
    $javaVersion = & java -version 2>&1 | Select-Object -First 1
    Write-Host "✓ Java Version: $javaVersion" -ForegroundColor Green
} else {
    Write-Host "✗ Android Studio JBR not found at: $javaHome" -ForegroundColor Red
    Write-Host "  Please ensure Android Studio is installed" -ForegroundColor Yellow
    exit 1
}

# Set ANDROID_HOME
$androidHome = "$env:LOCALAPPDATA\Android\Sdk"
if (Test-Path $androidHome) {
    $env:ANDROID_HOME = $androidHome
    $env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
    Write-Host "✓ ANDROID_HOME set to: $env:ANDROID_HOME" -ForegroundColor Green
} else {
    Write-Host "⚠ Android SDK not found at: $androidHome" -ForegroundColor Yellow
    Write-Host "  Run Android Studio setup wizard to install SDK" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Environment setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Available commands:" -ForegroundColor Cyan
Write-Host "  .\gradlew.bat tasks          - List all available tasks"
Write-Host "  .\gradlew.bat build          - Build the project"
Write-Host "  .\gradlew.bat clean          - Clean build artifacts"
Write-Host "  .\gradlew.bat installDebug   - Install debug APK to connected device"
Write-Host "  adb devices                  - List connected Android devices"
Write-Host ""
