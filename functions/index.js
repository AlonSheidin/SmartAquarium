// Import the v1 Functions SDK and the Firebase Admin SDK.
const functions = require("firebase-functions");
const admin = require("firebase-admin");

// Initialize the Admin SDK. This allows our function to access other Firebase services.
admin.initializeApp();

/**
 * This Cloud Function triggers whenever a new document is created in any
 * 'aquariumData' subcollection.
 *
 * The wildcard {userId} captures the ID of the user whose data was added.
 */
exports.checkAquariumAndNotify = functions.firestore
    .document("users/{userId}/aquariumData/{dataId}")
    .onCreate(async (snapshot, context) => {
        // 1. Get the newly added aquarium data and the user's ID.
        const newAquariumData = snapshot.data();
        const userId = context.params.userId;
        console.log(`Checking new data for user: ${userId}`);

        // 2. Fetch the user's settings document.
        const settingsRef = admin.firestore()
            .collection("users").doc(userId)
            .collection("settings").doc("userSettings");

        const settingsDoc = await settingsRef.get();

        // If the user has no settings, we can't check anything, so we exit.
        if (!settingsDoc.exists) {
            console.log(`No settings found for user ${userId}. Exiting function.`);
            return null;
        }
        const userSettings = settingsDoc.data();

        // 3. Compare new data with settings to find issues.
        const issuesFound = [];
        if (newAquariumData.temperature < userSettings.minTemperature) {
            issuesFound.push(`Temperature is too low: ${newAquariumData.temperature}°C.`);
        }
        if (newAquariumData.temperature > userSettings.maxTemperature) {
            issuesFound.push(`Temperature is too high: ${newAquariumData.temperature}°C.`);
        }
        if (newAquariumData.ph < userSettings.minPh) {
            issuesFound.push(`pH is too low: ${newAquariumData.ph}.`);
        }
        if (newAquariumData.ph > userSettings.maxPh) {
            issuesFound.push(`pH is too high: ${newAquariumData.ph}.`);
        }
        if (newAquariumData.oxygen < userSettings.minOxygen) {
            issuesFound.push(`Oxygen is too low: ${newAquariumData.oxygen} mg/L.`);
        }
        if (newAquariumData.oxygen > userSettings.maxOxygen) {
            issuesFound.push(`Oxygen is too high: ${newAquariumData.oxygen} mg/L.`);
        }
        if (newAquariumData.waterLevel < userSettings.minWaterLevel) {
                    issuesFound.push(`Water Level is too low: ${newAquariumData.waterLevel}%.`);
        }
        if (newAquariumData.waterLevel > userSettings.maxWaterLevel) {
            issuesFound.push(`Water Level is too high: ${newAquariumData.waterLevel}%.`);
        }

        // 4. If no issues were found, we are done. Exit the function.
        if (issuesFound.length === 0) {
            console.log("All data is within range. No notification needed.");
            return null;
        }

        // 5. If issues were found, get the user's main document to find their FCM token.
        const userDoc = await admin.firestore().collection("users").doc(userId).get();
        if (!userDoc.exists) {
            console.error(`User document for ${userId} not found.`);
            return null;
        }
        const fcmToken = userDoc.data().fcmToken;

        // If the user doesn't have an FCM token, we can't send a notification.
        if (!fcmToken) {
            console.error(`FCM token not found for user ${userId}. Cannot send notification.`);
            return null;
        }

        // 6. Construct the notification payload and send it.
        const notificationTitle = "Smart Aquarium Alert!";
        const notificationBody = issuesFound.join(" "); // Combine all issues into one message.

        const payload = {
            notification: {
                title: notificationTitle,
                body: notificationBody,
            },
            token: fcmToken,
        };

        try {
            console.log(`Sending notification to user ${userId}`);
            await admin.messaging().send(payload);
            console.log("Notification sent successfully.");
        } catch (error) {
            console.error("Error sending notification:", error);
            // Optional: If the error code indicates an invalid token, you could remove it from Firestore.
        }

        return null;
    });
