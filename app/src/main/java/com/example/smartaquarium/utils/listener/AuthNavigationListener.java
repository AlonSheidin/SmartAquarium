package com.example.smartaquarium.utils.listener;

/**
 * An interface to allow fragments to request navigation changes
 * from their hosting Activity, such as hiding UI elements and
 * navigating to an authentication screen.
 */
public interface AuthNavigationListener {
    /**     * Called when the app needs to navigate to the authentication flow,
     * typically after a user logs out.
     */
    void navigateToAuth();
}
