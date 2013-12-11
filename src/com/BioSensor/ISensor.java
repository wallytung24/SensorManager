package com.BioSensor;

import android.bluetooth.BluetoothAdapter;

/**
 * 	This serves as an interface for the individual sensor classes, providing the guidelines for how they should be written.
 */

public interface ISensor
{
	/**
	 * Change the BluetoothAdapter for the device.
	 * @param adapter = the adapter which is paired with the device
	 */
	public void setBluetoothAdapter(BluetoothAdapter adapter);

	/**
	 * Return the adapter in use for the device
	 * @return the BluetoothAdapter used by the device
	 */
	public BluetoothAdapter getBluetoothAdapter();

	/**
	 * Get the human-readable name of the device.
	 * @return A string giving the name of the device.
	 */
	public String getDeviceName();

	 /**
     * Connects to the implemented sensor device
     */
	public void connect();

	/**
     * Disconnects the sensor from the device and does any necessary cleanup.
     */
	public void disconnect();

	/**
     * Obtains a float for the raw data value output by a sensor.
     * @param sensorName The name of which sensor (assuming the device has multiple) to retrieve raw data from
     * @throws IllegalArgumentException If an invalid sensorName is input as a parameter.
     * @return Data retrieved from the sensor, as a double (if the sensor returns an integer, it will be converted to a double)
     */
	public double getRawData(String sensorName) throws IllegalArgumentException;

    /**
     * 	Obtains the general stress level that the data portrays.
     *  @param sensorName A string array denoting which sensors (assuming the device has multiple) to read from
     *	@return A double in the range [0, 100] denoting the overall stress level (0 being completely stress-free and 100 being extremely stressed)
     */
	public int getStressLevel(String[] sensorNames) throws IllegalArgumentException;

	/**
     * Get a list of the names of all sensors available on this device; these are the possible parameters for getRawData and getAdjustedData.
     * @return A string array of the names of sensors available for reading from this device.
     */
	public String[] getSensorList();

	/**
	 * Sets the threshold level for the specified sensor.
	 * @param sensorName A string denoting which sensor to apply the threshold to.
	 * @param threshold A floating point number defining the nominal level for the sensor.
	 * @throws IllegalArgumentException If sensorName is an invalid sensor.
	 */
	public void setThreshold(String sensorName, float threshold) throws IllegalArgumentException;

	/**
	 *	Retrieves the current threshold level for the specified sensors
	 *	@param sensorName an array of strings denoting which sensors to retrieve the thresholds of.
	 *	@return An array of floats that represent the current thresholds of the sensors
	 *  @throws IllegalArgumentException If any of the strings in sensorName isn't supported.
	 *  @return An array of thresholds for each of the corresponding sensor names in sensorName.
	 */
	public float[] getThresholds(String[] sensorName) throws IllegalArgumentException;

	/**
	 * Sets the upper threshold level for the specified sensor.
	 * @param sensorName A string denoting which sensor to set the threshold of.
	 * @param threshold A floating point number defining the nominal level for the sensor
	 * @throws IllegalArgumentException If any of the strings in sensorName isn't a valid sensor.
	 */
	public void setUpperThreshold(String sensorName, float threshold) throws IllegalArgumentException;

	/**
	 * Retrieves the current upper threshold level for the specified sensors
	 * @param sensorNames = an array of strings denoting which sensors to retrieve the threshold for
	 * @return An array of thresholds for each of the corresponding sensor names in sensorName.
	 */
	public float[] getUpperThresholds(String[] sensorName) throws IllegalArgumentException;
	
	/**
	 * Performs an automated calibration for the given sensors.
	 * @param sensorName An array of strings denoting which sensors to calibrate.
	 */
	public void calibrate(String[] sensorName) throws IllegalArgumentException;
}