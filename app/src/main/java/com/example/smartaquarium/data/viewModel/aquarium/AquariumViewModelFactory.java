package com.example.smartaquarium.data.viewModel.aquarium;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

/**
 * Factory class for creating instances of {@link AquariumViewModel}.
 * This factory is responsible for passing the required dependencies, {@link Application} and {@link ViewModelStoreOwner},
 * to the ViewModel's constructor. It ensures that the ViewModel is instantiated with the necessary context
 * and lifecycle owner, which are crucial for its operation, particularly for handling Android framework components
 * and sharing data across different UI components that share the same {@link ViewModelStoreOwner}.
 */
public class AquariumViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final ViewModelStoreOwner owner;

    /**
     * Constructs an {@link AquariumViewModelFactory}.
     *
     * @param application The application context, used for accessing application-level resources.
     * @param owner       The {@link ViewModelStoreOwner} (e.g., Activity or Fragment) that owns this ViewModel.
     *                    This is essential for providing a scope for the ViewModel's lifecycle.
     */
    public AquariumViewModelFactory(@NonNull Application application, @NonNull ViewModelStoreOwner owner) {
        this.application = application;
        this.owner = owner;
    }

    /**
     * Creates a new instance of the given {@code Class}.
     * <p>
     * This method is part of the {@link ViewModelProvider.Factory} interface. It checks if the requested
     * {@code modelClass} is assignable from {@link AquariumViewModel}. If it is, a new instance of
     * {@link AquariumViewModel} is created and returned, injecting the {@code Application} and {@code ViewModelStoreOwner}
     * into its constructor.
     * If the requested {@code modelClass} is not {@link AquariumViewModel}, an {@link IllegalArgumentException} is thrown.
     *
     * @param modelClass a {@code Class} whose instance is requested, expected to be {@link AquariumViewModel}.
     * @param <T>        The type parameter for the ViewModel, ensuring type safety.
     * @return a newly created ViewModel instance of the requested type {@code T}.
     * @throws IllegalArgumentException if the given {@code modelClass} is not a known ViewModel class that this factory can instantiate (i.e., not {@link AquariumViewModel}).
     * @throws RuntimeException if an unexpected error occurs during the instantiation of {@link AquariumViewModel}.
     */
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(AquariumViewModel.class)) {
            try {
                // Instantiate AquariumViewModel, passing the application context and owner.
                return (T) new AquariumViewModel(application, owner);
            } catch (Exception e) {
                // Catch any exceptions during ViewModel construction and re-throw as a RuntimeException.
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            }
        }
        // If the modelClass is not AquariumViewModel, this factory cannot create it.
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
