# SmartAquarium Project - FishKeeper

## 1. Project Overview
SmartAquarium is an Android application designed to monitor and manage aquarium data. It provides real-time data display, historical data analytics, and user-customizable alert settings. The application utilizes Firebase for user authentication, data storage (Firestore), and potentially real-time database functionalities. It also incorporates background services for periodic data monitoring.

## 2. Core Functionalities
*   **Aquarium Data Monitoring:** Displays real-time and historical data for temperature, pH, oxygen, and water level.
*   **User Settings Management:** Allows users to define thresholds for alerts (min/max for various parameters) and configure "Do Not Disturb" hours and contact information.
*   **Data Visualization:** Presents historical data in chart format.
*   **Background Alerting:** Schedules periodic jobs to check aquarium parameters against user-defined thresholds.
*   **Aquarium Management:** Users can add and select different aquariums.

## 3. Key Components/Architecture

### Data Layer
*   **`AquariumData.java`**: POJO representing a single snapshot of aquarium sensor readings (temperature, pH, oxygen, water level, timestamp). Used for data exchange and storage.
*   **`UserSettings.java`**: POJO representing user-specific alert thresholds, DND settings, and contact info. Mapped directly to Firestore documents.
*   **`FirestoreDataSource.java` (inferred)**: Handles interactions with Firestore for `UserSettings` and likely `AquariumData`. Responsible for reading and writing data to the cloud.

### Service Layer
*   **`DummyConnection.java`**: A simulated data source implementing `IConnection`. Generates random aquarium data periodically on a background thread and notifies registered listeners on the UI thread. Essential for testing without physical hardware.
*   **`UserSettingsService.java`**: Business logic for user settings. Interacts with `FirestoreDataSource` and `FirebaseAuth` to retrieve and save user settings, including validation.
*   **`AquariumJobScheduler.java`**: Utility class for scheduling and canceling `AquariumAlertJobService` (inferred) to perform background monitoring tasks using Android's JobScheduler.

### UI Layer (Fragments)
*   **`DashboardFragment.java`**: The main screen displaying current aquarium data, connection status, and controls for adding/selecting aquariums. Observes `AquariumDataViewModel` for updates.
*   **`AnalyticsFragment.java`**: Displays historical aquarium data using charts. Allows users to select data types (Temperature, pH, Oxygen, Water Level) and time filters (Last 24 Hours, Last 7 Days, etc.). Interacts with `AnalyticsViewModel`.

### ViewModel Layer
*   **`AquariumDataViewModel` (inferred)**: Provides `LiveData` for the latest aquarium data and a list of available aquariums. Manages the selected aquarium.
*   **`AnalyticsViewModel` (inferred)**: Provides `LiveData` for processed chart data based on selected `DataType` and `DateFilter`.

## 4. Important Files and Their Roles

*   **`F:/AndroidApps/SmartAquarium/app/src/main/java/com/example/smartaquarium/service/DummyConnection.java`**: Simulates aquarium data.
*   **`F:/AndroidApps/SmartAquarium/app/src/main/java/com/example/smartaquarium/service/AquariumJobScheduler.java`**: Schedules background jobs for alerts.
*   **`F:/AndroidApps/SmartAquarium/app/src/main/java/com/example/smartaquarium/data/model/UserSettings.java`**: Data model for user alert preferences.
*   **`F:/AndroidApps/SmartAquarium/app/src/main/java/com/example/smartaquarium/service/UserSettingsService.java`**: Handles user settings logic.
*   **`F:/AndroidApps/SmartAquarium/app/src/main/java/com/example/smartaquarium/ui/analyics/AnalyticsFragment.java`**: Displays historical data charts.
*   **`F:/AndroidApps/SmartAquarium/app/src/main/java/com/example/smartaquarium/ui/dashboard/DashboardFragment.java`**: Main dashboard for current data and aquarium selection.
*   **`F:/AndroidApps/SmartAquarium/app/src/main/java/com/example/smartaquarium/data/model/AquariumData.java`**: Core data model for aquarium readings.
*   **`F:/AndroidApps/SmartAquarium/app/build.gradle.kts`**: Gradle build script defining dependencies and project configuration.

## 5. Dependencies
*   **AndroidX Libraries**: `appcompat`, `constraintlayout`, `navigation-fragment`, `multidex`, `datastore`.
*   **Material Design**: `com.google.android.material:material`.
*   **Firebase**: `firebase-bom`, `firebase-auth`, `firebase-firestore`, `firebase-database`.
*   **Charting Library**: `com.github.PhilJay:MPAndroidChart`.
*   **Kotlin Stdlib**: `org.jetbrains.kotlin:kotlin-stdlib`.

## 6. Build System
The project uses Gradle as its build system. The `app/build.gradle.kts` file is the primary configuration file for the `:app` module. Building typically involves running Gradle tasks such as `assembleDebug` or `installDebug`.
