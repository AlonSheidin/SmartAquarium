package com.example.smartaquarium.data.viewModel.analyics;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.example.smartaquarium.data.model.AquariumData;
import com.example.smartaquarium.data.viewModel.aquariumData.AquariumDataViewModel;
import com.example.smartaquarium.ui.analyics.AnalyticsFragment;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * ViewModel for the Analytics screen. This ViewModel is responsible for all
 * data processing and state management for the analytics chart.
 * It observes changes in selected data type and date filter, processes the
 * aquarium history data, and provides formatted data suitable for charting.
 */
public class AnalyticsViewModel extends AndroidViewModel {

    private static final String TAG = "AnalyticsViewModel";

    // --- Input LiveData ---
    // Holds the currently selected data type to be displayed on the chart (e.g., Temperature, pH).
    private final MutableLiveData<AnalyticsFragment.DataType> selectedDataType = new MutableLiveData<>();
    // Holds the currently selected date filter for the chart data (e.g., Last 24 hours, Last 7 days).
    private final MutableLiveData<AnalyticsFragment.DateFilter> selectedDateFilter = new MutableLiveData<>();

    // --- Output LiveData ---
    // The processed data ready to be displayed on the chart. This is a MediatorLiveData
    // that combines data from various sources.
    private final LiveData<LineData> processedChartData;

    /**
     * Constructs an {@link AnalyticsViewModel}.
     *
     * @param application The application context.
     * @param owner       The {@link ViewModelStoreOwner} (e.g., Activity or Fragment) that owns this ViewModel.
     *                    Used here to get an instance of {@link AquariumDataViewModel}.
     */
    public AnalyticsViewModel(@NonNull Application application, @NonNull ViewModelStoreOwner owner) {
        super(application);
        // Obtain an instance of AquariumDataViewModel to access historical aquarium data.
        AquariumDataViewModel aquariumDataViewModel = new ViewModelProvider(owner).get(AquariumDataViewModel.class);

        // Set default values for the data type and date filter to ensure the pipeline triggers on initialization.
        selectedDataType.setValue(AnalyticsFragment.DataType.TEMPERATURE);
        selectedDateFilter.setValue(AnalyticsFragment.DateFilter.LAST_24_HOURS);

        // Initialize the data processing pipeline.
        processedChartData = init(aquariumDataViewModel);
    }

    /**
     * Initializes the data processing pipeline.
     * This method sets up observers for historical data, selected data type, and date filter.
     * When any of these inputs change, it re-processes the data and updates the chart data.
     *
     * @param dataProviderViewModel The {@link AquariumDataViewModel} used to fetch historical data.
     * @return A {@link LiveData<LineData>} that emits the processed data for the chart.
     */
    private LiveData<LineData> init(AquariumDataViewModel dataProviderViewModel) {
        // Get the LiveData stream for historical aquarium data.
        LiveData<List<AquariumData>> historyDataSource = dataProviderViewModel.getHistory();
        // Use a MediatorLiveData to combine multiple sources and trigger updates.
        MediatorLiveData<LineData> mediator = new MediatorLiveData<>();

        // Define a Runnable that will be executed whenever any of the input LiveData sources change.
        Runnable updatePipeline = () -> {
            // Get the latest values from the observed LiveData.
            List<AquariumData> history = historyDataSource.getValue();
            AnalyticsFragment.DataType type = selectedDataType.getValue();
            AnalyticsFragment.DateFilter filter = selectedDateFilter.getValue();

            // Only process data if all required inputs are available.
            if (history != null && type != null && filter != null) {
                Log.d(TAG, "Processing analytics data. History size: " + history.size() + ", Type: " + type + ", Filter: " + filter);
                // Process the data and set the value for the chart.
                mediator.setValue(processDataForChart(history, type, filter));
            } else {
                // Log if data is not ready for processing.
                Log.d(TAG, "Analytics data not ready for processing. History is null: " + (history == null) + ", Type is null: " + (type == null) + ", Filter is null: " + (filter == null));
                // Clear the chart data if inputs are not ready.
                mediator.setValue(new LineData());
            }
        };

        // Add sources to the MediatorLiveData. The updatePipeline Runnable will be called
        // whenever any of these sources emit a new value.
        mediator.addSource(historyDataSource, h -> updatePipeline.run());
        mediator.addSource(selectedDataType, t -> updatePipeline.run());
        mediator.addSource(selectedDateFilter, f -> updatePipeline.run());

        return mediator;
    }

    /**
     * Gets the processed chart data.
     *
     * @return A {@link LiveData<LineData>} containing the data formatted for the chart.
     */
    public LiveData<LineData> getProcessedChartData() {
        return processedChartData;
    }

