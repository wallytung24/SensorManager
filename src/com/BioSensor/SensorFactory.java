package com.BioSensor;

import BioHarnessSensor.BioharnessSensor;
import ShimmerSensor.ShimmerFramework;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

/**
 * Factory class to generate necessary ISensor implementations.
 */
public class SensorFactory
{
	/**
	 * The Data Key for the Zepyr Bioharness heart rate variance sensor.
	 */
	final public static int HRV_BioHarness 			= 0;
	/**
	 * The Data Key for the Zephyr Bioharness breathing rate sensor.
	 */
	final public static int BR_BioHarness 			= 1;
	/**
	 * The Data Key for the Zephyr Bioharness breathing rate sensor.
	 */
	final public static int HR_BioHarness 			= 2;
	/**
	 * The Data Key for the Zephyr Bioharness heart rate sensor.
	 */
	final public static int SCL_Shimmer 			= 3;
	/**
	 * The Data Key for the Shimmer GSR skin conductance level sensor.
	 */
	final public static int SCR_Shimmer 			= 4;
	/**
	 * The Data Key for the Shimmer GSR skin conductance response sensor.
	 */
	final public static int StressLevel_BioHarness 	= 5;
	/**
	 * The Data Key for the Zephyr Bioharness stress level.
	 */
	final public static int StressLevel_Shimmer 	= 6;
	/**
	 * The Data Key for the Shimmer GSR stress level.
	 */


	/**
	 *Creates the appropriate ISensor implementation based on given parameters.
	 *Currently this is used for the zephyr m
	 *@param name string giving the human-readable name of the device being instantiated
	 *@param b_adapter The bluetooth adapter on the android device
	 *
	 *@returns The ISensor implementation for the specified device.
	 *
	 *@throws InvalidArgumentException If 
	 */
	public static ISensor getSensor(String name, BluetoothAdapter b_adapter) throws IllegalArgumentException
	{
		if(name.equals("BioHarness"))
		{

			return new BioharnessSensor(b_adapter);
		}
//		else if(name.equals("Shimmer"))
//		{
//			return new ShimmerFramework(b_adapter, 60, context);
//		}
		else
		{
			throw new IllegalArgumentException("No implementation exists for " + name);
		}
	}

	/**
	 * Functions the same as getSensor(String,BluetoothAdapter), but is used by the shimmer since it needs
	 * a context for establishing the connection.
	 * @param context The application context used by the shimmer android library in the connect function
	 * 
	 * @see #getSensor(String, BluetoothAdapter)
	 */
	public static ISensor getSensor(String name, BluetoothAdapter b_adapter, Context context) throws IllegalArgumentException
	{
		if(name.equals("Shimmer"))
		{
			return new ShimmerFramework(b_adapter, 60, context);
		}
		else
		{
			throw new IllegalArgumentException("No implementation exists for " + name);
		}
	}
}