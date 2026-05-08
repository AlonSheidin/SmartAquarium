package com.example.smartaquarium.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.example.smartaquarium.data.datasource.FirestoreDataSource;
import com.example.smartaquarium.data.model.AquariumData;
import com.example.smartaquarium.utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;

/**
 * JobService responsible for periodically checking aquarium data against user-defined thresholds
 * and sending notifications (alerts) if any parameters are out of range.
 * This service runs in the background, independent of the main application's lifecycle,
 * ensuring continuous monitoring.
 */
public class AquariumAlertJobService extends JobService {

    private static final String TAG = "AquariumAlertJob";
    private FirestoreDataSource dataSource;
    private NotificationHelper notificationHelper;
    private FirebaseFirestore database;

    /**
     * Called when the system determines that it is time to run the job.
     * This method is executed on the application's main thread.
     *
     * @param params The parameters associated with the job.
     * @return {@code true} if the job needs to continue running on a separate thread (asynchronous work),
     *         {@code false} if the job is finished.
     */
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob: System triggered the background check.");

        init();

        // Start the alert check logic on a background thread as Firestore operations are asynchronous.
        // We use jobFinished(params, false) later when the async work is complete.
        checkAquariumLimits(params);

        // Return true because we are performing asynchronous Firestore calls
        return true;
    }

    /**
     * Initializes dependencies for the job execution if they haven't been initialized yet.
     * This ensures that {@link FirestoreDataSource}, {@link NotificationHelper}, and {@link FirebaseFirestore}
     * instances are available when needed.
     */
    private void init() {
        if (dataSource == null) {
            dataSource = new FirestoreDataSource();
        }
        if (notificationHelper == null) {
            notificationHelper = new NotificationHelper(this);
        }
        if (database == null) {
            database = FirebaseFirestore.getInstance();
        }
    }

    /**
     * Initiates the process of checking aquarium limits.
     * It first fetches user settings (thresholds) from Firestore and then proceeds
     * to fetch and check each aquarium associated with the authenticated user.
     *
     * @param params The JobParameters passed to {@link #onStartJob(JobParameters)}.
     */
    private void checkAquariumLimits(JobParameters params) {
        String userId = FirebaseAuth.getInstance().getUid();

        if (userId == null) {
            Log.e(TAG, "User not authenticated. Stopping job.");
            jobFinished(params, false); // Job is finished, no reschedule
            return;
        }

        // 1. Fetch User Settings (Thresholds) from Firestore
        database.collection("users").document(userId)
                .collection("settings").document("userSettings")
                .get()
                .addOnSuccessListener(settingsSnapshot -> {
                    if (!settingsSnapshot.exists()) {
                        Log.w(TAG, "Settings document 'userSettings' does not exist.");
                        jobFinished(params, false); // No settings, job finished
                        return;
                    }

                    // Retrieve all threshold values.
                    Double maxTemperature = settingsSnapshot.getDouble("maxTemperature");
                    Double minTemperature = settingsSnapshot.getDouble("minTemperature");
                    Double maxPh = settingsSnapshot.getDouble("maxPh");
                    Double minPh = settingsSnapshot.getDouble("minPh");
                    Double maxOxygen = settingsSnapshot.getDouble("maxOxygen");
                    Double minOxygen = settingsSnapshot.getDouble("minOxygen");
                    Double maxWaterLevel = settingsSnapshot.getDouble("maxWaterLevel");
                    Double minWaterLevel = settingsSnapshot.getDouble("minWaterLevel");

                    // Check if any critical thresholds are missing.
                    if (maxTemperature == null || minTemperature == null || maxPh == null || minPh == null ||
                        maxOxygen == null || minOxygen == null || maxWaterLevel == null || minWaterLevel == null) {
                        Log.e(TAG, "Aquarium thresholds are missing in Firestore. Cannot perform checks.");
                        jobFinished(params, false); // Missing thresholds, job finished
                        return;
                    }

                    // 2. Fetch all aquariums for the user and proceed to check each one.
                    fetchAndCheckAquariums(userId, maxTemperature, minTemperature, maxPh, minPh,
                            maxOxygen, minOxygen, maxWaterLevel, minWaterLevel, params);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch settings: " + e.getMessage());
                    jobFinished(params, true); // Reschedule on failure to fetch settings
                });
    }

    /**
     * Fetches all aquariums belonging to a specific user and then processes their latest data.
     *
     * @param userId The ID of the authenticated user.
     * @param maxT The maximum allowed temperature.
     * @param minT The minimum allowed temperature.
     * @param maxPh The maximum allowed pH.
     * @param minPh The minimum allowed pH.
     * @param maxOxygen The maximum allowed oxygen level.
     * @param minOxygen The minimum allowed oxygen level.
     * @param maxWaterLevel The maximum allowed water level.
     * @param minWaterLevel The minimum allowed water level.
     * @param params The JobParameters passed to {@link #onStartJob(JobParameters)}.
     */
    private void fetchAndCheckAquariums(String userId, double maxT, double minT, double maxPh, double minPh,
                                        double maxOxygen, double minOxygen, double maxWaterLevel, double minWaterLevel,
                                        JobParameters params) {
        database.collection("users").document(userId)
                .collection("aquariums")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DocumentSnapshot> aquariumDocuments = querySnapshot.getDocuments();

                    if (aquariumDocuments.isEmpty()) {
                        Log.d(TAG, "No aquariums found for user: " + userId + ". Job finished.");
                        jobFinished(params, false); // No aquariums, job finished
                        return;
                    }

                    // For each aquarium, get the latest reading and process it.
                    for (DocumentSnapshot doc : aquariumDocuments) {
                        processLatestTankData(userId, doc.getId(), maxT, minT, maxPh, minPh,
                                maxOxygen, minOxygen, maxWaterLevel, minWaterLevel);
                    }

                    // We notify the system that the work for fetching aquariums is complete.
                    // Individual data processing for each tank is asynchronous and will trigger
                    // notifications if needed.
                    jobFinished(params, false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch aquariums for user: " + userId + ": " + e.getMessage());
                    jobFinished(params, true); // Reschedule on failure to fetch aquariums
                });
    }

    /**
     * Fetches the latest sensor data for a specific aquarium and validates it against thresholds.
     * Separate notifications are sent for each parameter that is out of range.
     *
     * @param userId The ID of the authenticated user.
     * @param aquariumId The ID of the specific aquarium to process.
     * @param maxT The maximum allowed temperature.
     * @param minT The minimum allowed temperature.
     * @param maxPh The maximum allowed pH.
     * @param minPh The minimum allowed pH.
     * @param maxOxygen The maximum allowed oxygen level.
     * @param minOxygen The minimum allowed oxygen level.
     * @param maxWaterLevel The maximum allowed water level.
     * @param minWaterLevel The minimum allowed water level.
     */
    private void processLatestTankData(String userId, String aquariumId, double maxT, double minT,
                                       double maxPh, double minPh, double maxOxygen, double minOxygen,
                                       double maxWaterLevel, double minWaterLevel) {
        database.collection("users").document(userId)
                .collection("aquariums").document(aquariumId)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1) // Get only the latest reading
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        AquariumData latestReading = querySnapshot.getDocuments().get(0).toObject(AquariumData.class);
                        if (latestReading != null) {
                            // Validate each parameter individually and send separate alerts.
                            validateTemperature(aquariumId, latestReading, maxT, minT);
                            validatePh(aquariumId, latestReading, maxPh, minPh);
                            validateOxygen(aquariumId, latestReading, maxOxygen, minOxygen);
                            validateWaterLevel(aquariumId, latestReading, maxWaterLevel, minWaterLevel);
                        }
                    } else {
                        Log.d(TAG, "No history data found for aquarium: " + aquariumId);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch latest data for aquarium " + aquariumId + ": " + e.getMessage()));
    }

    /**
     * Validates the temperature reading of an aquarium against user-defined thresholds.
     * Sends a notification if the temperature is outside the acceptable range.
     *
     * @param tankName The name of the aquarium.
     * @param data The latest {@link AquariumData} object.
     * @param max The maximum acceptable temperature.
     * @param min The minimum acceptable temperature.
     */
    private void validateTemperature(String tankName, AquariumData data, double max, double min) {
        double currentTemp = data.getTemperature();
        Log.d(TAG, "Validating " + tankName + ": " + currentTemp + "°C (Limits: " + min + "-" + max + ")");

        if (currentTemp > max) {
            notificationHelper.sendAlert("High Temperature Alert!",
                    tankName + " is at " + currentTemp + "°C (Max limit: " + max + "°C)");
        } else if (currentTemp < min) {
            notificationHelper.sendAlert("Low Temperature Alert!",
                    tankName + " is at " + currentTemp + "°C (Min limit: " + min + "°C)");
        }
    }

    /**
     * Validates the pH reading of an aquarium against user-defined thresholds.
     * Sends a notification if the pH is outside the acceptable range.
     *
     * @param tankName The name of the aquarium.
     * @param data The latest {@link AquariumData} object.
     * @param max The maximum acceptable pH.
     * @param min The minimum acceptable pH.
     */
    private void validatePh(String tankName, AquariumData data, double max, double min) {
        double currentPh = data.getPh();
        Log.d(TAG, "Validating " + tankName + ": " + currentPh + " (Limits: " + min + "-" + max + ")");

        if (currentPh > max) {
            notificationHelper.sendAlert("High pH Alert!",
                    tankName + " is at " + currentPh + " (Max limit: " + max + ")");
        } else if (currentPh < min) {
            notificationHelper.sendAlert("Low pH Alert!",
                    tankName + " is at " + currentPh + " (Min limit: " + min + ")");
        }
    }




    /**
     * Validates the oxygen level reading of an aquarium against user-defined thresholds.
     * Sends a notification if the oxygen level is outside the acceptable range.
     *
     * @param tankName The name of the aquarium.
     * @param data The latest {@link AquariumData} object.
     * @param max The maximum acceptable oxygen level.
     * @param min The minimum acceptable oxygen level.
     */
    private void validateOxygen(String tankName, AquariumData data, double max, double min) {
        double currentOxygen = data.getOxygen();
        Log.d(TAG, "Validating " + tankName + ": " + currentOxygen + " (Limits: " + min + "-" + max + ")");

        if (currentOxygen > max) {
            notificationHelper.sendAlert("High Oxygen Alert!",
                    tankName + " is at " + currentOxygen + " (Max limit: " + max + ") ");
        } else if (currentOxygen < min) {
            notificationHelper.sendAlert("Low Oxygen Alert!",
                    tankName + " is at " + currentOxygen + " (Min limit: " + min + ")");
        }
    }

    /**
     * Validates the water level reading of an aquarium against user-defined thresholds.
     * Sends a notification if the water level is outside the acceptable range.
     *
     * @param tankName The name of the aquarium.
     * @param data The latest {@link AquariumData} object.
     * @param max The maximum acceptable water level.
     * @param min The minimum acceptable water level.
     */
    private void validateWaterLevel(String tankName, AquariumData data, double max, double min) {
        double currentWaterLevel = data.getWaterLevel();
        Log.d(TAG, "Validating " + tankName + ": " + currentWaterLevel + " (Limits: " + min + "-" + max + ")");

        if (currentWaterLevel > max) {
            notificationHelper.sendAlert("High Water Level Alert!",
                    tankName + " is at " + currentWaterLevel + " (Max limit: " + max + ")");
        } else if (currentWaterLevel < min) {
            notificationHelper.sendAlert("Low Water Level Alert!",
                    tankName + " is at " + currentWaterLevel + " (Min limit: " + min + ")");
        }
    }

    /**
     * Called when the system has determined that you must stop running the job,
     * most likely because the constraints under which the job was running are no longer satisfied.
     *
     * @param params The parameters associated with the job.
     * @return {@code true} if the job should be rescheduled later, {@code false} if the job should be dropped.
     */
    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job stopped by system.");
        return true; // Reschedule if the job was interrupted
    }
}
