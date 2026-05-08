package com.example.smartaquarium.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.smartaquarium.data.model.Aquarium;
import com.example.smartaquarium.data.viewModel.aquariumData.AquariumDataViewModel;
import com.example.smartaquarium.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A Fragment that displays the current status and data from the selected aquarium.
 * It provides an overview of temperature, pH, oxygen, and water level, along with connection status.
 * Users can also add new aquariums through a dialog.
 * This fragment interacts with {@link AquariumDataViewModel} to fetch and manage aquarium data.
 */
public class DashboardFragment extends Fragment  {

    private TextView tvPhOverview;
    private TextView tvOxygenOverview;
    private TextView tvWaterOverview;
    private TextView tvAlerts;
    private TextView tvTempOverview;
    private TextView tvConnectionStatus;

    private View root;
    private AutoCompleteTextView aquariumSelector;
    private MaterialButton btnAddAquarium;
    private AquariumDataViewModel viewModel;

    /**
     * Public no-argument constructor.
     */
    public DashboardFragment() {}

    /**
     * Initializes the TextViews that display aquarium data and status.
     *
     * @param root The root view of the fragment layout.
     */
    void InitViews(View root) {
        tvTempOverview = root.findViewById(R.id.tv_temp_overview);
        tvPhOverview = root.findViewById(R.id.tv_ph_overview);
        tvOxygenOverview = root.findViewById(R.id.tv_oxygen_overview);
        tvWaterOverview = root.findViewById(R.id.tv_water_overview);
        tvAlerts = root.findViewById(R.id.tv_alerts);
        tvConnectionStatus = root.findViewById(R.id.tv_connection_status);
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_dashboard, container, false);
        InitViews(root);

        // 1. Initialize UI Objects
        aquariumSelector = root.findViewById(R.id.spinner_aquarium_selector);
        btnAddAquarium = root.findViewById(R.id.btn_add_aquarium);

        tvTempOverview.setText("Temp: Loading...");
        tvPhOverview.setText("pH: Loading...");
        tvOxygenOverview.setText("Oxygen: Loading...");
        tvWaterOverview.setText("Water: Loading...");
        tvAlerts.setText("Alerts: Loading...");
        tvConnectionStatus.setText("Connection: Loading...");

        viewModel = new ViewModelProvider(requireActivity()).get(AquariumDataViewModel.class);
        viewModel.getLatestData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                tvTempOverview.setText("Temp: "+data.temperature+" °C");
                tvPhOverview.setText("pH: "+data.ph);
                tvOxygenOverview.setText("Oxygen: "+data.oxygen+" mg/L");
                tvWaterOverview.setText("Water: "+data.waterLevel+"%");

            }
        });
        setupClickListeners();
        setupAquariumListObserver();
        return root;



    }

    /**
     * Sets up observers for the ViewModel's LiveData to update the UI when aquarium data changes.
     * It specifically observes the list of available aquariums and the currently selected aquarium.
     */
    private void setupAquariumListObserver() {
        // Watch for changes in the list of aquariums (e.g., after adding one)
        viewModel.getAvailableAquariums().observe(getViewLifecycleOwner(), list -> {
            if (list != null && !list.isEmpty()) {
                updateDropdownAdapter(list);
            }
        });

        // Sync the text in the dropdown with the currently selected aquarium
        viewModel.getSelectedAquariumId().observe(getViewLifecycleOwner(), selectedId -> {
            if (selectedId != null) {
                aquariumSelector.setText(selectedId, false);
            }
        });
    }

    /**
     * Updates the adapter for the aquarium selector dropdown with the provided list of aquariums.
     * It also sets up an item click listener to update the selected aquarium in the ViewModel.
     *
     * @param listOfAquariums The list of {@link Aquarium} objects to display in the dropdown.
     */
    private void updateDropdownAdapter(List<Aquarium> listOfAquariums) {
        if (listOfAquariums == null || getContext() == null) return;

        // 1. Use Streams for a more readable transformation
        List<String> aquariumNames = listOfAquariums.stream()
                .map(Aquarium::getName)
                .collect(Collectors.toList());

        // 2. Use the specific type in the constructor
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                aquariumNames
        );

        aquariumSelector.setAdapter(adapter);

        // 3. Robust selection handling
        aquariumSelector.setOnItemClickListener((parent, view, position, id) -> {
            // Instead of just passing a String, get the actual object from the original list
            Aquarium selectedAquarium = listOfAquariums.get(position);

            // Pass the ID or the whole object to the ViewModel for better reliability
            viewModel.setSelectedAquarium(selectedAquarium.getName());
        });

        // Automatically select the first aquarium if nothing is selected
        if (viewModel.getSelectedAquariumId().getValue() == null && !listOfAquariums.isEmpty()) {
            Aquarium firstAquarium = listOfAquariums.get(0);
            viewModel.setSelectedAquarium(firstAquarium.getName());
            aquariumSelector.setText(firstAquarium.getName(), false);
        }
    }

    /**
     * Sets up click listeners for interactive UI elements, such as the 'Add Aquarium' button.
     */
    private void setupClickListeners() {
        btnAddAquarium.setOnClickListener(v -> showAddAquariumDialog());
    }

    /**
     * Displays an AlertDialog to allow the user to add a new aquarium.
     * The dialog prompts for a unique name and creates a new aquarium via the ViewModel.
     */
    private void showAddAquariumDialog() {
        EditText inputField = new EditText(requireContext());
        inputField.setHint("e.g. Living Room Tank");

        new AlertDialog.Builder(requireContext())
                .setTitle("Add New Aquarium")
                .setMessage("Enter a unique name for this aquarium:")
                .setView(inputField)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = inputField.getText().toString().trim();
                    if (!name.isEmpty()) {
                        viewModel.addNewAquarium(name);
                    } else {
                        Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

}
