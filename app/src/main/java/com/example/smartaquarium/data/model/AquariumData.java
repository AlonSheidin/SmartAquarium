package com.example.smartaquarium.data.model;

import java.util.Date;

/**
 * Data model representing a single reading of aquarium sensor data.
 * This class holds the sensor values (temperature, pH, oxygen, water level)
 * and a timestamp for each reading. It is designed to be used with Firestore
 * for storing historical data and facilitating real-time monitoring and alerts.
 */
public class AquariumData {
    // Public fields are used for direct mapping with Firestore.
    // Data types are 'double' to accommodate precise sensor readings and align with Firestore's Number type.
    public double temperature;
    public double ph;
    public double oxygen;
    public double waterLevel;
    public Date timestamp;

    /**
     * Public no-argument constructor required by Firestore for deserialization.
     * Firestore uses this constructor to create an instance of AquariumData
     * before populating it with data from a Firestore document.
     */
    public AquariumData() {}

    /**
     * Constructor for convenience, allowing programmatic creation of AquariumData objects.
     * Initializes all sensor readings and sets the timestamp to the current time.
     *
     * @param temperature The temperature reading in degrees Celsius.
     * @param ph          The pH reading of the water.
     * @param oxygen      The dissolved oxygen level reading (e.g., in %).
     * @param waterLevel  The water level reading (e.g., in %).
     */
    public AquariumData(double temperature, double ph, double oxygen, double waterLevel) {
        this.temperature = temperature;
        this.ph = ph;
        this.oxygen = oxygen;
        this.waterLevel = waterLevel;
        this.timestamp = new Date(); // Initialize with current time
    }

    /**
     * Gets the temperature reading.
     *
     * @return The temperature value.
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * Sets the temperature reading.
     *
     * @param temperature The temperature value to set.
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /**
     * Gets the pH reading.
     *
     * @return The pH value.
     */
    public double getPh() {
        return ph;
    }

    /**
     * Sets the pH reading.
     *
     * @param ph The pH value to set.
     */
    public void setPh(double ph) {
        this.ph = ph;
    }

    /**
     * Gets the oxygen level reading.
     *
     * @return The oxygen level value.
     */
    public double getOxygen() {
        return oxygen;
    }

    /**
     * Sets the oxygen level reading.
     *
     * @param oxygen The oxygen level value to set.
     */
    public void setOxygen(double oxygen) {
        this.oxygen = oxygen;
    }

    /**
     * Gets the water level reading.
     *
     * @return The water level value.
     */
    public double getWaterLevel() {
        return waterLevel;
    }

    /**
     * Sets the water level reading.
     *
     * @param waterLevel The water level value to set.
     */
    public void setWaterLevel(double waterLevel) {
        this.waterLevel = waterLevel;
    }

    /**
     * Gets the timestamp of the data reading.
     *
     * @return The {@link Date} object representing when the reading was taken.
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp for the data reading.
     *
     * @param timestamp The {@link Date} object to set as the reading's timestamp.
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
