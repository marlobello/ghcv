# Testing Guide - Running GHCV in Android Emulator

## Prerequisites

- Android Studio Hedgehog or later installed
- Project opened in Android Studio
- At least 8GB RAM available for the emulator

## Step 1: Create an Android Virtual Device (AVD)

### Using Android Studio UI:

1. **Open AVD Manager:**
   - Click **Tools** → **Device Manager** (or click the device icon in the toolbar)
   - Or use shortcut: `Ctrl+Shift+A` (Windows/Linux) or `Cmd+Shift+A` (Mac), type "Device Manager"

2. **Create New Virtual Device:**
   - Click **"Create Device"** button
   - Select **Phone** category
   - Choose a device with Play Store support (e.g., **Pixel 8** or **Pixel 7**)
   - Click **Next**

3. **Select System Image:**
   - **Important:** Select **API Level 34 (Android 14)** or higher (the app requires minSdk 34)
   - Choose the **x86_64** or **ARM64** variant with Google APIs
   - Click **Download** if needed (this may take several minutes)
   - Click **Next**

4. **Configure AVD:**
   - Give it a name (e.g., "Pixel 8 API 34")
   - Under **Emulated Performance**, select **Hardware - GLES 2.0** for better performance
   - Click **Show Advanced Settings** (optional):
     - Increase RAM to 4096 MB or higher
     - Enable **Device Frame** for realistic appearance
   - Click **Finish**

## Step 2: Start the Emulator

1. **Launch Emulator:**
   - In Device Manager, click the **Play** button (▶) next to your AVD
   - Wait for the emulator to fully boot (may take 1-2 minutes on first launch)
   - You should see the Android home screen

2. **Verify Emulator is Running:**
   - The emulator should show up in Android Studio's device dropdown (top toolbar)

## Step 3: Install Health Connect

Health Connect is required for this app to work. Here are the installation methods:

### Method A: Via Play Store (Recommended)

1. Open **Play Store** in the emulator
2. Sign in with a Google account (create one if needed)
3. Search for **"Health Connect"**
4. Install the official Health Connect app by Google
5. Open Health Connect and grant necessary permissions

### Method B: Via ADB (If Play Store unavailable)

```bash
# Download Health Connect APK (from APKMirror or similar trusted source)
# Then install via ADB:
adb install health-connect.apk
```

### Method C: Using a Physical Device (Easiest for Testing)

If you have an Android 14+ physical device:
- Simply enable Developer Options
- Enable USB Debugging
- Connect via USB
- Android Studio will detect it automatically

## Step 4: Run the App

1. **Build and Run:**
   - Click the **Run** button (▶) in Android Studio toolbar
   - Or press `Shift+F10` (Windows/Linux) or `Control+R` (Mac)
   - Or use menu: **Run** → **Run 'app'**

2. **Select Deployment Target:**
   - Choose your emulator from the list
   - Click **OK**

3. **Wait for Build:**
   - Gradle will build the app (first build may take a few minutes)
   - The app will install and launch automatically

## Step 5: Grant Health Connect Permissions

When the app first launches:

1. **Health Connect Availability Check:**
   - If Health Connect is not installed, you'll see an error message
   - Install it following Step 3 above

2. **Grant Permissions:**
   - The app will show "Permissions Required" screen
   - Click **"Grant Permissions"**
   - Health Connect permission dialog will appear
   - Toggle ON all the health data types:
     - Steps
     - Heart Rate
     - Sleep
     - Calories
     - Distance
     - Exercise
   - Click **"Allow"**

## Step 6: Add Sample Health Data

Since the emulator won't have real health data, you need to add sample data:

### Option 1: Use Health Connect Test App

1. Install Google's Health Connect Sample App:
   ```bash
   git clone https://github.com/android/health-samples
   cd health-samples/health-connect
   # Open in Android Studio and run
   ```

2. Use the sample app to insert test data

### Option 2: Manually Add Data via Health Connect App

1. Open **Health Connect** app
2. Go to **Browse data**
3. Select data type (Steps, Heart Rate, etc.)
4. Click **Add data** button
5. Enter sample values and dates

### Option 3: Use ADB Commands to Insert Data

Health Connect stores data in a database. You can use the Health Connect API to insert test data programmatically.

## Step 7: Test the App Features

