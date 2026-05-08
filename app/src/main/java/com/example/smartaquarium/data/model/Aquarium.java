package com.example.smartaquarium.data.model;

/**
 * Data model representing an individual Aquarium.
 * This class serves as a Plain Old Java Object (POJO) to hold information
 * about a specific aquarium, such as its unique identifier and name.
 * It's designed to be easily serialized and deserialized, particularly for
 * use with Firestore.
 */
public class Aquarium {
    private String id;
    private String name;

    /**
     * Public no-argument constructor required by Firestore for deserialization.
     * When Firestore retrieves data, it uses this constructor to create an instance
     * before populating it with data from the document.
     */
    public Aquarium() {}

    /**
     * Constructs an {@link Aquarium} object with an ID and a name.
     * This constructor is useful for creating {@link Aquarium} objects programmatically
     * within the application.
     *
     * @param id   The unique identifier for the aquarium.
     * @param name The name of the aquarium.
     */
    public Aquarium(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Gets the unique identifier of the aquarium.
     *
     * @return The aquarium's ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the aquarium.
     *
     * @param id The ID to set for the aquarium.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the name of the aquarium.
     *
     * @return The aquarium's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the aquarium.
     *
     * @param name The name to set for the aquarium.
     */
    public void setName(String name) {
        this.name = name;
    }
}
