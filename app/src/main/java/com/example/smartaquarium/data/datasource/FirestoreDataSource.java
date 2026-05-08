package com.example.smartaquarium.data.datasource;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smartaquarium.data.model.Aquarium;
import com.example.smartaquarium.data.model.AquariumData;
import com.example.smartaquarium.data.model.UserSettings; // Import the UserSettings model
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.QueryDocumentSnapshot;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all direct data operations with the Google Firestore database.
 * This class is the lowest level of our application's data layer, responsible for
 * fetching and saving both aquarium data and user settings.
 */
public class FirestoreDataSource {

    private static final String TAG = "FirestoreDataSource";
    private static final String USERS_COLLECTION = "users";
    private static final String AQUARIUM_DATA_COLLECTION = "aquariumData";
    private static final String SETTINGS_COLLECTION = "settings"; // A dedicated subcollection for settings
    private static final String SETTINGS_DOCUMENT_NAME = "userSettings";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_AQUARIUMS = "aquariums";
    private static final String COLLECTION_HISTORY = "history";
    private final FirebaseFirestore firestoreDatabase;

    /**
     * Public constructor for the data source.
     * Initializes the Firestore database instance.
     */
    public FirestoreDataSource() {
        // Use an "init" style function to set up the required dependency.
        this.firestoreDatabase = initializeFirestore();
    }

    /**
     * Initializes the Firestore database instance.
     * @return The singleton instance of FirebaseFirestore.
     */
    private FirebaseFirestore initializeFirestore() {
        return FirebaseFirestore.getInstance();
    }

    // --- Aquarium Data Methods ---

    /**
     * Retrieves a real-time stream of aquarium data for a specific user from Firestore.
     * Note: This implementation has a potential for memory leaks. A better approach
     * would be to use a custom LiveData class that manages the listener lifecycle.
     *
     * @param userId The ID of the user whose aquarium data to retrieve.
     * @return A LiveData object that emits a list of AquariumData objects.
     * @see <a href="https://firebase.google.com/docs/firestore/query-data/listen">Listen to real-time updates</a>
     */
    public LiveData<List<AquariumData>> getUserAquariumData(String userId) {
        // Implementation details: Sets up a snapshot listener on the user's aquarium data collection.
        // It orders data by timestamp and updates the LiveData when changes occur.
        // Error handling for invalid userId is included.
        MutableLiveData<List<AquariumData>> liveData = new MutableLiveData<>();

        if (isInvalid(userId)) {
            Log.e(TAG, "Cannot get aquarium data for an invalid user ID.");
            liveData.setValue(Collections.emptyList());
            return liveData;
        }



        Log.i(TAG, "Getting aquarium data for userId: " + userId);
        firestoreDatabase.collection(USERS_COLLECTION)
                .document(userId)
                .collection(AQUARIUM_DATA_COLLECTION)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening to aquarium data", e);
                        return;
                    }

