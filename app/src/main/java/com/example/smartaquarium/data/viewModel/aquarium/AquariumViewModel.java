package com.example.smartaquarium.data.viewModel.aquarium;

import android.app.Application;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.example.smartaquarium.R;
import com.example.smartaquarium.data.model.AquariumData;
import com.example.smartaquarium.data.viewModel.aquariumData.AquariumDataViewModel;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * ViewModel for the {@link com.example.smartaquarium.ui.aquarium.AquariumFragment}.
 * This ViewModel transforms the latest {@link AquariumData} point into individual, styled {@link BarData} objects
 * for each metric (temperature, pH, oxygen, water level) to be displayed on the overview dashboard.
 * It depends on {@link AquariumDataViewModel} to get the raw data.
 */
public class AquariumViewModel extends AndroidViewModel {

    // --- Output LiveData ---
    // The fragment will observe these LiveData objects to get the final, styled chart data.
    public final LiveData<BarData> temperatureBarData;
    public final LiveData<BarData> phBarData;
    public final LiveData<BarData> oxygenBarData;
    public final LiveData<BarData> waterLevelBarData;
    public final LiveData<String> lastUpdatedTimestamp;

    /**
     * Constructs an {@link AquariumViewModel}.
     *
     * @param application The application context.
     * @param owner       The {@link ViewModelStoreOwner} (e.g., Activity or Fragment) that owns this ViewModel.
     *                    This is used to obtain a shared instance of {@link AquariumDataViewModel}.
     */
    public AquariumViewModel(@NonNull Application application, @NonNull ViewModelStoreOwner owner) {
        super(application);

        // Get the single, shared instance of the ViewModel that provides the raw data.
        // This ensures data consistency across different parts of the application that might use AquariumDataViewModel.
        AquariumDataViewModel dataProviderViewModel = new ViewModelProvider(owner).get(AquariumDataViewModel.class);

        // Initialize all the data streams and transformations.
        // This keeps the constructor clean and delegates stream creation to private helper methods.
        lastUpdatedTimestamp = initializeTimestampStream(dataProviderViewModel);
        temperatureBarData = initializeTemperatureStream(dataProviderViewModel);
        phBarData = initializePhStream(dataProviderViewModel);
        oxygenBarData = initializeOxygenStream(dataProviderViewModel);
        waterLevelBarData = initializeWaterLevelStream(dataProviderViewModel);
    }

    // --- Initialization Methods ---

    /**
     * Initializes the LiveData stream for the last updated timestamp.
     * It observes the latest {@link AquariumData} and formats its timestamp into a readable string.
     *
     * @param dataProvider The {@link AquariumDataViewModel} providing the data.
     * @return A {@link LiveData<String>} emitting the formatted timestamp.
     */
    private LiveData<String> initializeTimestampStream(AquariumDataViewModel dataProvider) {
        // The "trigger" is the LiveData that emits the most recent data point from the dataProvider.
        LiveData<AquariumData> latestDataStream = dataProvider.getLatestData();

        // Use Transformations.map to transform the latest AquariumData object into a formatted timestamp string.
        return Transformations.map(latestDataStream, latestAquariumData -> {
            // Check if data and timestamp are available.
            if (latestAquariumData != null && latestAquariumData.getTimestamp() != null) {
                // Get the Date object directly from the AquariumData.
                Date dateObject = latestAquariumData.getTimestamp();

                // Format the Date object into a readable time string (HH:mm:ss).
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                // Combine with a localized string resource for "Last updated: ".
                return getApplication().getString(R.string.last_updated_empty) + sdf.format(dateObject);
            }
            // Return a default string if data or timestamp is not available.
            return getApplication().getString(R.string.last_updated_empty);
        });
    }

