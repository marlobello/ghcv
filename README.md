# Google Health Connect Visualizer (GHCV)

A modern Android application that visualizes your health data from Google Health Connect with beautiful charts and comprehensive analytics.

## 📱 Features

### Current Screen - Real-Time Health Dashboard
- **Auto-Refresh**: Data automatically updates every 60 seconds
- **Today's Metrics**: Steps, heart rate, active calories, and sleep from last night
- **Trend Indicators**: Compare today's steps to yesterday with percentage changes
- **Live Timestamps**: See when each metric was last measured
- **Material3 Design**: Beautiful cards with icons and color-coded information

### Historical Screen - Detailed Daily Analysis
- **Date Picker**: Select any day to view historical data
- **Expandable Cards**: Tap metrics to see detailed breakdowns
- **Heart Rate Chart**: Interactive time-series visualization of intraday heart rate
- **Sleep Analysis**: Complete sleep session with stage breakdown (Deep, Light, REM, Awake)
- **Statistics**: Min/Max/Average calculations for heart rate
- **Comparisons**: Compare steps to the previous day
- **Additional Metrics**: Distance traveled and exercise sessions when available

### Trends Screen - Long-Term Pattern Analysis
- **Multi-Metric Views**: Switch between Steps, Heart Rate, and Sleep trends
- **Multiple Time Periods**: Week, 2 Weeks, Month, or 3 Months
- **Professional Charts**: 
  - Column charts for steps and sleep (discrete daily values)
  - Line charts for heart rate (continuous trends)
- **Comprehensive Statistics**:
  - Steps: Average, Total, Peak
  - Heart Rate: Min, Average, Max (bpm)
  - Sleep: Average per night, Total hours
- **Empty States**: Helpful messages when no data is available

## 🛠️ Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Data Source**: Google Health Connect API
- **Charts**: Vico Chart Library (v2.0.0-alpha.28)
- **Minimum SDK**: Android 14 (API 34)
- **Target SDK**: Android 15 (API 35)

## 📊 Supported Health Metrics

### Primary Metrics (Fully Implemented)
- ✅ Steps
- ✅ Heart Rate (with intraday tracking)
- ✅ Sleep (with stage analysis)
- ✅ Active Calories Burned

### Additional Metrics (Basic Support)
- Distance
- Exercise Sessions

### Ready for Extension
The app architecture supports easy addition of:
- Blood Pressure
- Weight
- Body Temperature
- Oxygen Saturation
- Blood Glucose
- Resting Heart Rate
- And 15+ more Health Connect data types

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android device or emulator running Android 14+
- Google Health Connect installed on the device

### Building the App
1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/ghcv.git
   cd ghcv
   ```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Build and run:
   ```bash
   ./gradlew build
   ```

### Required Permissions
The app requires read permissions for health data. On first launch, users will be prompted to grant permissions through the Health Connect interface.

## 📁 Project Structure

```
app/src/main/java/com/marlobell/ghcv/
├── data/
│   ├── model/              # Data models for health metrics
│   ├── repository/         # Data access layer
│   └── HealthConnectManager.kt
├── ui/
│   ├── screens/            # Composable screens
│   │   ├── CurrentScreen.kt
│   │   ├── HistoricalScreen.kt
│   │   └── TrendsScreen.kt
│   ├── viewmodel/          # ViewModels for each screen
│   ├── navigation/         # Navigation setup
│   └── theme/              # Material3 theming
└── MainActivity.kt
```

## 🎨 Design Philosophy

- **Material3 First**: Modern Material Design 3 guidelines throughout
- **Data-Driven**: Only show metrics when data is available
- **Progressive Disclosure**: Use expandable cards to show details on demand
- **Visual Clarity**: Charts and statistics designed for quick comprehension
- **Responsive**: Adapts to different screen sizes and orientations

## 🔐 Privacy & Data

- **Read-Only**: The app only reads data from Health Connect, never writes
- **No Cloud Storage**: All data remains on your device
- **Permissions-Based**: Users control exactly what data the app can access
- **Transparent**: Open source code for full transparency

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Guidelines
- Follow Kotlin coding conventions
- Use Jetpack Compose for all UI
- Maintain MVVM architecture
- Write meaningful commit messages
- Test on physical devices with Health Connect

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- Google Health Connect team for the excellent health data API
- [Vico](https://github.com/patrykandpatrick/vico) for the beautiful charting library
- Material Design team for the design system

## 📧 Contact

For questions or feedback, please open an issue on GitHub.

---

**Note**: This app requires Google Health Connect to be installed on your Android device. Health Connect is available on Android 14+ devices.