    /**
     * Sets the data type to be displayed on the chart.
     * This will trigger a re-processing of the data if the new type is different from the current one.
     *
     * @param dataType The {@link AnalyticsFragment.DataType} to set.
     */
    public void setDataType(AnalyticsFragment.DataType dataType) {
        if (dataType != selectedDataType.getValue()) {
            selectedDataType.setValue(dataType);
        }
    }

    /**
     * Sets the date filter for the chart data.
     * This will trigger a re-processing of the data if the new filter is different from the current one.
     *
     * @param filter The {@link AnalyticsFragment.DateFilter} to set.
     */
    public void setDateFilter(AnalyticsFragment.DateFilter filter) {
        if (filter != selectedDateFilter.getValue()) {
            selectedDateFilter.setValue(filter);
        }
    }

    /**
     * Processes the raw aquarium history data to generate data suitable for charting.
     * This involves filtering by date, converting data points to chart {@link Entry} objects,
     * and styling the {@link LineDataSet}.
     *
     * @param history The complete list of {@link AquariumData} records.
     * @param dataType The {@link AnalyticsFragment.DataType} to extract values for.
     * @param filter The {@link AnalyticsFragment.DateFilter} to apply.
     * @return A {@link LineData} object containing the processed data for the chart.
     */
    private LineData processDataForChart(List<AquariumData> history, AnalyticsFragment.DataType dataType, AnalyticsFragment.DateFilter filter) {
        // Return empty LineData if history is null or empty.
        if (history == null || history.isEmpty()) {
            Log.d(TAG, "processDataForChart: History is null or empty, returning empty LineData.");
            return new LineData();
        }

        // 1. Filter the history data by the selected date range.
        List<AquariumData> filteredList = filterHistoryByDate(history, filter);

        // Return empty LineData if the filtered list is empty.
        if (filteredList.isEmpty()) {
            Log.d(TAG, "processDataForChart: Filtered list is empty, returning empty LineData.");
            return new LineData();
        }

        // 2. Convert the filtered data into chart Entries.
        List<Entry> chartEntries = new ArrayList<>();
        for (int i = 0; i < filteredList.size(); i++) {
            AquariumData data = filteredList.get(i);
            // Use the DataType enum to get the specific value (e.g., temperature, pH).
            int value = (int) dataType.getValue(data);
            // Create an Entry for the chart. The x-value is the index, and the y-value is the data point.
            chartEntries.add(new Entry(i, value));
        }
        Log.d(TAG, "processDataForChart: Generated " + chartEntries.size() + " chart entries.");

        // 3. Create and style the LineDataSet for the chart.
        LineDataSet chartDataSet = new LineDataSet(chartEntries, dataType.toString() + " (" + filter.toString() + ")");
        // Set color and other visual properties based on the DataType.
        int color = dataType.getColor(getApplication().getApplicationContext());
        chartDataSet.setColor(color);
        chartDataSet.setCircleColor(color);
        chartDataSet.setDrawValues(false); // Do not draw the value labels on the data points.

        // Return the LineData object containing the configured dataset.
        return new LineData(chartDataSet);
    }

    /**
     * Filters the list of aquarium history data based on a specified time range.
     *
     * @param history The complete list of {@link AquariumData} to be filtered.
     * @param filter The time duration filter (e.g., last 24 hours, last week) to apply.
     * @return A list of {@link AquariumData} records that fall within the specified time frame.
     */
    private List<AquariumData> filterHistoryByDate(List<AquariumData> history, AnalyticsFragment.DateFilter filter) {
        // If the filter is "ALL_TIME", return the entire history list without filtering.
        if (filter == AnalyticsFragment.DateFilter.ALL_TIME) {
            Log.d(TAG, "filterHistoryByDate: Filter is ALL_TIME, returning full history.");
            return history;
        }

        // Calculate the cutoff date based on the selected filter.
        Calendar cal = Calendar.getInstance();
        // Subtract the number of hours specified by the filter from the current time.
        cal.add(Calendar.HOUR, -filter.getHours());
        Date cutoff = cal.getTime();

        List<AquariumData> filtered = new ArrayList<>();
        // Iterate through the history and add data points that are after the cutoff date.
        for (AquariumData data : history) {
            // Ensure the timestamp is not null before comparing.
            if (data.getTimestamp() != null && data.getTimestamp().after(cutoff)) {
                filtered.add(data);
            }
        }
        Log.d(TAG, "filterHistoryByDate: Filtered history from " + history.size() + " to " + filtered.size() + " items for filter: " + filter);
        return filtered;
    }
}
