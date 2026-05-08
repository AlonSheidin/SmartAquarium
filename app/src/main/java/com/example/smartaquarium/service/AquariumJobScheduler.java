package com.example.smartaquarium.service;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * Utility class for scheduling and canceling the {@link AquariumAlertJobService}.
 * This class provides static methods to interact with the Android JobScheduler
 * to ensure that aquarium data monitoring runs periodically in the background.
 */
public class AquariumJobScheduler {

    private static final String TAG = "AquariumJobScheduler";
    private static final int JOB_ID = 1001; // Unique ID for this job

    /**
     * Schedules the {@link AquariumAlertJobService} to run periodically.
     * The job is set to repeat every 15 minutes, which is the minimum interval for periodic jobs.
     *
     * @param context The application context, used to get the JobScheduler service and ComponentName.
     */
    public static void scheduleAquariumAlertJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler == null) {
            Log.e(TAG, "JobScheduler service not available.");
            return;
        }

        JobInfo.Builder builder = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, AquariumAlertJobService.class)
        );

        // Set the job to run periodically, for example, every 15 minutes.
        // The minimum interval is 15 minutes.
        builder.setPeriodic(15 * 60 * 1000); // 15 minutes in milliseconds

        // Optional: Add constraints
        // builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        // builder.setRequiresCharging(true);
        // builder.setRequiresDeviceIdle(true);

        JobInfo jobInfo = builder.build();

        int resultCode = jobScheduler.schedule(jobInfo);

        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Aquarium alert job scheduled successfully.");
        } else {
            Log.e(TAG, "Failed to schedule aquarium alert job. Result code: " + resultCode);
        }
    }

    /**
     * Cancels the previously scheduled {@link AquariumAlertJobService}.
     * If the job is currently running, the system will stop it.
     *
     * @param context The application context, used to get the JobScheduler service.
     */
    public static void cancelAquariumAlertJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            jobScheduler.cancel(JOB_ID);
            Log.d(TAG, "Aquarium alert job cancelled.");
        } else {
            Log.e(TAG, "JobScheduler service not available when trying to cancel job.");
        }
    }
}