                    if (snapshot != null && !snapshot.isEmpty()) {
                        List<AquariumData> dataList = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            AquariumData data = doc.toObject(AquariumData.class);
                            if (data != null) {
                                dataList.add(data);
                            }
                        }
                        liveData.setValue(dataList);
                    } else {
                        liveData.setValue(Collections.emptyList());
                    }
                });

        return liveData;
    }
    /**
     * Fetches the list of all aquarium IDs/Names for a specific user.
     * Path: users/{userId}/aquariums/
     */
    public LiveData<List<Aquarium>> getListOfAquariums(String userId) {
        MutableLiveData<List<Aquarium>> aquariumListLiveData = new MutableLiveData<>();

        if (isInvalid(userId)) {
            aquariumListLiveData.setValue(new ArrayList<>());
            return aquariumListLiveData;
        }

        firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to aquarium list updates", error);
                        return;
                    }

                    List<Aquarium> aquariumList = new ArrayList<>();
                    if (value != null) {
                        for (QueryDocumentSnapshot document : value) {
                            // Extract the name from the document and the ID from the document reference
                            String tankName = document.getString("name");
                            // Fallback to document ID if name field is missing
                            if (tankName == null) tankName = document.getId();

                            Aquarium aquarium = new Aquarium(document.getId(), tankName);
                            aquariumList.add(aquarium);
                        }
                    }
                    aquariumListLiveData.setValue(aquariumList);
                });

        return aquariumListLiveData;
    }
    /**
     * Fetches the historical sensor data for a specific aquarium.
     * Path: users/{userId}/aquariums/{aquariumId}/history
     */
    public LiveData<List<AquariumData>> getAquariumHistory(String userId, String aquariumId) {
        MutableLiveData<List<AquariumData>> historyLiveData = new MutableLiveData<>();

        firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .document(aquariumId)
                .collection(COLLECTION_HISTORY)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error fetching aquarium history", error);
                        return;
                    }

                    List<AquariumData> historyItems = new ArrayList<>();
                    if (value != null) {
                        for (QueryDocumentSnapshot document : value) {
                            AquariumData data = document.toObject(AquariumData.class);
                            historyItems.add(data);
                        }
                    }
                    historyLiveData.setValue(historyItems);
                });

        return historyLiveData;
    }

    /**
     * Saves new sensor data to the specific aquarium's history collection.
     */
    public void saveDataToAquarium(String userId, String aquariumId, AquariumData data) {
        firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .document(aquariumId)
                .collection(COLLECTION_HISTORY)
                .add(data)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Data saved to aquarium: " + aquariumId))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving data", e));
    }

    /**
     * Creates a new aquarium document for the user.
     */
    public Task<Void> createAquarium(String userId, String aquariumName) {
        // We create a dummy field so the document exists
        return firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .document(aquariumName)
                .set(new java.util.HashMap<String, Object>() {{
                    put("createdAt", com.google.firebase.Timestamp.now());
                }});
    }
    /**
     * Fetches the list of all aquarium IDs/Names for a specific user.
     * The data is fetched from the `users/{userId}/aquariums/` path in Firestore.
     *
     * @param userId The ID of the user whose aquariums to list.
     * @return A LiveData object emitting a list of Aquarium objects, where each Aquarium contains an ID and a name.
     */
    public LiveData<List<Aquarium>> getListOfAquariums(String userId) {
        // Implementation details: Sets up a snapshot listener for the user's aquariums subcollection.
        // It maps each document to an Aquarium object, using the document ID as the aquarium ID
        // and the "name" field as the aquarium name. Falls back to document ID if name is missing.
        MutableLiveData<List<Aquarium>> aquariumListLiveData = new MutableLiveData<>();

        if (isInvalid(userId)) {
            aquariumListLiveData.setValue(new ArrayList<>());
            return aquariumListLiveData;
        }

        firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to aquarium list updates", error);
                        return;
                    }

                    List<Aquarium> aquariumList = new ArrayList<>();
                    if (value != null) {
                        for (QueryDocumentSnapshot document : value) {
                            // Extract the name from the document and the ID from the document reference
                            String tankName = document.getString("name");
                            // Fallback to document ID if name field is missing
                            if (tankName == null) tankName = document.getId();

                            Aquarium aquarium = new Aquarium(document.getId(), tankName);
                            aquariumList.add(aquarium);
                        }
                    }
                    aquariumListLiveData.setValue(aquariumList);
                });

        return aquariumListLiveData;
    }

    /**
     * Fetches the historical sensor data for a specific aquarium.
     * The data is retrieved from the `users/{userId}/aquariums/{aquariumId}/history` path in Firestore.
     *
     * @param userId The ID of the user who owns the aquarium.
     * @param aquariumId The ID of the aquarium for which to fetch history.
     * @return A LiveData object emitting a list of AquariumData objects, representing the historical readings, ordered by timestamp.
     */
    public LiveData<List<AquariumData>> getAquariumHistory(String userId, String aquariumId) {
        // Implementation details: Sets up a snapshot listener for the specific aquarium's history subcollection.
        // It orders the readings by timestamp in ascending order and updates the LiveData.
        // Includes detailed logging for debugging and error handling.
        MutableLiveData<List<AquariumData>> historyLiveData = new MutableLiveData<>();

        Log.d(TAG, "Attempting to get aquarium history for User ID: " + userId + ", Aquarium ID: " + aquariumId);

        firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .document(aquariumId)
                .collection(COLLECTION_HISTORY)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error fetching aquarium history for User ID: " + userId + ", Aquarium ID: " + aquariumId, error);
                        historyLiveData.setValue(new ArrayList<>());
                        return;
                    }

                    List<AquariumData> historyItems = new ArrayList<>();
                    if (value != null) {
                        Log.d(TAG, "Snapshot received for history. Number of documents: " + value.size());
                        if (value.isEmpty()) {
                            Log.d(TAG, "History snapshot is empty for User ID: " + userId + ", Aquarium ID: " + aquariumId);
                        }
                        for (QueryDocumentSnapshot document : value) {
                            AquariumData data = document.toObject(AquariumData.class);
                            if (data != null) {
                                historyItems.add(data);
                            } else {
                                Log.w(TAG, "Could not parse AquariumData from document: " + document.getId());
                            }
                        }
                    } else {
                        Log.d(TAG, "History snapshot value is null for User ID: " + userId + ", Aquarium ID: " + aquariumId);
                    }
                    historyLiveData.setValue(historyItems);
                });

        return historyLiveData;
    }

    /**
     * Saves new sensor data to the specific aquarium's history collection.
     * The data is added to the `users/{userId}/aquariums/{aquariumId}/history` path.
     * A server-side timestamp is used for consistency.
     *
     * @param userId The ID of the user who owns the aquarium.
     * @param aquariumId The ID of the aquarium to save data to.
     * @param data The AquariumData object containing the sensor readings.
     * @return A Task that resolves when the data is successfully saved.
     */
    public void saveDataToAquarium(String userId, String aquariumId, AquariumData data) {
        // Implementation details: Creates a map of data fields and uses FieldValue.serverTimestamp()
        // for the timestamp. Adds this data to the specified aquarium's history collection.
        // Logs success or failure of the operation.
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("temperature", data.getTemperature());
        dataMap.put("ph", data.getPh());
        dataMap.put("oxygen", data.getOxygen());
        dataMap.put("waterLevel", data.getWaterLevel());
        // Use a server-side timestamp to ensure chronological order and consistency
        dataMap.put("timestamp", FieldValue.serverTimestamp());

        firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .document(aquariumId)
                .collection(COLLECTION_HISTORY)
                .add(dataMap)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Data saved to aquarium: " + aquariumId))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving data", e));
    }

    /**
     * Creates a new aquarium document for the user.
     * A document is created at `users/{userId}/aquariums/{aquariumName}`.
     * A `createdAt` timestamp is added to the document.
     *
     * @param userId The ID of the user creating the aquarium.
     * @param aquariumName The name for the new aquarium. This will also be used as the document ID.
     * @return A Task that resolves when the aquarium document is created.
     */
    public Task<Void> createAquarium(String userId, String aquariumName) {
        // Implementation details: Creates a new document in the aquariums collection for the user.
        // Uses the aquariumName as the document ID. Adds a 'createdAt' field with a server timestamp.
        // We create a dummy field so the document exists
        return firestoreDatabase.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_AQUARIUMS)
                .document(aquariumName)
                .set(new java.util.HashMap<String, Object>() {{ // Using anonymous inner class for HashMap initialization
                    put("createdAt", com.google.firebase.Timestamp.now());
                }});
    }

    /**
     * Adds a new aquarium data point to Firestore.
     * This method seems to be for adding data directly to a user's main aquarium data collection,
     * which might be different from the history collection used by `saveDataToAquarium`.
     * It adds data to `users/{userId}/aquariumData`.
     *
     * @param userId The ID of the user.
     * @param data The AquariumData object to add.
     */
    public void addNewData(String userId, AquariumData data) {
        // Implementation details: Adds a new document to the user's 'aquariumData' collection.
        // It maps temperature, ph, oxygen, waterLevel, and includes both the provided date and a server timestamp.
        if (isInvalid(userId)) {
            Log.e(TAG, "userId is null or empty, cannot add new data.");
            return;
        }

        Log.i(TAG, "Adding new aquarium data for userId: " + userId);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("temperature", data.getTemperature());
        dataMap.put("ph", data.getPh());
        dataMap.put("oxygen", data.getOxygen());
        dataMap.put("waterLevel", data.getWaterLevel());
        dataMap.put("date", data.getTimestamp()); // Use getTimestamp() now
        // Use a server-side timestamp to ensure chronological order and consistency
        dataMap.put("timestamp", FieldValue.serverTimestamp());

        firestoreDatabase.collection(USERS_COLLECTION)
                .document(userId)
                .collection(AQUARIUM_DATA_COLLECTION)
                .add(dataMap)
                .addOnSuccessListener(documentRef ->
                        Log.d(TAG, "Aquarium data added successfully: " + documentRef.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error adding aquarium data", e));
    }

    // --- User Settings Methods ---

    /**
     * Fetches a user's settings document from Firestore.
     * This method listens for real-time updates to the user's settings.
     * The settings are located at `users/{userId}/settings/userSettings`.
     *
     * @param userId The ID of the user whose settings to fetch.
     * @return A LiveData object that will contain the UserSettings. If settings don't exist or an error occurs,
     *         it emits a default UserSettings object.
     * @see <a href="https://firebase.google.com/docs/firestore/query-data/listen">Listen to real-time updates</a>
     */
    public LiveData<UserSettings> getUserSettings(String userId) {
        // Implementation details: Sets up a snapshot listener for the user's settings document.
        // If the document exists, it's converted to a UserSettings object.
        // If it doesn't exist or an error occurs, a default UserSettings object is provided.
        MutableLiveData<UserSettings> settingsLiveData = new MutableLiveData<>();

        if (isInvalid(userId)) {
            Log.w(TAG, "getUserSettings called with invalid userId. Returning default settings.");
            settingsLiveData.setValue(new UserSettings()); // Return default settings on error
            return settingsLiveData;
        }

        DocumentReference settingsDocRef = firestoreDatabase
                .collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(SETTINGS_DOCUMENT_NAME);

        settingsDocRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen failed for user settings.", error);
                settingsLiveData.setValue(new UserSettings()); // Return default on error
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                UserSettings settings = snapshot.toObject(UserSettings.class);
                Log.i(TAG, "getUserSettings: "+settings.getMinTemperature());
                settingsLiveData.setValue(settings);
                Log.d(TAG, "User settings loaded successfully from Firestore.");
            } else {
                // If no settings document exists yet for the user, provide a default one.
                Log.d(TAG, "No settings document found for user, providing defaults.");
                settingsLiveData.setValue(new UserSettings());
            }
        });

        return settingsLiveData;
    }

    /**
     * Saves a user's settings to a specific document in Firestore.
     * The `.set()` method creates the document if it doesn't exist or overwrites it if it does.
     * The settings are saved to `users/{userId}/settings/userSettings`.
     *
     * @param userId The user's ID.
     * @param settingsToSave The UserSettings object to save.
     * @return A Task that resolves upon completion of the save operation.
     * @throws IllegalArgumentException if the userId is null or empty.
     */
    public Task<Void> saveUserSettings(String userId, UserSettings settingsToSave) {
        // Implementation details: Gets a reference to the user's settings document and uses .set() 
        // to save the provided UserSettings object. Includes error handling for invalid userId.
        if (isInvalid(userId)) {
            return Tasks.forException(new IllegalArgumentException("User ID cannot be null or empty."));
        }

        DocumentReference settingsDocRef = firestoreDatabase
                .collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(SETTINGS_DOCUMENT_NAME);

        // The .set() method is perfect for settings, as it handles both creation and updates.
        return settingsDocRef.set(settingsToSave)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "User settings saved successfully for user: " + userId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error saving user settings for user: " + userId, e));
    }


    /**
     * Helper method to check for invalid IDs (null or empty strings) to reduce code duplication.
     *
     * @param id The ID string to check.
     * @return true if the ID is null or empty after trimming whitespace, false otherwise.
     */
    private boolean isInvalid(String id) {
        return id == null || id.trim().isEmpty();
    }
}