    /**
     * Initializes the LiveData stream for the temperature bar data.
     * Observes the latest {@link AquariumData} and creates a {@link BarData} object for temperature.
     *
     * @param dataProvider The {@link AquariumDataViewModel} providing the data.
     * @return A {@link LiveData<BarData>} emitting the temperature bar data. Returns null if data is unavailable.
     */
    private LiveData<BarData> initializeTemperatureStream(AquariumDataViewModel dataProvider) {
        return Transformations.map(dataProvider.getLatestData(), data -> {
            if (data == null) return null; // Return null if no data is available.
            // Create a BarData object for temperature, using a specific color resource.
            return createSingleBarData((int) data.getTemperature(), "Temp °C", R.color.chart_temperature);
        });
    }

    /**
     * Initializes the LiveData stream for the pH bar data.
     * Observes the latest {@link AquariumData} and creates a {@link BarData} object for pH.
     *
     * @param dataProvider The {@link AquariumDataViewModel} providing the data.
     * @return A {@link LiveData<BarData>} emitting the pH bar data. Returns null if data is unavailable.
     */
    private LiveData<BarData> initializePhStream(AquariumDataViewModel dataProvider) {
        return Transformations.map(dataProvider.getLatestData(), data -> {
            if (data == null) return null; // Return null if no data is available.
            // Create a BarData object for pH, using a specific color resource.
            return createSingleBarData((int) data.getPh(), "pH", R.color.chart_ph);
        });
    }

    /**
     * Initializes the LiveData stream for the oxygen bar data.
     * Observes the latest {@link AquariumData} and creates a {@link BarData} object for oxygen.
     *
     * @param dataProvider The {@link AquariumDataViewModel} providing the data.
     * @return A {@link LiveData<BarData>} emitting the oxygen bar data. Returns null if data is unavailable.
     */
    private LiveData<BarData> initializeOxygenStream(AquariumDataViewModel dataProvider) {
        return Transformations.map(dataProvider.getLatestData(), data -> {
            if (data == null) return null; // Return null if no data is available.
            // Create a BarData object for oxygen, using a specific color resource.
            return createSingleBarData((int) data.getOxygen(), "Oxygen %", R.color.chart_oxygen);
        });
    }

    /**
     * Initializes the LiveData stream for the water level bar data.
     * Observes the latest {@link AquariumData} and creates a {@link BarData} object for water level.
     *
     * @param dataProvider The {@link AquariumDataViewModel} providing the data.
     * @return A {@link LiveData<BarData>} emitting the water level bar data. Returns null if data is unavailable.
     */
    private LiveData<BarData> initializeWaterLevelStream(AquariumDataViewModel dataProvider) {
        return Transformations.map(dataProvider.getLatestData(), data -> {
            if (data == null) return null; // Return null if no data is available.
            // Create a BarData object for water level, using a specific color resource.
            return createSingleBarData((int) data.getWaterLevel(), "Level %", R.color.chart_water_level);
        });
    }


    /**
     * A helper function to create a styled {@link BarData} object for a single value.
     * This function encapsulates the creation and styling of a {@link BarDataSet} and wraps it in a {@link BarData}.
     *
     * @param value The integer value to display on the bar.
     * @param label The label for the data set (e.g., "Temp °C", "pH").
     * @param colorResId The color resource ID for the bar (e.g., R.color.chart_temperature).
     * @return A fully formed {@link BarData} object ready for charting.
     */
    private BarData createSingleBarData(int value, String label, int colorResId) {
        // Create a list to hold the bar chart entries. We only need one entry for a single value.
        ArrayList<BarEntry> entries = new ArrayList<>();
        // Add the entry with x=0 (representing the single bar) and the given value.
        entries.add(new BarEntry(0, value));

        // Create a BarDataSet with the entries and the label.
        BarDataSet dataSet = new BarDataSet(entries, label);
        // Set the color of the bar using the provided color resource ID.
        dataSet.setColor(ContextCompat.getColor(getApplication(), colorResId));
        // Set the text color for the value labels displayed on the bars.
        dataSet.setValueTextColor(Color.BLACK);
        // Set the text size for the value labels.
        dataSet.setValueTextSize(16f);

        // Create a BarData object, which holds the DataSet.
        return new BarData(dataSet);
    }
}
