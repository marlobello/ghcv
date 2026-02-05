# Windows Setup Guide for GHCV

## ✅ Installation Complete!

Android Studio has been successfully installed on your Windows machine.

## 📍 Installation Location

- **Android Studio:** `C:\Program Files\Android\Android Studio`
- **Java (JBR):** `C:\Program Files\Android\Android Studio\jbr`
- **Android SDK:** `C:\Users\marlobell\AppData\Local\Android\Sdk`

## 🚀 First-Time Setup

### 1. Complete Android Studio Setup Wizard

Android Studio should be running now. If not, launch it from the Start Menu.

**Follow these steps in the setup wizard:**

1. **Welcome Screen**
   - Click "Next"

2. **Install Type**
   - Select "Standard" installation
   - Click "Next"

3. **Select UI Theme**
   - Choose your preferred theme (Light/Dark)
   - Click "Next"

4. **SDK Components**
   - Review the components to be installed
   - Make sure these are included:
     - Android SDK
     - Android SDK Platform (API 34, 35)
     - Android SDK Build-Tools
     - Android SDK Platform-Tools
     - Android Emulator
   - Click "Next"

5. **Verify Settings**
   - Review SDK location: `C:\Users\marlobell\AppData\Local\Android\Sdk`
   - Click "Finish"

6. **Downloading Components**
   - Wait for all SDK components to download (this may take 10-20 minutes)
   - Click "Finish" when complete

### 2. Accept Android SDK Licenses

After setup wizard completes:

```powershell
cd C:\Users\marlobell\AppData\Local\Android\Sdk\cmdline-tools\latest\bin
.\sdkmanager.bat --licenses
```

Press 'y' and Enter for each license.

### 3. Open the GHCV Project

1. In Android Studio, click "Open"
2. Navigate to: `C:\Users\marlobell\Git\ghcv`
3. Click "OK"
4. Wait for Gradle sync to complete

## 🔧 Working with the Project

### Quick Setup (Each Terminal Session)

Before running Gradle commands, set up your environment:

```powershell
.\setup-windows.ps1
```

This script sets `JAVA_HOME` and `ANDROID_HOME` for your current terminal session.

### Common Commands

```powershell
# Build the project
.\gradlew.bat build

# Clean build artifacts
.\gradlew.bat clean

# List all tasks
.\gradlew.bat tasks

# Install debug APK to connected device
.\gradlew.bat installDebug

# Run tests
.\gradlew.bat test
```

### Using Android Studio (Recommended)

1. **Open Project:** File → Open → Select `C:\Users\marlobell\Git\ghcv`
2. **Sync Gradle:** File → Sync Project with Gradle Files
3. **Build:** Build → Make Project (Ctrl+F9)
4. **Run on Emulator:** Run → Run 'app' (Shift+F10)

## 📱 Testing on Emulator

See [TESTING.md](TESTING.md) for detailed instructions on:
- Creating an Android Virtual Device (AVD)
- Installing Health Connect
- Running the app
- Adding sample health data

## 🔍 Troubleshooting

### "JAVA_HOME is not set"

Run the setup script first:
```powershell
.\setup-windows.ps1
```

### "Android SDK not found"

1. Open Android Studio
2. Go to File → Settings → Appearance & Behavior → System Settings → Android SDK
3. Verify SDK location is: `C:\Users\marlobell\AppData\Local\Android\Sdk`
4. Install required SDK versions (API 34-35)

### "Gradle sync failed"

1. In Android Studio: File → Invalidate Caches / Restart
2. Or from command line:
   ```powershell
   .\gradlew.bat clean
   .\gradlew.bat build --refresh-dependencies
   ```

### Slow Gradle Builds

Edit `gradle.properties` and add:
```properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
```

## 🌍 Differences from Linux

The project was originally created on Linux but is fully compatible with Windows:

- ✅ Use `gradlew.bat` instead of `./gradlew`
- ✅ Paths use backslashes (`\`) instead of forward slashes
- ✅ Environment variables set via PowerShell instead of bash
- ✅ Same Android Studio experience across platforms

## 📚 Additional Resources

- [Android Studio User Guide](https://developer.android.com/studio/intro)
- [Gradle on Windows](https://docs.gradle.org/current/userguide/installation.html#installing_on_windows)
- [Health Connect Documentation](https://developer.android.com/health-and-fitness/guides/health-connect)

## 🎯 Next Steps

1. ✅ Complete Android Studio setup wizard
2. ✅ Accept SDK licenses
3. ✅ Open project in Android Studio
4. ✅ Wait for Gradle sync
5. ✅ Create an AVD (Android Virtual Device)
6. ✅ Run the app!

---

**Installation Date:** 2026-02-05  
**Android Studio Version:** 2024.2.1.12  
**Java Version:** OpenJDK 21.0.3
