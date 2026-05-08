package com.example.smartaquarium.ui.registration;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartaquarium.R;
import com.example.smartaquarium.data.model.UserSettings;
import com.example.smartaquarium.service.UserSettingsService;
import com.example.smartaquarium.ui.main.MainActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegistrationFragment extends Fragment {

    private static final String TAG = "RegistrationFragment";

    // --- UI Components ---
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private Button registerButton;
    private Button goToLoginButton;
    private ProgressBar loadingProgressBar;

    // --- Firebase & Services ---
    private FirebaseAuth firebaseAuth;
    private UserSettingsService userSettingsService;

    public RegistrationFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_registration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Initialize everything after the view has been created
        initializeFragment(view);
    }

    /**
     * Main initialization function for the fragment.
     * It sets up UI components, services, and click listeners.
     */
    private void initializeFragment(View view) {
        initializeUiComponents(view);
        initializeServices();
        setupClickListeners();
    }

    /**
     * Initializes all UI components by finding them in the fragment's view.
     * @param view The root view of the fragment.
     */
    private void initializeUiComponents(View view) {
        emailEditText = view.findViewById(R.id.edit_text_email);
        passwordEditText = view.findViewById(R.id.edit_text_password);
        registerButton = view.findViewById(R.id.button_register);
        goToLoginButton = view.findViewById(R.id.button_go_to_login);
        loadingProgressBar = view.findViewById(R.id.loading_progress_bar);
    }

    /**
     * Initializes the services needed by this fragment, like Firebase Authentication.
     */
    private void initializeServices() {
        firebaseAuth = FirebaseAuth.getInstance();
        userSettingsService = new UserSettingsService(); // For creating default settings
    }

    /**
     * Sets up the click listeners for all the buttons on the screen.
     */
    private void setupClickListeners() {
        registerButton.setOnClickListener(v -> attemptRegistration());

        // When the "Go to Login" button is clicked, pop the back stack
        // to return to the LoginFragment.
        goToLoginButton.setOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });
    }

    /**
     * Gathers user input, validates it, and attempts to create a new user account.
     */
    private void attemptRegistration() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // --- Input Validation ---
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email address.");
            emailEditText.requestFocus();
            return;
        }
        if (password.isEmpty() || password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters long.");
            passwordEditText.requestFocus();
            return;
        }

        setLoadingState(true);

        // --- Firebase User Creation ---
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser newUser = firebaseAuth.getCurrentUser();
                        if (newUser != null) {
                            // After successful registration, create a default settings document for the new user.
                            createDefaultUserSettings(newUser.getUid());
                        }
                    } else {
                        // If sign up fails, display a message to the user.
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(getContext(), "Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                        setLoadingState(false);
                    }
                });
    }

    /**
     * Creates a default settings document in Firestore for the newly registered user.
     * After settings are created, navigates to the main activity.
     * @param userId The unique ID of the new user.
     */
    private void createDefaultUserSettings(String userId) {
        userSettingsService.saveSettingsForCurrentUser(new UserSettings()) // Save a new default UserSettings object
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Default user settings created successfully for user: " + userId);
                    // Proceed to the main app only after settings are created
                    goToMainActivity();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create default settings for user: " + userId, e);
                    // Even if settings fail to create, we can still proceed, but show an error.
                    Toast.makeText(getContext(), "Could not create default settings. Please save them manually.", Toast.LENGTH_LONG).show();
                    goToMainActivity();
                });
    }


    /**
     * Navigates the user to the main screen of the application after a successful registration.
     */
    private void goToMainActivity() {
        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            // Clear the activity stack so the user can't go back to the auth flow
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish(); // Close the hosting activity (e.g., AuthActivity or MainActivity)
        }
    }

    /**
     * Manages the UI state, showing/hiding the progress bar and enabling/disabling buttons.
     * @param isLoading True if the app is processing a request.
     */
    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            registerButton.setEnabled(false);
            goToLoginButton.setEnabled(false);
        } else {
            loadingProgressBar.setVisibility(View.GONE);
            registerButton.setEnabled(true);
            goToLoginButton.setEnabled(true);
        }
    }
}
