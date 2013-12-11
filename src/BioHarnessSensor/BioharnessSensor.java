package BioHarnessSensor;

import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import zephyr.android.BioHarnessBT.BTClient;
import zephyr.android.BioHarnessBT.ZephyrProtocol;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.BioSensor.ISensor;

public class BioharnessSensor implements ISensor {

	static Message respMessage;
	static Message skinMessage;
	static Message heartMessage;
	static Message hrvMessage;
	
	
	
	double windowSize = 30.0; // 30 seconds default

	protected final static int HRV = 0;
	private final static int HEART_RATE = 0x100;
	private final static int RESPIRATION_RATE = 0x101;
	private final static int SKIN_TEMPERATURE = 0x102;

	// Bioharness Framework

	String TAG = "BioHarness Sensor";


	// Bluetooth variables
	BluetoothAdapter bAdapter = null;
	BTClient _bt;
	ZephyrProtocol _protocol;
	NewConnectedListener _NConnListener;
	BluetoothDevice bDevice;
	boolean connected = false;
	static boolean writeable = false;

	//private ConnectThread mConnectThread;
	//private ConnectedThread mConnectedThread;
	//private int mState;

	// Lists of measurements, in case they are wanted to be saved to a file
	ArrayList<Float> respRates = new ArrayList<Float>();
	ArrayList<Float> skinTemps = new ArrayList<Float>();
	ArrayList<Float> heartRates = new ArrayList<Float>();
	ArrayList<Float> hrvs = new ArrayList<Float>();
	float[] thresholds = new float[4];
	float[] upperThreshold = new float[4];
	
	
	// Current Measurements and Values
	double respRate = 0.0;
	double skinTemp = 0.0;
	double heartRate = 0.0;
	double hrv = 0.0;
	int stressLevel = 0;
	
	
	// Calibration Variables
	ArrayList<Float> calRespRates = new ArrayList<Float>();
	ArrayList<Float> calSkinTemps = new ArrayList<Float>();
	ArrayList<Float> calHeartRates = new ArrayList<Float>();
	ArrayList<Float> calHrvs = new ArrayList<Float>();
	
	// Calibration Booleans: True if in calibration mode
	boolean calibrateResp = false;
	boolean calibrateSkin = false;
	boolean calibrateHeart = false;
	boolean calibrateHRV = false;
	
	// Measurement Booleans: True if current readings are beyond threshold
	boolean respUp = false;
	boolean skinUp = false;
	boolean heartUp = false;
	boolean hrvDown = false;
	
	// Lists for Updating Thresholds During Use
	ArrayList<Float> respsAbove = new ArrayList<Float>();
	ArrayList<Float> skinsAbove = new ArrayList<Float>();
	ArrayList<Float> heartsAbove = new ArrayList<Float>();
	ArrayList<Float> hrvsBelow = new ArrayList<Float>();
	
	
	public UUID mSPP_UID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
	public BluetoothSocket mmSocket;

	public BioharnessSensor(BluetoothAdapter b_adapter) {
		bAdapter = b_adapter;
		//_bt = b_bt;
		//bDevice = device;
		
		Set<BluetoothDevice> pairedDevices = bAdapter.getBondedDevices();
		String BhMacID = "00:07:80:9D:8A:E8";
		
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device1 : pairedDevices) {
				if (device1.getName().startsWith("BH")) {
					bDevice = device1;
					BhMacID = bDevice.getAddress();
					break;
				}
			}
			
