package com.sensormanager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

import com.BioSensor.SensorConnectionManager;

import android.R.raw;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

public class DataStreamActivity extends Activity {
	
	public BluetoothAdapter mBluetoothAdapter;
	private final int REQUEST_ENABLE_BT = 3;
	private TextView textviewbhbr;
	private TextView textviewbhhr;
	private TextView textviewbhhrv;
	private TextView textviewshimscl;
	private TextView textviewshimscr;
	private BlueToothThread btthread;
	
	final private static int HRV = 0;
	final private static int BR = 1;
	final private static int HR = 2;
	final private static int SCL = 3;
	final private static int SCR = 4;
	final private static int BH_SL = 5;
	final private static int SHIM_SL = 6;
	final private static int RAWDATA = 7;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		btthread = new BlueToothThread();
		btthread.start();
		
		textviewbhbr = (TextView) findViewById(R.id.tvbh_br);
		textviewbhhr = (TextView) findViewById(R.id.tvbh_hr);
		textviewbhhrv = (TextView) findViewById(R.id.tvbh_hrv);
		textviewshimscl = (TextView) findViewById(R.id.tvshim_scl);
		textviewshimscr = (TextView) findViewById(R.id.tvshim_scr);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		btthread.stopThread();
		
	}
	
	private Handler dataHandler = new Handler(Looper.getMainLooper()) {
		public void handleMessage(Message msg) {
			// Handle Data from Sensor Manager
			String data = null;
			switch (msg.what) {
			case BR:
				data = (String) msg.obj;
				Log.i("Data", "BR:" + data);
				textviewbhbr.setText(data);
				break;

			case HR:
				data = (String) msg.obj;
				Log.i("Data", "HR:" + data);
				textviewbhhr.setText(data);
				break;

			case HRV:
				data = (String) msg.obj;
				Log.i("Data", "HRV:" + data);
				textviewbhhrv.setText(data);
				break;

			case SCL:
				data = (String) msg.obj;
				Log.i("Data", "SCL:" + data);
				textviewshimscl.setText(data);
				break;

			case SCR:
				data = (String) msg.obj;
				Log.i("Data", "SCR:" + data);
				textviewshimscr.setText(data);
				break;

			case BH_SL:
				data = (String) msg.obj;
				Log.i("Data", "BH SL:" + data);
				break;
			
			case SHIM_SL:
				data = (String) msg.obj;
				Log.i("Data", "SHIM SL:" + data);
				break;
				
			case RAWDATA:
				data = (String) msg.obj;
				appendLog(data);
				
			default:
				break;
			};
		}
	};
	
	public void appendLog(String text)
	{       
		File logFile = new File("sdcard/log.txt");
		if (!logFile.exists())
		{
			try
			{
				logFile.createNewFile();
			} 
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		try
		{
			//BufferedWriter for performance, true to set append to file flag
			BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true)); 
			buf.append(text);
			buf.newLine();
			buf.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}  

	
	public class BlueToothThread extends Thread{

		SensorConnectionManager scm;
		boolean stillContinue;
		private String[] sensor_names;
		/*
		 * initialize Sensor Connection Manager 
		 */
		public BlueToothThread() {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

//			while (!mBluetoothAdapter.isEnabled()) {
//				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//			}

			scm = new SensorConnectionManager(mBluetoothAdapter);
			stillContinue = true;
		}

		public void run(){
			Looper.prepare();
			sensor_names = null;
			Vector<String> vSenorNames = new Vector<String>();
			if(GlobalConstant.isCheck_BH_BR || GlobalConstant.isCheck_BH_HR || GlobalConstant.isCheck_BH_HRV) 
				vSenorNames.add("BioHarness");
			if(GlobalConstant.isCheck_Shim_SCL || GlobalConstant.isCheck_Shim_SCR) 
				vSenorNames.add("Shimmer");
			sensor_names = vSenorNames.toArray(new String[vSenorNames.size()]);
			
			
			try {
				scm.connectSensors(sensor_names);
			} catch (IllegalArgumentException e) {
				Log.d("Exception",e.getMessage());
			}
			if(GlobalConstant.isCheck_BH_BR) 
				scm.setThreshold("BioHarness", "Respiration Rate", GlobalConstant.thresholdBHBR);
			if(GlobalConstant.isCheck_BH_HR) 
				scm.setThreshold("BioHarness", "Heart Rate", GlobalConstant.thresholdBHHR);
			if(GlobalConstant.isCheck_BH_HRV)
				scm.setThreshold("BioHarness", "HRV", GlobalConstant.thresholdBHHRV);
			if(GlobalConstant.isCheck_Shim_SCL)
				scm.setThreshold("Shimmer", "SCL", GlobalConstant.thresholdSHSCL);
			if(GlobalConstant.isCheck_Shim_SCR)
				scm.setThreshold("Shimmer", "SCR", GlobalConstant.thresholdSHSCR);
			
			
			while(stillContinue){
				try {
					sleep(100);
					Vector<String> rawData = new Vector<String>();
					
					String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
					rawData.add(currentDateTimeString);

					if(GlobalConstant.isCheck_BH_BR) {
						Message msg_BR = new Message();
						msg_BR.what = BR;
						msg_BR.obj = (Object) String.valueOf(scm.getRawData("BioHarness", "Respiration Rate"));
						rawData.add((String) msg_BR.obj);
						dataHandler.sendMessage(msg_BR);
					}

					if(GlobalConstant.isCheck_BH_HR) {
						Message msg_HR = new Message();
						msg_HR.what = HR;
						msg_HR.obj = (Object) String.valueOf(scm.getRawData("BioHarness", "Heart Rate"));
						rawData.add((String) msg_HR.obj);
						dataHandler.sendMessage(msg_HR);
					}
					
					if(GlobalConstant.isCheck_BH_HRV) {
						Message msg_HRV = new Message();
						msg_HRV.what = HRV;
						msg_HRV.obj = (Object) String.valueOf(scm.getRawData("BioHarness", "HRV"));
						dataHandler.sendMessage(msg_HRV);
						rawData.add((String) msg_HRV.obj);
					}

					if(GlobalConstant.isCheck_Shim_SCL) {
						Message msg_SCL = new Message();
						msg_SCL.what = SCL;
						msg_SCL.obj = (Object) String.valueOf(scm.getRawData("Shimmer", "SCL"));
						dataHandler.sendMessage(msg_SCL);
						rawData.add((String) msg_SCL.obj);
					}

					if(GlobalConstant.isCheck_Shim_SCR) {
						Message msg_SCR = new Message();
						msg_SCR.what = SCR;
						msg_SCR.obj = (Object) String.valueOf(scm.getRawData("Shimmer", "SCR"));
						dataHandler.sendMessage(msg_SCR);
						rawData.add((String) msg_SCR.obj);
					}
					
					Message msgRawData = new Message();
					msgRawData.what = RAWDATA;
					String rawValue = new String();
					for(String content: rawData) {
						rawValue += content + ",";
					}
					msgRawData.obj = rawValue;
					dataHandler.sendMessage(msgRawData);
					
					if(GlobalConstant.isCheck_Shim_SCL || GlobalConstant.isCheck_Shim_SCR) {
						Message msg_SHIM_SL = new Message();
						
						Vector<String> data_SHIM = new Vector<String>();
						if(GlobalConstant.isCheck_Shim_SCL)
							data_SHIM.add("SCL");
						if(GlobalConstant.isCheck_Shim_SCR)
							data_SHIM.add("SCR");

						String[] data = data_SHIM.toArray(new String[data_SHIM.size()]);
						msg_SHIM_SL.what = SHIM_SL;
						msg_SHIM_SL.obj = (Object) String.valueOf(scm.getStressLevel("Shimmer", data));
						dataHandler.sendMessage(msg_SHIM_SL);
					}

					if(GlobalConstant.isCheck_BH_BR || GlobalConstant.isCheck_BH_HR || GlobalConstant.isCheck_BH_HRV) {
						Message msg_BH_SL = new Message();

						Vector<String> data_BH = new Vector<String>();
						if(GlobalConstant.isCheck_BH_BR)
							data_BH.add("Respiration Rate");
						if(GlobalConstant.isCheck_BH_HR)
							data_BH.add("Heart Rate");
						if(GlobalConstant.isCheck_BH_HRV)
							data_BH.add("HRV");

						String[] data = data_BH.toArray(new String[data_BH.size()]);
						msg_BH_SL.what = BH_SL;
						msg_BH_SL.obj = (Object) String.valueOf(scm.getStressLevel("BioHarness", data));
						dataHandler.sendMessage(msg_BH_SL); 
					}
					
				} catch (IllegalArgumentException e) {
					Log.d("Exception", e.getMessage());
				} catch (InterruptedException e) {
					Log.d("Exception", e.getMessage());
				}
			}
			Looper.loop();
		}
		
		public void stopThread() {
			stillContinue = false;
			scm.disconnectSensors(sensor_names);
		}
	}


}
