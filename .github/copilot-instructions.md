# GHCV - Google Health Connect Visualizer

An Android app built with Jetpack Compose that visualizes health data from Google Health Connect API.

## Development Standards

**All code must be vetted against:**
- **Official Android SDK guidance** - Follow [developer.android.com](https://developer.android.com) documentation for APIs, best practices, and recommended patterns
- **Official Google UX designs** - Adhere to [Material Design 3 guidelines](https://m3.material.io/) for UI/UX consistency
- **Well-established patterns and libraries** - Use Google-recommended Jetpack libraries (Compose, Navigation, Lifecycle) and established Android architecture patterns (MVVM, Repository pattern)

When suggesting code changes:
- Verify patterns match current Android best practices
- Reference official documentation when introducing new APIs or patterns
- Prefer Jetpack Compose Material3 components over custom implementations
- Follow Health Connect API guidelines from official Google documentation

## Build & Test Commands

### Building
```bash
# Windows
.\gradlew.bat build

# Linux/Mac
./gradlew build
```

### Testing
```bash
# Run all tests
.\gradlew.bat test

# Run instrumented tests (requires emulator/device)
.\gradlew.bat connectedAndroidTest

# Install debug APK
.\gradlew.bat installDebug
```

### Windows-Specific Setup
Before running Gradle commands in a new terminal session, run:
```powershell
.\setup-windows.ps1
```
This sets `JAVA_HOME` and `ANDROID_HOME` environment variables.

## Architecture

### MVVM Pattern
- **Models**: `data/model/` - Sealed `HealthMetric` class with type-safe metric implementations
- **ViewModels**: `ui/viewmodel/` - One per screen (Current, Historical, Trends)
- **Views**: `ui/screens/` - Jetpack Compose screens with Material3

### Data Flow
1. **HealthConnectManager** - Manages permissions and Health Connect client initialization
2. **HealthConnectRepository** - Reads health data via Health Connect API
3. **ViewModels** - Expose `StateFlow<T>` for UI state; handle business logic
4. **Screens** - Compose UI that collects state and reacts to changes

### Key Components

**HealthConnectManager** (`data/HealthConnectManager.kt`)
- Central permission management (20+ health permissions defined in companion object)
- Health Connect availability checks
- Single source for HealthConnectClient instance

**HealthConnectRepository** (`data/repository/HealthConnectRepository.kt`)
- All Health Connect API read operations
- Date-based queries using `TimeRangeFilter`
- Data aggregation (steps totals, heart rate averages, sleep analysis)

**HealthMetric sealed class** (`data/model/HealthMetric.kt`)
- Type-safe wrapper for all health metrics
- Each metric has: `timestamp`, `value`, `unit`
- Includes `VitalStats<T>` for aggregated statistics

## Key Conventions

### ViewModels
- Must accept both `HealthConnectRepository` and `HealthConnectManager` in constructor
- Expose UI state via `StateFlow<T>` (never `MutableStateFlow` directly)
- Always have a `checkPermissions()` method that updates `_hasPermissions` flow
- Use `viewModelScope.launch` for coroutines
- **Use individual try-catch blocks for each data fetch** to prevent one failure from blocking all data
- Log failures with `Log.w()` for debugging, but don't propagate errors to UI state
- Example pattern:
```kotlin
private val _uiState = MutableStateFlow(MyData())
val uiState: StateFlow<MyData> = _uiState.asStateFlow()

private val _hasPermissions = MutableStateFlow(false)
val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

// Individual error handling prevents cascading failures
val steps = try {
    repository.getTodaySteps()
} catch (e: Exception) {
    Log.w("MyViewModel", "Failed to fetch steps", e)
    0L
}
```

### Compose Screens
- Create ViewModels using custom factory that injects dependencies:
```kotlin
val viewModel: MyViewModel = viewModel(
    factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MyViewModel(repository, healthConnectManager) as T
        }
    }
)
```
- Use `collectAsState()` to observe StateFlows
- Create repository instance with `remember { HealthConnectRepository(healthConnectManager.getClient()) }`

### Health Connect Queries
- Use `TimeRangeFilter.between(startInstant, endInstant)` for date ranges
- Convert `LocalDate` to `Instant` via: `date.atStartOfDay(ZoneId.systemDefault()).toInstant()`
- Add one day: `startOfDay.plus(1, ChronoUnit.DAYS)`
- All Health Connect operations are suspend functions
- **Use aggregation APIs for cumulative data** (steps, calories) to avoid double-counting from multiple sources
  - Example: `healthConnectClient.aggregate()` with `StepsRecord.COUNT_TOTAL`
  - Aggregation handles deduplication and data source prioritization automatically
  - Use `readRecords()` only for time-series data (heart rate samples, sleep stages)

### Time Handling
- Always use `java.time.*` (not java.util.Date)
- Store timestamps as `Instant`
- Display dates as `LocalDate`
- Use `ZoneId.systemDefault()` for conversions

### Material3 Theming
- All UI uses Material3 (`androidx.compose.material3`)
- Theme defined in `ui/theme/Theme.kt`
- Use `MaterialTheme.colorScheme` for colors
- Icons from `androidx.compose.material.icons.filled` and `automirrored` variants

### Chart Library (Vico)
- Library: v2.0.0-alpha.28
- Use column charts for discrete metrics (steps, sleep)
- Use line charts for continuous metrics (heart rate)
- Chart setup is complex - refer to existing implementations in TrendsScreen.kt

### Navigation
- Bottom navigation with three tabs: Current, Historical, Trends
- Screen definitions in `ui/navigation/Screen.kt`
- Use `navController.navigate(route)` with string routes

## Testing Environment

### Requirements
- Minimum SDK: 34 (Android 14)
- Target SDK: 35 (Android 15)
- Health Connect must be installed on device/emulator
- Permissions must be granted via Health Connect UI

### Adding Test Data
See TESTING.md for detailed instructions on:
- Creating AVD with API 34+
- Installing Health Connect
- Adding sample health data
- Troubleshooting common issues

### Common Test Commands
```bash
# Clear app data
adb shell pm clear com.marlobell.ghcv

# View logs
adb logcat | grep "GHCV"

# Uninstall
adb uninstall com.marlobell.ghcv
```

## Adding New Health Metrics

1. **Add permission** to `HealthConnectManager.PERMISSIONS` set
2. **Create metric model** in `HealthMetric.kt` extending sealed class
3. **Add repository method** to fetch data in `HealthConnectRepository.kt`
4. **Update ViewModel** to include new metric in UI state
5. **Add UI component** in corresponding screen

Example: All existing metrics (steps, heart rate, sleep, etc.) follow this pattern.

## Performance Notes

- **Auto-refresh**: CurrentViewModel refreshes every 60 seconds
- **Pagination**: Not implemented - queries load full date ranges
- **Caching**: No caching - data fetched fresh on each query
- **Loading states**: Always show loading indicator during data fetch

## Gradle Configuration

- Uses Kotlin DSL (`.kts` files)
- Version catalogs in `gradle/libs.versions.toml`
- Gradle version: 9.3.1
- Android Gradle Plugin (AGP): 9.0.0 with built-in Kotlin support
- Kotlin version: 2.3.10 (updated Feb 2026)
- Uses new Kotlin `compilerOptions` DSL (not deprecated `kotlinOptions`)
- Java target: 11
- **Note:** AGP 9.0+ has built-in Kotlin support - do NOT add `org.jetbrains.kotlin.android` plugin

## File Naming
- Activities: `*Activity.kt`
- Screens: `*Screen.kt`
- ViewModels: `*ViewModel.kt`
- Models: No suffix, just descriptive names
- Repositories: `*Repository.kt`
