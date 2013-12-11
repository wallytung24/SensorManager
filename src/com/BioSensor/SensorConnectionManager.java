package com.BioSensor;

import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;

/*  SensorConnectionManager
 *      purpose: an abstraction layer to manage the multiple devices and their connections
 */

public class SensorConnectionManager
{
	//the Bluetooth connection adapter on the Android device
	private BluetoothAdapter bluetooth_adapter;
	//array of active sensor devices
	private ArrayList<ISensor> sensors = new ArrayList<ISensor>();
	//the list of human-readable names of all sensor devices for which there is an implementation of ISensor in the library
	private final String[] availableSensors =
		{"Shimmer",
		"BioHarness"};

	/*  Constructor
	 */
	public SensorConnectionManager(BluetoothAdapter adapter) {
		bluetooth_adapter = adapter;
	}

	/*  getSensor
	 *      purpose: retrieve the sensor device by name
	 *      parameters:
	 *          deviceName = a string of the human-readable name of the device
	 *      returns: ISensor implementation for the given device
	 */
	private ISensor getSensor(String deviceName) throws IllegalArgumentException
	{
		ISensor device = null;
		boolean d_set = false;
		for(ISensor d : sensors)
		{
			if((d.getDeviceName()).equals(deviceName))
			{
				device = d;
				d_set = true;
				break;
			}
		}
		if(!d_set)
		{
			throw new IllegalArgumentException("No active device named " + deviceName);
		}
		return device;
	}

	/*  getAvailableSensors
	 *      purpose: get the lis of sensor devices which have ISensor implementations
	 *      parameters: none
	 *      returns: string array of names of available sensors
	 */
	public String[] getAvailableSensors()
	{
		return availableSensors;
	}

	public ArrayList<String> getConnectedSensors()
	{
		ArrayList<String> connectedSensors = new ArrayList<String>();
		for(ISensor device : sensors)
		{
			connectedSensors.add(device.getDeviceName());
		}
		return connectedSensors;
	}

	/*  connectSensors
	 */
	 public void connectSensors(String[] sensor_list) throws IllegalArgumentException
	 {
		 //temporary, needs to be rewritten to use threads
		 for(String sensor_name : sensor_list)
		 {
			 try
			 {
				 ISensor sensor = null;
				 if (sensor_name.equals("BioHarness")) {
					 sensor = SensorFactory.getSensor(sensor_name, bluetooth_adapter);
				 }
				 else if(sensor_name.equals("Shimmer"))
					 sensor = SensorFactory.getSensor(sensor_name, bluetooth_adapter, null);
					 
				 sensor.setBluetoothAdapter(bluetooth_adapter);
				 sensor.connect();
				 sensors.add(sensor);
			 }
			 catch(IllegalArgumentException e)
			 {
				 throw e;
			 }
		 }
	 }

	 /*  disconnectSensors
	  */
	 public void disconnectSensors(String[] sensor_list) throws IllegalArgumentException
	 {
		 for(String sensor_name : sensor_list)
		 {
			 try
			 {
				 ISensor device = getSensor(sensor_name);
				 device.disconnect();
				 sensors.remove(device);
			 }
			 catch(IllegalArgumentException e)
			 {
				 throw e;
			 }
		 }
	 }

	 /*  getStressLevel
	  */
	 public int getStressLevel(String deviceName, String[] sensorNames) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 return device.getStressLevel(sensorNames);
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 /*  getRawData
	  */
	 public double getRawData(String deviceName, String sensorName) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 return device.getRawData(sensorName);
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 /*  getDeviceSensors
	  */
	 public String[] getDeviceSensors(String deviceName) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 return device.getSensorList();
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 /*  setThreshold
	  */
	 public void setThreshold(String deviceName, String sensorName, float threshold) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 device.setThreshold(sensorName, threshold);
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 /*  getThresholds
	  */
	 public float[] getThresholds(String deviceName, String[] sensorNames) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 return device.getThresholds(sensorNames);
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 /*  setUpperThreshold
	  */
	 public void setUpperThreshold(String deviceName, String sensorName, float threshold) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 device.setUpperThreshold(sensorName, threshold);
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 /*  getUpperThresholds
	  */
	 public float[] getUpperThresholds(String deviceName, String[] sensorNames) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 return device.getUpperThresholds(sensorNames);
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 /*  calibrate
	  */
	 public void calibrate(String deviceName, String[] sensorNames) throws IllegalArgumentException
	 {
		 try
		 {
			 ISensor device = getSensor(deviceName);
			 device.calibrate(sensorNames);
		 }
		 catch(IllegalArgumentException e)
		 {
			 throw e;
		 }
	 }

	 public void calibrateAll()
	 {
		 for(ISensor device : sensors)
		 {
			 try
			 {
				 calibrate(device.getDeviceName(), device.getSensorList());
			 }
			 catch(IllegalArgumentException e)
			 {
				 System.out.println(e.getMessage());
			 }
		 }
	 }

	 /*  getBluetoothAdapter
	  */
	  public BluetoothAdapter getBluetoothAdapter()
	 {
		  return bluetooth_adapter;
	 }

	  /*  setBluetoothAdapter
	   */
	  public void setBluetoothAdapter(BluetoothAdapter adapter)
	  {
		  bluetooth_adapter = adapter;
	  }
}