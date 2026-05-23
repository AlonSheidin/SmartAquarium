package com.example.smartaquarium.data.viewModel.aquariumData;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.smartaquarium.R;
import com.example.smartaquarium.data.datasource.FirestoreDataSource;
import com.example.smartaquarium.data.model.Aquarium;
import com.example.smartaquarium.data.model.AquariumData;
import com.example.smartaquarium.data.model.UserSettings;
import com.example.smartaquarium.service.AquariumJobScheduler;
import com.example.smartaquarium.service.UserSettingsService;
import com.example.smartaquarium.utils.enums.EnumConnectionStatus;
import com.example.smartaquarium.utils.interfaces.IConnection;
import com.example.smartaquarium.utils.interfaces.IDataListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for managing and providing aquarium-related data to the UI.
 * This class extends {@link AndroidViewModel} and implements {@link IDataListener}
 * to handle real-time updates from data sources. It is responsible for:
 * <ul>
 *     <li>Managing user authentication state.</li>
 *     <li>Fetching and exposing lists of available aquariums.</li>
 *     <li>Retrieving and exposing user-specific settings.</li>
 *     <li>Fetching and exposing historical aquarium data for a selected aquarium.</li>
 *     <li>Handling new incoming aquarium data and saving it to Firestore.</li>
 *     <li>Checking data against user-defined limits and sending local notifications or SMS alerts.</li>
 *     <li>Scheduling and cancelling the {@link AquariumJobScheduler} based on authentication status.</li>
 * </ul>
 */
public class AquariumDataViewModel extends AndroidViewModel implements IDataListener {

    /**
     * Constant representing an unauthenticated user ID.
     */
    private static final String NO_USER_ID = "UserNotLoggedIn";
    /**
     * The ID for the notification channel used for aquarium alerts.
     */
    private static final String CHANNEL_ID = "aquarium_alerts";
    /**
     * Tag for logging purposes.
     */
    private static final String TAG = "AquariumDataViewModel";

    /**
     * Data source for interacting with Firestore.
     */
    private final FirestoreDataSource firestoreDataSource;
    /**
     * Service for managing user settings.
     */
    private final UserSettingsService settingsService;

    // --- State ---
    /**
     * LiveData holding the currently authenticated user's ID.
     * Updates to this LiveData trigger changes in other data sources and job scheduling.
     */
    private final MutableLiveData<String> authenticatedUserId = new MutableLiveData<>();
    /**
     * LiveData holding the ID of the currently selected aquarium.
     * Changes to this LiveData trigger updates to the historical data stream.
     */
    private final MutableLiveData<String> selectedAquariumId = new MutableLiveData<>();

    // --- Observables ---
    /**
     * MediatorLiveData that combines sources to provide a list of aquariums available to the authenticated user.
     * Observes {@code authenticatedUserId}.
     */
    private final MediatorLiveData<List<Aquarium>> availableAquariums = new MediatorLiveData<>();
    /**
     * MediatorLiveData that combines sources to provide the user's settings.
     * Observes {@code authenticatedUserId}.
     */
    private final MediatorLiveData<UserSettings> userSettings = new MediatorLiveData<>();
    /**
     * MediatorLiveData that combines sources to provide the full history of a selected aquarium.
     * Observes both {@code authenticatedUserId} and {@code selectedAquariumId}.
     */
    private final MediatorLiveData<List<AquariumData>> fullHistory = new MediatorLiveData<>();
    /**
     * MutableLiveData holding the latest data point received for the selected aquarium.
     * Updated via the {@link #onNewData(AquariumData)} callback.
     */
    private final MutableLiveData<AquariumData> latestDataPoint = new MutableLiveData<>();
    /**
     * MutableLiveData holding the current connection status to external data sources.
     * Updated via the {@link #onConnectionStatusChanged(EnumConnectionStatus)} callback.
     */
    private final MutableLiveData<EnumConnectionStatus> connectionStatus = new MutableLiveData<>();

