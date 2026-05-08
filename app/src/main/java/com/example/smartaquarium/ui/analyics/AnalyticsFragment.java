package com.example.smartaquarium.ui.analyics;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.smartaquarium.R;
import com.example.smartaquarium.data.model.AquariumData;
import com.example.smartaquarium.data.viewModel.analyics.AnalyticsViewModel;
import com.example.smartaquarium.data.viewModel.analyics.AnalyticsViewModelFactory;
import com.github.mikephil.charting.charts.LineChart;

import java.util.function.Function;

/**
 * A Fragment that displays historical aquarium data using a LineChart.
 * Users can select different data types (Temperature, pH, Oxygen, Water Level)
 * and filter data by time ranges (Last 24 Hours, Last 7 Days, Last 30 Days, All Time).
 * This fragment interacts with {@link AnalyticsViewModel} to fetch and process data.
 */
public class AnalyticsFragment extends Fragment {

    private static final int MAX_VISIBLE_ENTRIES = 15;

    /**
     * Enum representing the different types of aquarium data that can be displayed.
     * Each type has a label, a color resource ID for charting, and a function to extract
     * the corresponding value from an {@link AquariumData} object.
     */
    public enum DataType {
        TEMPERATURE("Temperature", R.color.chart_temperature, AquariumData::getTemperature),
        PH("pH Level", R.color.chart_ph, AquariumData::getPh),
        OXYGEN("Oxygen Level", R.color.chart_oxygen, AquariumData::getOxygen),
        WATER_LEVEL("Water Level", R.color.chart_water_level, AquariumData::getWaterLevel);

        private final String label;
        @ColorRes
        private final int colorResId;
        private final Function<AquariumData, Double> valueExtractor;
 
        /**
         * Constructs a DataType enum member.
         *
         * @param label The user-friendly label for this data type.
         * @param colorResId The resource ID for the color associated with this data type's chart line.
         * @param valueExtractor A function to extract the relevant double value from an {@link AquariumData} object.
         */
        DataType(String label, @ColorRes int colorResId, Function<AquariumData, Double> valueExtractor) {
            this.label = label;
            this.colorResId = colorResId;
            this.valueExtractor = valueExtractor;
        }

        /**
         * Retrieves the data value for a given {@link AquariumData} object.
         *
         * @param data The {@link AquariumData} object from which to extract the value.
         * @return The extracted double value.
         */
        public double getValue(AquariumData data) { return valueExtractor.apply(data); }

        /**
         * Retrieves the color associated with this data type.
         *
         * @param context The context to resolve the color resource.
         * @return The integer color value.
         */
        public int getColor(@NonNull Context context) { return ContextCompat.getColor(context, this.colorResId); }
        @NonNull
        @Override
        public String toString() { return this.label; }
    }

    /**
     * Enum representing the different time filters that can be applied to the displayed data.
     * Each filter has a label and a corresponding number of hours.
     */
    public enum DateFilter {
        LAST_24_HOURS("Last 24 Hours", 24),
        LAST_7_DAYS("Last 7 Days", 168),
        LAST_30_DAYS("Last 30 Days", 720),
        ALL_TIME("All Time", Integer.MAX_VALUE);

        private final String label;
        private final int hours;

        /**
         * Constructs a DateFilter enum member.
         *
         * @param label The user-friendly label for this date filter.
         * @param hours The number of hours this filter represents.
         */
        DateFilter(String label, int hours) {
            this.label = label;
            this.hours = hours;
        }

        /**
         * Retrieves the number of hours for this date filter.
         *
         * @return The number of hours.
         */
        public int getHours() { return hours; }
        @NonNull
        @Override
        public String toString() { return label; }
    }

    // --- Views and ViewModel ---
    private LineChart lineChart;
    private Spinner dataTypeSpinner;
    private Spinner dateFilterSpinner;
    private AnalyticsViewModel analyticsViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analytics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init(view);
        setupDataObservers();
    }

    /**
     * Initializes the views and ViewModel for the fragment.
     *
     * @param view The root view of the fragment layout.
     */
    private void init(View view) {
        // Initialize views
        lineChart = view.findViewById(R.id.line_chart);
        dataTypeSpinner = view.findViewById(R.id.spinner_data_type);
        dateFilterSpinner = view.findViewById(R.id.spinner_date_filter);

        // Create the factory to correctly instantiate our ViewModel
        Application application = requireActivity().getApplication();
        FragmentActivity owner = requireActivity();
        AnalyticsViewModelFactory factory = new AnalyticsViewModelFactory(application, owner);

        // Get the ViewModel using the factory
        analyticsViewModel = new ViewModelProvider(this, factory).get(AnalyticsViewModel.class);

        setupChartStyling();
        setupSpinners();
    }

    /**
     * Sets up the initial styling for the {@link LineChart}.
     */
    private void setupChartStyling() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setNoDataText("No data available for the selected range.");
    }

    /**
     * Sets up the adapters and item selected listeners for the data type and date filter spinners.
     */
    private void setupSpinners() {
        // Data Type Spinner
        ArrayAdapter<DataType> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, DataType.values());
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dataTypeSpinner.setAdapter(typeAdapter);

        dataTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                analyticsViewModel.setDataType((DataType) parent.getItemAtPosition(position));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Date Filter Spinner
        ArrayAdapter<DateFilter> filterAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, DateFilter.values());
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dateFilterSpinner.setAdapter(filterAdapter);

        dateFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                analyticsViewModel.setDateFilter((DateFilter) parent.getItemAtPosition(position));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Sets up observers for the {@link AnalyticsViewModel}'s LiveData, specifically for processed chart data.
     * Updates the chart when new data is available.
     */
    private void setupDataObservers() {
        analyticsViewModel.getProcessedChartData().observe(getViewLifecycleOwner(), chartData -> {
            if (chartData != null) {
                lineChart.setData(chartData);
                moveChartViewToLastEntry();
                lineChart.invalidate();
            }
        });
    }

    /**
     * Moves the chart view to display the most recent entries, up to {@link #MAX_VISIBLE_ENTRIES}.
     * If no data is available, the chart is cleared.
     */
    private void moveChartViewToLastEntry() {
        if (lineChart.getData() != null && lineChart.getData().getEntryCount() > 0) {
            lineChart.setVisibleXRangeMaximum(MAX_VISIBLE_ENTRIES);
            lineChart.moveViewToX(lineChart.getData().getEntryCount());
        } else {
            lineChart.clear();
        }
    }
}
