package ShimmerSensor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.BioSensor.ISensor;
import com.shimmerresearch.driver.Shimmer;


public class ShimmerFramework implements ISensor{

	private float []Thresholds;
	private File scrFile,sclFile;
	private PrintWriter sclBuf, scrBuf;
	private boolean streaming;
	private BluetoothAdapter bluetoothAdapter;
	//Lock lock,arrayLock;
	private float sclMax, sclRaw, scrMax, scrRaw;
	private double scrWindow, gsrRaw;
	private final double SAMPLE_RATE=25;
	private Thread scrThread;
	ArrayList <Double> GSRData;
	ArrayList <Double> SCLData;

	private String [] Sensors;
	
	private Shimmer mShimmer;
	private BluetoothDevice mDevice;
	//private UUID btUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
	


	public ShimmerFramework(BluetoothAdapter b_adapter,double window, Context context)
	{
		Log.d("Shimmer",Environment.getExternalStorageDirectory().getAbsolutePath());
		Date now = new Date();
		File root= new File(Environment.getExternalStorageDirectory(),"ShimmerData");
		if(!root.exists()){
			root.mkdir();
		}
		scrFile=new File(root,"GSR_Log_"+(now.getTime()/5000));
		sclFile=new File(root,"SCL_Log_"+(now.getTime()/5000));
		
		bluetoothAdapter = b_adapter;
		mShimmer=new Shimmer(context,dataHandler,"RightArm",false);
		Set<BluetoothDevice> pairedDevices = b_adapter.getBondedDevices();
		// If there are paired devices
		if (pairedDevices.size() > 0) {
			// Loop through paired devices
			for(BluetoothDevice device : pairedDevices) {
				if(device.getName().startsWith("RN42")) {
					mDevice = device;
					Log.d("Debug", mDevice.getName());
					break;
				}
			}
		}

		Sensors = new String[2];
		Sensors[0] = "SCR";
		Sensors[1] = "SCL";

		Thresholds = new float[2];
		Thresholds[0] = 30;
		Thresholds[1] = 2;

		GSRData = new ArrayList <Double>();
		SCLData = new ArrayList <Double>();


		sclMax=0;
		sclRaw=0;
		scrMax=0;
		scrRaw=0;
		scrWindow=window;

		scrThread = new gsrThread();
		streaming = false;
	}