    // Private LiveData sources that are managed by MediatorLiveData.
    private LiveData<List<Aquarium>> currentAquariumsSource;
    private LiveData<UserSettings> currentSettingsSource;
    private LiveData<List<AquariumData>> currentHistorySource;

    /**
     * Constructs an {@link AquariumDataViewModel}.
     *
     * @param application The application context.
     */
    public AquariumDataViewModel(@NonNull Application application) {
        super(application);
        this.firestoreDataSource = new FirestoreDataSource();
        this.settingsService = new UserSettingsService();
        createNotificationChannel();
        init();
    }

    /**
     * Initializes the ViewModel's observers and sets up the data pipeline.
     * This method observes authentication state, user ID changes for aquarium lists and settings,
     * and a combination of user ID and selected aquarium ID for historical data.
     * It also schedules or cancels the {@link AquariumJobScheduler} based on login status.
     */
    private void init() {
        // --- Observe Authentication State ---
        // This observer will react to changes in authenticatedUserId.
        // It will schedule the job when a user logs in and cancel it when they log out.
        authenticatedUserId.observeForever(userId -> {
            Log.d(TAG, "Authentication state changed. User ID: " + userId);
            if (isValidUser(userId)) {
                // User is logged in, schedule the job
                AquariumJobScheduler.scheduleAquariumAlertJob(getApplication());
                Log.d(TAG, "Aquarium alert job scheduled for authenticated user.");
            } else {
                // User is logged out, cancel the job
                AquariumJobScheduler.cancelAquariumAlertJob(getApplication());
                Log.d(TAG, "Aquarium alert job cancelled for unauthenticated user.");
                // Clear selection and other user-specific state
                selectedAquariumId.setValue(null);
                latestDataPoint.setValue(null);
            }
        });

        // Observe user ID changes to update settings and aquarium list
        availableAquariums.addSource(authenticatedUserId, this::updateAquariumListSource);
        userSettings.addSource(authenticatedUserId, this::updateSettingsSource);

        // Observe both user and selection for history
        fullHistory.addSource(authenticatedUserId, id -> updateHistorySource());
        fullHistory.addSource(selectedAquariumId, id -> updateHistorySource());

        // Check initial authentication state (this will trigger the observer above)
        checkUserAuthentication();
    }

    /**
     * Updates the data source for available aquariums based on the authenticated user ID.
     * If a valid user is logged in, it fetches the list of aquariums; otherwise, it provides an empty list.
     *
     * @param userId The ID of the authenticated user.
     */
    private void updateAquariumListSource(String userId) {
        if (currentAquariumsSource != null) availableAquariums.removeSource(currentAquariumsSource);
        if (isValidUser(userId)) {
            currentAquariumsSource = firestoreDataSource.getListOfAquariums(userId);
            availableAquariums.addSource(currentAquariumsSource, availableAquariums::setValue);
        } else {
            availableAquariums.setValue(new ArrayList<>());
        }
    }

    /**
     * Updates the data source for user settings based on the authenticated user ID.
     * If a valid user is logged in, it fetches their settings; otherwise, it provides default settings.
     *
     * @param userId The ID of the authenticated user.
     */
    private void updateSettingsSource(String userId) {
        if (currentSettingsSource != null) userSettings.removeSource(currentSettingsSource);
        if (isValidUser(userId)) {
            currentSettingsSource = settingsService.getSettingsForCurrentUser();
            userSettings.addSource(currentSettingsSource, userSettings::setValue);
        } else {
            userSettings.setValue(new UserSettings());
        }
    }