			_bt = new BTClient(bAdapter, BhMacID);
			_NConnListener = new NewConnectedListener(BioHarnessHandler,BioHarnessHandler);
			_bt.addConnectedEventListener(_NConnListener);
		}
		
		thresholds[0] = 10;
		thresholds[2] = 97;
		thresholds[3] = 42000;
		
		upperThreshold[0] = (float) (thresholds[0]*1.5);
		upperThreshold[2] = (float) (thresholds[2]*1.5);
		upperThreshold[3] = (float) (thresholds[3]*0.5);
	}
	
	@Override
	public void connect() {
//		Connect connection = new Connect(bAdapter);
//		connection.run();
		
		if(_bt.IsConnected()) {
			// starting Bluetooth connection
			_bt.start();

			Log.i(TAG, "Zephyr Connected");

			connected = true;
		}
		else {
			// exit if connection fails
			Log.w("BlueTooth Connection Failed", "The bluetooth connection failed");

			Log.i(TAG, "Zephyr not connected");

			connected = false;
		}
		
	}

	@Override
	public void disconnect() {
		if(connected){
			//Disconnect sensor
			//Disconnect listener from received messages
			_bt.removeConnectedEventListener(_NConnListener);
			//Close the connection to device
			_bt.Close();

			connected = false;
		}

		Log.i(TAG, "Devices Disconnected");
	}

	@Override
	public double getRawData(String sensorName) throws IllegalArgumentException {
		double data = 0.0;
		if(sensorName.equals("Respiration Rate")) data = respRate;
		else if (sensorName.equals("Skin Temperature")) data = skinTemp;
		else if(sensorName.equals("Heart Rate")) data = heartRate;
		else if(sensorName.equals("HRV")) data = hrv;
		else throw new IllegalArgumentException("Error 404:" + sensorName + " not found");
		return data;
	}
	
	public int getAdjustedData(String[] sensorNames) throws IllegalArgumentException{
		int stressLevel = 0;
		boolean respCheck =false, skinCheck =false, heartCheck =false, hrvCheck =false;
		int respStress =0, skinStress =0, heartStress =0, hrvStress =0;
		// For each sensor in sensorNames, perform stress calculation using that sensor's data
		//throw new IllegalArgumentException("Error 404: Sensor not found");
		for(int i = 0; i<sensorNames.length; i++){
			if(sensorNames[i].equals("Respiration Rate")) {respStress = stressResp(); respCheck=true;}
			else if (sensorNames[i].equals("Skin Temperature")) {skinStress = stressSkin(); skinCheck=true;}
			else if(sensorNames[i].equals("Heart Rate")) {heartStress = stressHeart(); heartCheck=true;}
			else if(sensorNames[i].equals("HRV")) {hrvStress = stressHrv(); hrvCheck=true;}
			else throw new IllegalArgumentException("Error 404: Sensor not found");
		}
		
		int stressSum = 0;
		if(respCheck) stressSum = stressSum+respStress;
		if(skinCheck) stressSum = stressSum+skinStress;
		if(heartCheck) stressSum = stressSum+heartStress;
		if(hrvCheck) stressSum = stressSum+hrvStress;
		
		if(sensorNames.length>0)
			stressLevel = stressSum/sensorNames.length;
		else 
			stressLevel = 0;
		return stressLevel;
	}
	
	private int stressResp(){
		float stress = 0;
		
		stress = (float) getRawData("Respiration Rate");
		
		stress = (float) (100 * (1 - ((upperThreshold[0] - stress)/(upperThreshold[0] - thresholds[0]))));
		
		return (int) stress;
	}
	
	private int stressSkin(){
		float stress = 0;
		
		stress = (float) getRawData("Skin Temperature");
		
		stress = (float) (100 * (1 - ((upperThreshold[1] - stress)/(upperThreshold[1] - thresholds[1]))));
		
		return (int) stress;
		
	}
	
	private int stressHeart(){
		float stress = 0;
		
		stress = (float) getRawData("Heart Rate");
		
		stress = (float) (100 * (1 - ((upperThreshold[2] - stress)/(upperThreshold[2] - thresholds[2]))));
		
		return (int) stress;
		
	}
	
	private int stressHrv(){
		float stress = 0;
		
		stress = (float) getRawData("HRV");
		
		if(stress==0) { return (int) 0;}
		else {
		stress = (float) (100 * (1 - ((thresholds[3] - stress)/(thresholds[3] - upperThreshold[3]))));
		
		return (int) stress;
		}
		
	}

	public String[] getSensorList(){
		String[] sensors = {"Respiration Rate","Skin Temperature", "Heart Rate", "HRV"};	

		return sensors;
	}

	@Override
	public void setThreshold(String sensorName, float threshold)
			throws IllegalArgumentException {
		if(sensorName.equals("Respiration Rate")){ thresholds[0] = threshold; }
		else if(sensorName.equals("Skin Temperature")){ thresholds[1] = threshold; }
		else if(sensorName.equals("Heart Rate")){ thresholds[2] = threshold; }
		else if(sensorName.equals("HRV")){ thresholds[3] = threshold; }
		else throw new IllegalArgumentException("Error 404: Sensor not found");
	}

	@Override
	public float[] getThresholds(String[] sensorName)
			throws IllegalArgumentException {
		float[] data = new float[sensorName.length];
		
		for(int i = 0; i<sensorName.length; i++){
			if(sensorName[i].equals("Respiration Rate")) data[i] = thresholds[0];
			else if (sensorName[i].equals("Skin Temperature")) data[i] = thresholds[1];
			else if(sensorName[i].equals("Heart Rate")) data[i] = thresholds[2];
			else if(sensorName[i].equals("HRV")) data[i] = thresholds[3];
			else throw new IllegalArgumentException("Error 404: Sensor not found");
		}
		
		return data;
	}
	
	@Override
	public void setUpperThreshold(String sensorName, float threshold)
			throws IllegalArgumentException {
		if(sensorName.equals("Respiration Rate")){ upperThreshold[0] = threshold; }
		else if(sensorName.equals("Skin Temperature")){ upperThreshold[1] = threshold; }
		else if(sensorName.equals("Heart Rate")){ upperThreshold[2] = threshold; }
		else if(sensorName.equals("HRV")){ upperThreshold[3] = threshold; }
		else throw new IllegalArgumentException("Error 404: Sensor not found");
	}

	@Override
	public float[] getUpperThresholds(String[] sensorName)
			throws IllegalArgumentException {
		float[] data = new float[sensorName.length];
		
		for(int i = 0; i<sensorName.length; i++){
			if(sensorName[i].equals("Respiration Rate")) data[i] = upperThreshold[0];
			else if (sensorName[i].equals("Skin Temperature")) data[i] = upperThreshold[1];
			else if(sensorName[i].equals("Heart Rate")) data[i] = upperThreshold[2];
			else if(sensorName[i].equals("HRV")) data[i] = upperThreshold[3];
			else throw new IllegalArgumentException("Error 404: Sensor not found");
		}
		
		return data;
	}
	

	@Override
	public void calibrate(String[] sensorName) throws IllegalArgumentException {
		writeable = true;
		
		for(int i = 0; i<sensorName.length; i++){
			if(sensorName[i].equals("Respiration Rate")) calibrateResp();
			else if (sensorName[i].equals("Skin Temperature")) calibrateTemp();
			else if(sensorName[i].equals("Heart Rate")) calibrateHrtRate();
			else if(sensorName[i].equals("HRV")) calibrateHRV();
			else throw new IllegalArgumentException("Error 404: Sensor not found");
		}
		
	}
	
	private void calibrateResp(){
		//thresholds[0] = 20; // 20 is upper limit of normal breathing rate of adults
		
		Timer respTimer = null;
		calRespRates.clear();
		calibrateResp = true;
		class respCalibration extends TimerTask{

			@Override
			public void run() {
				calibrateResp = false;
				float sum = 0;
				float max = 0;
				for(int i = 0; i<calRespRates.size(); i++){
					sum = sum + calRespRates.get(i);
					if(max < calRespRates.get(i)) max = calRespRates.get(i);
				}
				thresholds[0] = max;
			}
			
		}
		respTimer = new Timer();
		respTimer.schedule(new respCalibration(), (long) (windowSize*1000));
		
		while(calibrateResp){
			calRespRates.add((float) getRawData("Respiration Rate"));
		}
	}
	
	private void calibrateTemp(){
		Timer skinTimer = null;
		calSkinTemps.clear();
		calibrateSkin = true;
		class tempCalibration extends TimerTask{

			@Override
			public void run() {
				calibrateSkin = false;
				float sum = 0;
				float max = 0;
				for(int i = 0; i<calSkinTemps.size(); i++){
					sum = sum + calSkinTemps.get(i);
					if(max < calSkinTemps.get(i)) max = calSkinTemps.get(i);
				}
				thresholds[1] = max;
			}
			
		}
		skinTimer = new Timer();
		skinTimer.schedule(new tempCalibration(), (long) (windowSize*1000));
		
		while(calibrateSkin){
			calSkinTemps.add((float) getRawData("Skin Temperature"));
		}
	}
	
	private void calibrateHrtRate(){
		Timer hrtTimer = null;
		calHeartRates.clear();
		calibrateHeart = true;
		class heartCalibration extends TimerTask{

			@Override
			public void run() {
				calibrateHeart = false;
				float sum = 0;
				float max = 0;
				for(int i = 0; i<calHeartRates.size(); i++){
					sum = sum + calHeartRates.get(i);
					if(max < calHeartRates.get(i)) max = calHeartRates.get(i);
				}
				thresholds[2] = max;
			}
			
		}
		hrtTimer = new Timer();
		hrtTimer.schedule(new heartCalibration(), 120000);
		
		while(calibrateHeart){
			calHeartRates.add((float) getRawData("Heart Rate"));
		}
	}
	
	private void calibrateHRV(){
		Timer hrvTimer = null;
		calHrvs.clear();
		calibrateHRV = true;
		class hrvCalibration extends TimerTask{

			@Override
			public void run() {
				calibrateHRV = false;
				float sum = 0;
				float min = 9999;
				for(int i = 0; i<calHrvs.size(); i++){
					sum = sum + calHrvs.get(i);
					if(min > calHrvs.get(i)) min = calHrvs.get(i);
				}
				thresholds[3] = min;
			}
			
		}
		hrvTimer = new Timer();
		hrvTimer.schedule(new hrvCalibration(), (long) (windowSize*1000));
		
		while(calibrateHRV){
			calHrvs.add((float) getRawData("HRV"));
		}
	}
	
	final Handler BioHarnessHandler = new Handler(Looper.getMainLooper()) {
		public void handleMessage(Message msg) {
			switch(msg.what){
			case Global.RESPIRATION_RATE:
				respRate = getRespRate(msg);
				//Log.d("BioHarnessSensor", "Got resp rate");
				break;
			case Global.SKIN_TEMPERATURE:
				skinTemp = getSkinTemp(msg);
				//Log.d("BioHarnessSensor", "Got skin temp");
				break;
			case Global.HEART_RATE:
				heartRate = getHeartRate(msg);
				//Log.d("BioHarnessSensor", "Got heart rate");
				break;
			case Global.HRV:
				hrv = getHRV(msg);
				//Log.d("BioHarnessSensor", "Got HRV");
				break;
			default:
				break;
			}
		}
	};

	private float getRespRate(Message msg) {
		float rate = 0;

		if(msg==null) {
			Log.d("BioHarnessSensor", "Null message2");
			return -1;
		}
		
		rate = msg.getData().getFloat("RespirationRate");
		if(writeable) respRates.add(rate);
		
		
		// For adjusting threshold while the game is running
		/*if(rate > thresholds[0]) {
			respUp = true;
			respsAbove.add(rate);
		}
		
		if(respUp) {
			if(rate < thresholds[0]) respUp = false;
		}
		
		if(respUp && respsAbove.size() >=60){
			int i =0;
			float sum = 0;
			
			for(i=0; i<respsAbove.size(); ++i)
				sum = sum + respsAbove.get(i);
			thresholds[0] = sum/respsAbove.size();
			respUp = false;
			
			respsAbove.clear();
		}
		*/

		return rate;
	}

	private float getSkinTemp(Message msg) {
		float temp = 0;

		temp = msg.getData().getFloat("SkinTemperature");
		if(writeable) skinTemps.add(temp);

		// For adjusting threshold while game is running
		/*
		if(temp > thresholds[1]) {
			skinUp = true;
			skinsAbove.add(temp);
		}
		
		if(skinUp) {
			if(temp < thresholds[1]) skinUp = false;
		}
		
		if(skinUp && skinsAbove.size() >=60){
			int i =0;
			float sum = 0;
			
			for(i=0; i<skinsAbove.size(); ++i)
				sum = sum + skinsAbove.get(i);
			thresholds[1] = sum/skinsAbove.size();
			skinUp = false;
			
			skinsAbove.clear();
		}
		*/
		
		return temp;
	}

	public float getHeartRate(Message msg){
		float heartRate = 0;

		heartRate = (float) msg.getData().getInt("HeartRate");
		if(writeable) heartRates.add(heartRate);

		// For adjusting threshold while game is running
		/*if(heartRate > thresholds[2]) {
			heartUp = true;
			heartsAbove.add(heartRate);
		}
		
		if(heartUp) {
			if(heartRate < thresholds[2]) heartUp = false;
		}
		
		if(heartUp && heartsAbove.size() >=60){
			int i =0;
			float sum = 0;
			
			for(i=0; i<heartsAbove.size(); ++i)
				sum = sum + heartsAbove.get(i);
			thresholds[2] = sum/heartsAbove.size();
			heartUp = false;
			
			heartsAbove.clear();
		}
		*/
		
		return heartRate;
	}

	private float getHRV(Message msg) {
		float hrv = 0;

		hrv = msg.getData().getInt("RMSSD");
		if(writeable) hrvs.add(hrv);
		
		// For adjusting threshold while game is running
		/*
		if(hrv < thresholds[3]) {
			hrvDown = true;
			hrvsBelow.add(hrv);
		}
		
		if(hrvDown) {
			if(hrv > thresholds[3]) hrvDown = false;
		}
		
		if(hrvDown && hrvsBelow.size() >=60){
			int i =0;
			float sum = 0;
			
			for(i=0; i<hrvsBelow.size(); ++i)
				sum = sum + hrvsBelow.get(i);
			thresholds[3] = sum/hrvsBelow.size();
			hrvDown = false;
			
			hrvsBelow.clear();
		}
		*/

		return hrv;
	}

	public double getData(String dataName, Message msg) throws IllegalArgumentException{

		double data = 0.0;

		if(dataName.equals("Respiration Rate")){ if(msg==null) Log.d("BioHarnessSensor", "Null Message 1"); data = getRespRate(msg); }
		else if(dataName.equals("Skin Temperature")){ data = getSkinTemp(msg); }
		else if(dataName.equals("Heart Rate")){ data = getHeartRate(msg); }
		else if(dataName.equals("HRV")){ data = getHRV(msg); }
		else throw new IllegalArgumentException("Error 404: Sensor not found");

		return data;
	}
	

	@Override
	public void setBluetoothAdapter(BluetoothAdapter adapter) {
		bAdapter = adapter;
		
	}

	
	@Override
	public BluetoothAdapter getBluetoothAdapter() {
		return bAdapter;
	}

	
	@Override
	public String getDeviceName() {
		return "BioHarness";
	}

	
	@Override
	public int getStressLevel(String[] sensorNames)
			throws IllegalArgumentException {
		int stressLevel = getAdjustedData(sensorNames);
		if(stressLevel > 100)
			stressLevel = 100;
		if(stressLevel < 0)
			stressLevel = 0;
		
		return stressLevel;
	}

	
	public void setWindowSize(double size){
		windowSize = size;
	}
	
}