	public void connect()
	{	
		Log.d("Debug", mDevice.getName() + '\t' + mDevice.getAddress());
		mShimmer.connect(mDevice);
		while(mShimmer.getState()==Shimmer.STATE_CONNECTING){
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		mShimmer.writeEnabledSensors(Shimmer.SENSOR_GSR);
		mShimmer.writeSamplingRate(SAMPLE_RATE);
		mShimmer.startStreaming();
		streaming = true;
		scrThread.start();
	}

	public void disconnect()
	{
		mShimmer.stopStreaming();
		streaming = false;
		try {
			scrThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		GSRData.clear();
		SCLData.clear();
		Log.d("Shimmer","Shimmer Disconnect properly.");
	}

	public double getRawData(String sensorName)
	{
		if(sensorName.equals("SCR"))
			return scrRaw;
		else if(sensorName.equals("SCL"))
			return sclRaw;
		else
			throw new IllegalArgumentException("Invalid Sensor Name:"+sensorName);
	}
	
    public int getAdjustedData(String[] sensorNames)
	{
		float total=0;
		float weight=0;
		float weightSCL=0.5f;
		float weightSCR=1;
		for(int i=0;i<sensorNames.length;i++){
			if(sensorNames[i].equals("SCL")){
				if(sclMax>Thresholds[1]&&sclRaw>Thresholds[1])
					total+=((sclMax-sclRaw)/(sclMax-Thresholds[1]))*(100*weightSCL);
					weight+=weightSCL;
				
			}
			if(sensorNames[i].equals("SCR")){
				if(scrMax>Thresholds[0]&&scrRaw>Thresholds[0])
					total+=((scrMax-scrRaw)/(scrMax-Thresholds[0]))*(100*weightSCR);
				weight+=weightSCR;
			}
		}
		//Avoid divide by 0 errors
		if(sensorNames.length==0)
			return 0;
		//Average out the sensor readings
		return (int)((total)/weight);
	}

	
	public int getStressLevel(String[] sensorNames)
	{
		return getAdjustedData(sensorNames);
	}

	
	public String[] getSensorList()
	{
		return Sensors;
	}

	
	@Override
	public void setThreshold(String sensorName, float threshold) throws IllegalArgumentException
	{
		if(sensorName.equals("SCR")){
			Thresholds[0]=threshold;
			scrMax=threshold;
		}
		else if(sensorName.equals("SCL")){
			Thresholds[1]=threshold;
			sclMax=threshold;
		}
		else{
			throw new IllegalArgumentException("Invalid Shimmer Sensor Name:"+sensorName);
		}
	}

	@Override
	public float[] getThresholds(String[] sensorName) throws IllegalArgumentException
	{
		float[] retThresh = new float[sensorName.length];
		for (int i=0; i<sensorName.length;i++){
			if(sensorName[i].equals("SCR")){
				retThresh[i]=Thresholds[0];
			}
			else if(sensorName[i].equals("SCL")){
				retThresh[i]=Thresholds[1];
			}
			else{
				throw new IllegalArgumentException("Invalid Shimmer Sensor Name:"+sensorName[i]);
			}
		}
		return retThresh;
	}
	@Override
	public void setUpperThreshold(String sensorName, float threshold)
			throws IllegalArgumentException {
		if(sensorName.equals("SCR")){
			scrMax=threshold;
		}
		else if(sensorName.equals("SCL")){
			sclMax=threshold;
		}
		else{
			throw new IllegalArgumentException("Invalid Shimmer Sensor Name:"+sensorName);
		}
	}

	@Override
	public float[] getUpperThresholds(String[] sensorName)
			throws IllegalArgumentException {
		float[] retThresh = new float[sensorName.length];
		for (int i=0; i<sensorName.length;i++){
			if(sensorName[i].equals("SCR")){
				retThresh[i]=scrMax;
			}
			else if(sensorName[i].equals("SCL")){
				retThresh[i]=sclMax;
			}
			else{
				throw new IllegalArgumentException("Invalid Shimmer Sensor Name:"+sensorName[i]);
			}
		}
		return retThresh;
	}

	
	public void calibrate(String[] sensorName) throws IllegalArgumentException
	{
		
		if(sensorName.length==0)
			return;
		
		boolean containsSCL=false;		
		boolean containsSCR=false;

		for(String sensor:sensorName){
			if(sensor.equals("SCL")){
				containsSCL=true;
			}
			else if(sensor.equals("SCR")){
				containsSCR=true;
			}
			else{
				throw new IllegalArgumentException("Invalid Shimmer Sensor Name:"+sensor);
			}
		}
		//Make certain the scr window is filled up before calibrating scr
		if(containsSCR){
			try {
				Thread.sleep((int)(scrWindow*1000));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//Otherwise make certain that the SCLWindow is filled up
		else if(containsSCL){
			try {
				Thread.sleep((int)(2000));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//get 30 seconds worth of readings
		int numReadings=(int)((int)(SAMPLE_RATE*30));
		double sclThreshold=0;
		double scrThreshold=0;

		if(containsSCL){
			sclMax=0;
		}
		if(containsSCR){
			scrMax=0;
		}

		for(int i=0;i<numReadings;i++){
			//Wait until there is a new reading
			if(containsSCL){
				sclThreshold+=sclRaw;
				if(sclRaw>sclMax)
					sclMax=sclRaw;
			}
			if(containsSCR){
				scrThreshold+=scrRaw;
				if(scrRaw>scrMax)
					scrMax=scrRaw;
			}
			try {
				Thread.sleep((int) ((1/SAMPLE_RATE) * 1000));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		sclThreshold/=numReadings;
		scrThreshold/=numReadings;
		if(containsSCR)
			Thresholds[0]=(float)scrThreshold;
		if(containsSCL)
			Thresholds[1]=(float)sclThreshold;

	}
	
	
	final Handler dataHandler = new Handler(Looper.getMainLooper())
	{ 
		  public void handleMessage(Message msg)
		  {
			  switch (msg.what)
			  { 
			  	case Shimmer.MESSAGE_READ: 
			  		//Log.d("Shimmer",msg.obj.toString());
				  if ((msg.obj instanceof com.shimmerresearch.driver.ObjectCluster))
				  { 
					  com.shimmerresearch.driver.ObjectCluster objectCluster = (com.shimmerresearch.driver.ObjectCluster) msg.obj;
					  //Log.d("Shimmer","Hello-2");
					  if (objectCluster.mMyName=="RightArm")
					  { 
						 // Log.d("Shimmer","Hello-3");
						  Collection<com.shimmerresearch.driver.FormatCluster> GSRFormats = objectCluster.mPropertyCluster.get("GSR"); 
						  if (GSRFormats != null)
						  { 
							  com.shimmerresearch.driver.FormatCluster formatCluster =  ((com.shimmerresearch.driver.FormatCluster)objectCluster.returnFormatCluster(GSRFormats,"Calibrated"));
							  
							  SCLData.add(formatCluster.mData);
							  if(SCLData.size() >= (int)(SAMPLE_RATE*2))
							  {
								  double sclTemp = 0;
								  for(int i = 0 ; i < SCLData.size() ; i++)
								  {
									  sclTemp= sclTemp + (SCLData.get(i).floatValue());
								  }
								  //Obtain the value in microsiemens since the gsr outputs by default in Kohms
								  //This is Ohms/1000 so the inverse = 1000/ohms ,which is millisiemesn
								  //So multiply by an additional 1000 to get microsiemens
								  sclTemp = sclTemp / 40.0;
								  sclTemp = 1000.0 / sclTemp;
								  sclRaw = (float) sclTemp;
								  if(sclRaw>sclMax){
									  sclMax=sclRaw;
								  }
								  SCLData.remove(0);
							  }
							  //Average 
							  if(SCLData.size() >= 5){
								  double gsrTemp = 0;
								  for(int i= SCLData.size()-1; i>SCLData.size()-5 ; i--){
									  gsrTemp += (SCLData.get(i).floatValue());
								  }
								  gsrTemp = gsrTemp / 5.0;
								  gsrTemp = 1000.0 / gsrTemp;
								  gsrRaw = gsrTemp;
							  }
						  }  
					  } 
				  }
			  } 
		  } 
	  };
	  
	  public class gsrThread extends Thread{
		  
		  private ArrayList <Integer> peaks;
		  private double threshold;
		  private int bottom;
		  private int top;
		  private int position;
		  private boolean increase;
		  public gsrThread(){
			  super();
			  peaks = new ArrayList <Integer>();
			  Log.d("Shimmer GSR", "Current scr:"+scrRaw);
			  threshold=.015;
			  bottom=0;
			  top=0;
			  position=0;
			  increase=true;
		  }
		  
		  public void run(){
			  
			  try {
				sclBuf = new PrintWriter(new FileWriter(sclFile));
				scrBuf = new PrintWriter(new FileWriter(scrFile));
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			  while(streaming){
				  try 
				  {
					  super.sleep(100);
				  } 
				  catch (InterruptedException e) 
				  {
					  break;
				  }
				  
				  GSRData.add(gsrRaw);
				  getPeaks(position);
				  
				  for(int i=peaks.size()-1; i >= 0; i--){
					  if(peaks.get(i).intValue() < (position-scrWindow*10)){
						  //All prior checked peaks except for the last one
						  scrRaw=peaks.size()-i-1;
						  break;
					  }
					  if(i==0){
						  scrRaw=peaks.size();
					  }
				  }
				 
				String buf=position/10.0+"\tSCL:"+gsrRaw+'\n';
				Log.d("sclBuf",buf);
				sclBuf.append(buf);
				  
				  
				  if(scrRaw>scrMax)
					  scrMax=scrRaw;
				  
				  position++;
			  }
			  
				sclBuf.close();
				scrBuf.close();
			
			
		  }
		  
		  public void getPeaks(int pos){
			if(pos==0)
			{
				return;
			}
			else 
			{
				if (increase)
				{
					if (GSRData.get(pos-1).doubleValue() < GSRData.get(pos).doubleValue())
					{
						return;
					}
					else
					{
						top = pos-1;
						if (top == bottom)
						{
							bottom = pos;
							increase = false;
							return;
						}
						double slope = (GSRData.get(top).doubleValue() - GSRData.get(bottom).doubleValue())/(top - bottom);
						increase = false;
						if (slope >= threshold)
						{
							
							//try {
								String buf = "Top:"+top/10.0+"\tBottom:"+bottom/10.0+'\n';
								Log.d("scrBuf",buf);
								scrBuf.append(buf);
							/*} 
							catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							*/
							peaks.add(top);
						}
					}
				}
				else
				{
					if (GSRData.get(pos-1).doubleValue() > GSRData.get(pos).doubleValue())
					{
						return;
					}
					else
					{
						bottom = pos-1;
						increase = true;
					}
				}
			}
		}
	}
	
	@Override
	public void setBluetoothAdapter(BluetoothAdapter adapter) {
		// TODO Auto-generated method stub
		bluetoothAdapter=adapter;
	}

	@Override
	public BluetoothAdapter getBluetoothAdapter() {
		// TODO Auto-generated method stub
		return bluetoothAdapter;
	}

	@Override
	public String getDeviceName() {
		// TODO Auto-generated method stub
		return "Shimmer";
	}

	
}