    /**
     * Updates the data source for historical aquarium data.
     * This method is triggered by changes in either the authenticated user ID or the selected aquarium ID.
     * It fetches the history only if both a valid user is logged in and an aquarium is selected.
     */
    private void updateHistorySource() {
        String userId = authenticatedUserId.getValue();
        String aquariumId = selectedAquariumId.getValue();

        Log.d(TAG, "updateHistorySource called. User ID: " + userId + ", Aquarium ID: " + aquariumId);

        if (currentHistorySource != null) fullHistory.removeSource(currentHistorySource);

        if (isValidUser(userId) && aquariumId != null) {
            currentHistorySource = firestoreDataSource.getAquariumHistory(userId, aquariumId);
            fullHistory.addSource(currentHistorySource, fullHistory::setValue);
        } else {
            Log.d(TAG, "Not fetching history: Invalid user or no aquarium selected. User ID valid: " + isValidUser(userId) + ", Aquarium ID null: " + (aquariumId == null));
            fullHistory.setValue(new ArrayList<>());
        }
    }

    /**
     * Checks if a given user ID is valid (not null and not equal to {@link #NO_USER_ID}).
     *
     * @param userId The user ID to validate.
     * @return {@code true} if the user ID is valid; {@code false} otherwise.
     */
    private boolean isValidUser(String userId) {
        return userId != null && !userId.equals(NO_USER_ID);
    }

    // --- Getters & Setters ---
    /**
     * Returns a {@link LiveData} containing the list of available aquariums for the current user.
     *
     * @return A LiveData object with a list of {@link Aquarium} objects.
     */
    public LiveData<List<Aquarium>> getAvailableAquariums() { return availableAquariums; }
    /**
     * Returns a {@link LiveData} containing the current user's settings.
     *
     * @return A LiveData object with {@link UserSettings}.
     */
    public LiveData<UserSettings> getUserSettings() { return userSettings; }
    /**
     * Returns a {@link LiveData} containing the historical data for the currently selected aquarium.
     *
     * @return A LiveData object with a list of {@link AquariumData} objects.
     */
    public LiveData<List<AquariumData>> getHistory() { return fullHistory; }
    /**
     * Returns a {@link LiveData} containing the latest received data point for the selected aquarium.
     *
     * @return A LiveData object with an {@link AquariumData} object.
     */
    public LiveData<AquariumData> getLatestData() { return latestDataPoint; }
    /**
     * Returns a {@link LiveData} containing the current connection status.
     *
     * @return A LiveData object with an {@link EnumConnectionStatus}.
     */
    public LiveData<EnumConnectionStatus> getConnectionStatus() { return connectionStatus; }
    /**
     * Returns a {@link LiveData} containing the ID of the currently selected aquarium.
     *
     * @return A LiveData object with the selected aquarium's ID as a String.
     */
    public LiveData<String> getSelectedAquariumId() { return selectedAquariumId; }

    /**
     * Sets the currently selected aquarium by its ID.
     * If the provided ID is different from the current selection, it updates the {@code selectedAquariumId} LiveData,
     * which will in turn trigger an update to the historical data stream.
     *
     * @param aquariumId The ID of the aquarium to set as selected.
     */
    public void setSelectedAquarium(String aquariumId) {
        if (aquariumId != null && !aquariumId.equals(selectedAquariumId.getValue())) {
            selectedAquariumId.setValue(aquariumId);
        }
    }

    /**
     * Creates a new aquarium for the current user.
     * The new aquarium is stored in Firestore via {@link FirestoreDataSource#createAquarium(String, String)}.
     *
     * @param name The name of the aquarium to create.
     */
    public void addNewAquarium(String name) {
        String userId = authenticatedUserId.getValue();
        if (isValidUser(userId)) {
            firestoreDataSource.createAquarium(userId, name)
                    .addOnSuccessListener(docRef -> Log.d(TAG, "Aquarium created successfully with ID: " + docRef.getId()))
                    .addOnFailureListener(e -> Log.e(TAG, "Error creating aquarium: " + name, e));
        } else {
            Log.e(TAG, "Cannot create aquarium: User not authenticated.");
        }
    }

