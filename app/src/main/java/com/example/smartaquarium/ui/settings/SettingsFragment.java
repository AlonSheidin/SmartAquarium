package com.example.smartaquarium.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.smartaquarium.R;
import com.example.smartaquarium.data.model.UserSettings;
import com.example.smartaquarium.service.UserSettingsService;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Fragment for managing user settings for all aquarium parameters and app appearance.
 * It uses UserSettingsService to load and save data, keeping the UI logic clean.
 */
public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    private static final String PREFS_NAME = "SmartAquariumPrefs";
    private static final String KEY_DARK_MODE = "isDarkMode";

    // --- UI Components ---
    private SwitchMaterial darkModeSwitch;
    private TextInputEditText minTemperatureEditText;
    private TextInputEditText maxTemperatureEditText;
    private TextInputEditText minPhEditText;
    private TextInputEditText maxPhEditText;
    private TextInputEditText minOxygenEditText;
    private TextInputEditText maxOxygenEditText;
    private TextInputEditText minWaterLevelEditText;
    private TextInputEditText maxWaterLevelEditText;
    private TimePicker doNotDisturbStartTimePicker;
    private TimePicker doNotDisturbEndTimePicker;
    private Button saveSettingsButton;
    private Button logoutButton;

    // --- Logic Service ---
    private UserSettingsService userSettingsService;
    private FirebaseAuth firebaseAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.fragment_settings, container, false);
        initializeFragment(fragmentView);
        return fragmentView;
    }

    /**
     * Main initialization function for the fragment.
     */
    private void initializeFragment(View view) {
        this.userSettingsService = new UserSettingsService();
        this.firebaseAuth = FirebaseAuth.getInstance();
        
        initializeUiComponents(view);
        setupListeners();
        loadAndObserveUserSettings();
        loadAppearanceSettings();
    }

    /**
     * Binds all the UI component variables to their views in the layout.
     */
    private void initializeUiComponents(View view) {
        darkModeSwitch = view.findViewById(R.id.switch_dark_mode);
        minTemperatureEditText = view.findViewById(R.id.edit_text_min_temperature);
        maxTemperatureEditText = view.findViewById(R.id.edit_text_max_temperature);
        minPhEditText = view.findViewById(R.id.edit_text_min_ph);
        maxPhEditText = view.findViewById(R.id.edit_text_max_ph);
        minOxygenEditText = view.findViewById(R.id.edit_text_min_oxygen);
        maxOxygenEditText = view.findViewById(R.id.edit_text_max_oxygen);
        minWaterLevelEditText = view.findViewById(R.id.edit_text_min_water_level);
        maxWaterLevelEditText = view.findViewById(R.id.edit_text_max_water_level);

        doNotDisturbStartTimePicker = view.findViewById(R.id.time_picker_dnd_start);
        doNotDisturbEndTimePicker = view.findViewById(R.id.time_picker_dnd_end);
        saveSettingsButton = view.findViewById(R.id.button_save_settings);
        logoutButton = view.findViewById(R.id.button_logout);

        doNotDisturbStartTimePicker.setIs24HourView(true);
        doNotDisturbEndTimePicker.setIs24HourView(true);
    }

    private void setupListeners() {
        saveSettingsButton.setOnClickListener(v -> showSaveConfirmationDialog());
        logoutButton.setOnClickListener(v -> showLogoutConfirmationDialog());
        
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveAppearancePreference(isChecked);
            applyTheme(isChecked);
        });
    }

    /**
     * Loads the theme preference and sets the switch state.
     */
    private void loadAppearanceSettings() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        darkModeSwitch.setChecked(isDarkMode);
    }

    private void saveAppearancePreference(boolean isDarkMode) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_MODE, isDarkMode).apply();
    }

    private void applyTheme(boolean isDarkMode) {
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void loadAndObserveUserSettings() {
        Log.d(TAG, "Asking UserSettingsService for current user's settings.");
        userSettingsService.getSettingsForCurrentUser().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                updateUiWithSettings(settings);
            } else {
                disableUiComponents();
            }
        });
    }

    private void executeSaveSettings() {
        UserSettings settingsToSave = new UserSettings();
        try {
            settingsToSave.setMinTemperature(Double.parseDouble(minTemperatureEditText.getText().toString()));
            settingsToSave.setMaxTemperature(Double.parseDouble(maxTemperatureEditText.getText().toString()));
            settingsToSave.setMinPh(Double.parseDouble(minPhEditText.getText().toString()));
            settingsToSave.setMaxPh(Double.parseDouble(maxPhEditText.getText().toString()));
            settingsToSave.setMinOxygen(Double.parseDouble(minOxygenEditText.getText().toString()));
            settingsToSave.setMaxOxygen(Double.parseDouble(maxOxygenEditText.getText().toString()));
            settingsToSave.setMinWaterLevel(Double.parseDouble(minWaterLevelEditText.getText().toString()));
            settingsToSave.setMaxWaterLevel(Double.parseDouble(maxWaterLevelEditText.getText().toString()));

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                settingsToSave.setDoNotDisturbStartHour(doNotDisturbStartTimePicker.getHour());
                settingsToSave.setDoNotDisturbEndHour(doNotDisturbEndTimePicker.getHour());
            } else {
                settingsToSave.setDoNotDisturbStartHour(doNotDisturbStartTimePicker.getCurrentHour());
                settingsToSave.setDoNotDisturbEndHour(doNotDisturbEndTimePicker.getCurrentHour());
            }

            Toast.makeText(getContext(), "Saving...", Toast.LENGTH_SHORT).show();
            saveSettingsButton.setEnabled(false);

            userSettingsService.saveSettingsForCurrentUser(settingsToSave)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Settings saved successfully!", Toast.LENGTH_SHORT).show();
                        saveSettingsButton.setEnabled(true);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save settings", e);
                        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        saveSettingsButton.setEnabled(true);
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter a valid number in all fields.", Toast.LENGTH_LONG).show();
            saveSettingsButton.setEnabled(true);
        }
    }

    private void updateUiWithSettings(UserSettings settings) {
        minTemperatureEditText.setText(String.valueOf(settings.getMinTemperature()));
        maxTemperatureEditText.setText(String.valueOf(settings.getMaxTemperature()));
        minPhEditText.setText(String.valueOf(settings.getMinPh()));
        maxPhEditText.setText(String.valueOf(settings.getMaxPh()));
        minOxygenEditText.setText(String.valueOf(settings.getMinOxygen()));
        maxOxygenEditText.setText(String.valueOf(settings.getMaxOxygen()));
        minWaterLevelEditText.setText(String.valueOf(settings.getMinWaterLevel()));
        maxWaterLevelEditText.setText(String.valueOf(settings.getMaxWaterLevel()));

        saveSettingsButton.setEnabled(true);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            doNotDisturbStartTimePicker.setHour(settings.getDoNotDisturbStartHour());
            doNotDisturbStartTimePicker.setMinute(0);
            doNotDisturbEndTimePicker.setHour(settings.getDoNotDisturbEndHour());
            doNotDisturbEndTimePicker.setMinute(0);
        } else {
            doNotDisturbStartTimePicker.setCurrentHour(settings.getDoNotDisturbStartHour());
            doNotDisturbStartTimePicker.setCurrentMinute(0);
            doNotDisturbEndTimePicker.setCurrentHour(settings.getDoNotDisturbEndHour());
            doNotDisturbEndTimePicker.setCurrentMinute(0);
        }
    }

    private void executeLogout() {
        firebaseAuth.signOut();
        View bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new com.example.smartaquarium.ui.login.LoginFragment())
                .commit();
        Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
    }

    private void showSaveConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Save")
                .setMessage("Are you sure you want to save these changes?")
                .setPositiveButton("Save", (dialog, which) -> executeSaveSettings())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout", (dialog, which) -> executeLogout())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setIcon(R.drawable.ic_logout)
                .create()
                .show();
    }

    private void disableUiComponents() {
        minTemperatureEditText.setEnabled(false);
        maxTemperatureEditText.setEnabled(false);
        minPhEditText.setEnabled(false);
        maxPhEditText.setEnabled(false);
        minOxygenEditText.setEnabled(false);
        maxOxygenEditText.setEnabled(false);
        minWaterLevelEditText.setEnabled(false);
        maxWaterLevelEditText.setEnabled(false);
        doNotDisturbStartTimePicker.setEnabled(false);
        doNotDisturbEndTimePicker.setEnabled(false);
        saveSettingsButton.setEnabled(false);
        logoutButton.setEnabled(false);
        saveSettingsButton.setText("Log in to change settings");
    }
}