### Testing Current Screen:
1. Open the app
2. Navigate to **Current** tab (should be default)
3. Verify it shows:
   - Today's steps
   - Latest heart rate (if added)
   - Sleep from last night
   - Active calories
   - Last updated timestamp
4. Wait 60 seconds to test auto-refresh
5. Click refresh button manually

### Testing Historical Screen:
1. Navigate to **Historical** tab
2. Click the date selector card
3. Pick a date where you added sample data
4. Verify it shows:
   - Steps with comparison to previous day
   - Heart rate chart with expand/collapse
   - Sleep details with stages
   - Distance and exercise (if added)

### Testing Trends Screen:
1. Navigate to **Trends** tab
2. Test period selectors:
   - Click **Week**, **2 Weeks**, **Month**, **3 Months**
   - Verify charts update
3. Test metric selectors:
   - Click **Steps**, **Heart Rate**, **Sleep**
   - Verify different charts appear
4. Verify statistics display correctly

## Troubleshooting

### App Won't Install
- **Error:** "Installation failed with message Failed to finalize session"
  - **Solution:** Uninstall any previous version: `adb uninstall com.marlobell.ghcv`

### Health Connect Not Available
- **Error:** "Health Connect Not Available"
  - **Solution:** Verify API level is 34+ and Health Connect is installed
  - Check: Settings → Apps → See all apps → Health Connect

### No Data Showing
- **Error:** All metrics show 0 or empty
  - **Solution:** Add sample data via Health Connect app
  - Verify permissions were granted

### Emulator Performance Issues
- **Slow/Laggy Emulator:**
  - Allocate more RAM in AVD settings (4GB+)
  - Enable hardware acceleration
  - Use x86_64 system image instead of ARM
  - Close other applications

### Build Errors
- **Gradle sync failed:**
  - Click **File** → **Sync Project with Gradle Files**
  - Click **Build** → **Clean Project**
  - Click **Build** → **Rebuild Project**

## Quick Command Reference

```bash
# List running emulators
adb devices

# Install app manually
./gradlew installDebug

# View app logs
adb logcat | grep "ghcv"

# Clear app data (reset)
adb shell pm clear com.marlobell.ghcv

# Uninstall app
adb uninstall com.marlobell.ghcv

# Take screenshot
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png

# Record screen video
adb shell screenrecord /sdcard/demo.mp4
# Stop with Ctrl+C, then:
adb pull /sdcard/demo.mp4
```

## Performance Tips

1. **Enable Hardware Acceleration:**
   - Ensure Intel HAXM (Intel) or WHPX (Windows) is installed
   - Settings → Appearance → System Settings → Android SDK → SDK Tools

2. **Use Instant Run:**
   - Settings → Build → Instant Run (Apply Changes without reinstall)

3. **Keep Emulator Running:**
   - Don't close the emulator between runs
   - Significantly faster subsequent builds

4. **Use Cold Boot Once:**
   - First launch: Cold Boot
   - Subsequent runs: Quick Boot (saves emulator state)

## Recommended Testing Workflow

1. **One-time Setup:**
   - Create emulator with API 34
   - Install Health Connect
   - Add sample data for multiple days

2. **Daily Development:**
   - Keep emulator running
   - Make code changes
   - Hit Run (Shift+F10)
   - Test specific features
   - Use logcat for debugging

3. **Before Commits:**
   - Test all three screens
   - Verify all interactions work
   - Check different data scenarios (empty, partial, full)
   - Test error states

## Testing with Real Device (Recommended)

For the best testing experience:

1. **Setup:**
   - Use Android 14+ phone with Health Connect installed
   - Enable Developer Options: Settings → About Phone → Tap Build Number 7 times
   - Enable USB Debugging: Settings → Developer Options → USB Debugging

2. **Connect:**
   - Connect phone via USB
   - Accept debugging prompt on phone
   - Phone appears in Android Studio device list

3. **Advantages:**
   - Real sensor data (steps, heart rate from wearables)
   - Actual Health Connect integration
   - Better performance
   - True user experience

4. **Sync Health Data:**
   - If you have a fitness tracker/smartwatch, sync it
   - Use Google Fit or other health apps to populate data
   - Your real health data will appear in the app!

## Next Steps

Once the app is running:
- Explore all three screens
- Test with different date ranges
- Verify charts render correctly
- Check expandable cards work
- Test period selection in Trends screen
- Verify auto-refresh on Current screen

Happy testing! 🚀