    /**
     * Callback method invoked when new aquarium data is received.
     * This method updates the {@link #latestDataPoint} LiveData, saves the data to Firestore,
     * and checks if any alerts need to be triggered based on user settings.
     *
     * @param newData The new {@link AquariumData} received.
     */
    @Override
    public void onNewData(AquariumData newData) {
        latestDataPoint.postValue(newData);

        String userId = authenticatedUserId.getValue();
        String aquariumId = selectedAquariumId.getValue();

        if (isValidUser(userId) && aquariumId != null) {
            firestoreDataSource.saveDataToAquarium(userId, aquariumId, newData);
        }

        checkLimitsAndNotify(newData);
    }

    /**
     * Checks the incoming aquarium data against user-defined thresholds in {@link UserSettings}.
     * If any parameter is out of range, it constructs an alert message and sends
     * a local notification and, optionally, an SMS if a phone number is configured.
     *
     * @param incomingData The latest {@link AquariumData} to check.
     */
    private void checkLimitsAndNotify(AquariumData incomingData) {
        UserSettings settings = userSettings.getValue();
        if (settings == null) {
            Log.w(TAG, "Settings not loaded yet, using defaults for check.");
            settings = new UserSettings();
        }






        StringBuilder alertBuilder = new StringBuilder();
        if (incomingData.getTemperature() < settings.getMinTemperature())
            alertBuilder.append("Temp low: ").append(incomingData.getTemperature()).append("°C. ");
        if (incomingData.getTemperature() > settings.getMaxTemperature())
            alertBuilder.append("Temp high: ").append(incomingData.getTemperature()).append("°C. ");
        if (incomingData.getPh() < settings.getMinPh() || incomingData.getPh() > settings.getMaxPh())
            alertBuilder.append("pH out of range. ");
        if (incomingData.getOxygen() < settings.getMinOxygen())
            alertBuilder.append("Oxygen low! ");

        if (alertBuilder.length() > 0) {
            String message = alertBuilder.toString();
            sendLocalNotification("Aquarium Alert", message);
            if (settings.getPhoneNumber() != null && !settings.getPhoneNumber().isEmpty()) {
                sendSms(settings.getPhoneNumber(), message);
            }
        }
    }

    /**
     * Sends an SMS message to a specified phone number.
     *
     * @param phoneNumber The recipient's phone number.
     * @param message     The content of the SMS message.
     */
    private void sendSms(String phoneNumber, String message) {
        try {
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null);
            Log.d(TAG, "SMS sent to " + phoneNumber);
        } catch (Exception e) {
            Log.e(TAG, "SMS failed", e);
        }
    }

    /**
     * Sends a local notification to the device.
     *
     * @param title   The title of the notification.
     * @param message The content text of the notification.
     */
    private void sendLocalNotification(String title, String message) {
        NotificationManager nm = (NotificationManager) getApplication().getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplication(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    /**
     * Creates a notification channel for aquarium alerts, required for Android 8.0 (Oreo) and above.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Aquarium Alerts", NotificationManager.IMPORTANCE_HIGH);
            getApplication().getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    /**
     * Callback method invoked when the connection status changes.
     * Updates the {@link #connectionStatus} LiveData.
     *
     * @param newStatus The new {@link EnumConnectionStatus}.
     */
    @Override
    public void onConnectionStatusChanged(EnumConnectionStatus newStatus) { connectionStatus.postValue(newStatus); }

    /**
     * Sets this ViewModel as a listener to an {@link IConnection} implementation.
     *
     * @param connection The connection object to which this ViewModel will listen.
     */
    public void setAsListenerTo(IConnection connection) { connection.addListener(this); }

    /**
     * Checks the current Firebase user authentication state and updates {@link #authenticatedUserId}.
     * This method is typically called on ViewModel initialization or when authentication state needs to be re-evaluated.
     */
    public void checkUserAuthentication() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String id = (user != null) ? user.getUid() : NO_USER_ID;
        if (!id.equals(authenticatedUserId.getValue())) authenticatedUserId.setValue(id);
    }
}
