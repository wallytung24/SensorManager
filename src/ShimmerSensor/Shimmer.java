//beta 0.2 (10 July 2012)

package ShimmerSensor;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Stack;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;
//import java.io.FileOutputStream;
import java.lang.Math;

public class Shimmer extends Service {
	//generic UUID for serial port protocol
	private UUID mSPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	// Message types sent from the Shimmer Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_ACK_RECEIVED = 4;
    public static final int MESSAGE_DEVICE_NAME = 5;
    public static final int MESSAGE_TOAST = 6;
    public static final int MESSAGE_SAMPLING_RATE_RECEIVED = 7;
    public static final int MESSAGE_INQUIRY_RESPONSE =8;
    // Key names received from the Shimmer Handler
    public static final String TOAST = "toast";
    
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	
	private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
	private int mState;
	
	// Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // The class is doing nothing
    public static final int STATE_CONNECTING = 1; // The class is now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // The class is now connected to a remote device
    
    //Sensor Bitmap
    public static final int SENSOR_ACCEL				   = 0x80;
    public static final int SENSOR_GYRO				   	   = 0x40;
    public static final int SENSOR_MAG					   = 0x20;
    public static final int SENSOR_ECG					   = 0x10;
    public static final int SENSOR_EMG					   = 0x08;
    public static final int SENSOR_GSR					   = 0x04;
    public static final int SENSOR_EXP_BOARD_A7		       = 0x02;
    public static final int SENSOR_EXP_BOARD_A0		       = 0x01;
    public static final int SENSOR_STRAIN				   = 0x8000;
    public static final int SENSOR_HEART				   = 0x4000;
    
    //Constants describing the packet type
    private static final byte DATA_PACKET                      = (byte) 0x00;
    private static final byte INQUIRY_COMMAND                  = (byte) 0x01;
    private static final byte INQUIRY_RESPONSE                 = (byte) 0x02;
    private static final byte GET_SAMPLING_RATE_COMMAND 	   = (byte) 0x03;
    private static final byte SAMPLING_RATE_RESPONSE           = (byte) 0x04;
    private static final byte SET_SAMPLING_RATE_COMMAND        = (byte) 0x05;
    private static final byte TOGGLE_LED_COMMAND               = (byte) 0x06;
    private static final byte START_STREAMING_COMMAND          = (byte) 0x07;
    private static final byte SET_SENSORS_COMMAND              = (byte) 0x08;
    private static final byte SET_ACCEL_SENSITIVITY_COMMAND    = (byte) 0x09;
    private static final byte ACCEL_SENSITIVITY_RESPONSE       = (byte) 0x0A;
    private static final byte GET_ACCEL_SENSITIVITY_COMMAND    = (byte) 0x0B;
    private static final byte SET_5V_REGULATOR_COMMAND         = (byte) 0x0C;
    private static final byte SET_PMUX_COMMAND                 = (byte) 0x0D;
    private static final byte SET_CONFIG_BYTE0_COMMAND   	   = (byte) 0x0E;
    private static final byte CONFIG_BYTE0_RESPONSE      	   = (byte) 0x0F;
    private static final byte GET_CONFIG_BYTE0_COMMAND   	   = (byte) 0x10;
    private static final byte STOP_STREAMING_COMMAND           = (byte) 0x20;
    private static final byte ACK_COMMAND_PROCESSED            = (byte) 0xff;
    private static final byte ACCEL_CALIBRATION_RESPONSE       = (byte) 0x12;
    private static final byte GET_ACCEL_CALIBRATION_COMMAND    = (byte) 0x13;
    private static final byte GYRO_CALIBRATION_RESPONSE        = (byte) 0x15;
    private static final byte GET_GYRO_CALIBRATION_COMMAND     = (byte) 0x16;
    private static final byte MAG_CALIBRATION_RESPONSE         = (byte) 0x18;
    private static final byte GET_MAG_CALIBRATION_COMMAND      = (byte) 0x19;
    private static final byte SET_GSR_RANGE_COMMAND			   = (byte) 0x21;
    private static final byte GSR_RANGE_RESPONSE			   = (byte) 0x22;
    private static final byte GET_GSR_RANGE_COMMAND			   = (byte) 0x23;
    private static final byte GET_SHIMMER_VERSION_COMMAND      = (byte) 0x24;
    private static final byte GET_SHIMMER_VERSION_RESPONSE     = (byte) 0x25;
    private final int ACK_TIMER_DURATION = 1; 									// Duration to wait for an ack packet (seconds)
    
    private double mLastReceivedTimeStamp=0;
    private double mCurrentTimeStampCycle=0;
    private boolean mStreaming =false;											// This is used to monitor whether the device is in streaming mode
	private double mSamplingRate; 	                                        // 51.2Hz is the default sampling rate 
	private int mEnabledSensors;												// This stores the enabled sensors
	private int mSetEnabledSensors = SENSOR_ACCEL;												// Only used during the initialization process, see initialize();
	private String mMyName;													// This stores the user assigned name
	private byte mCurrentCommand;												// This variable is used to keep track of the current command being executed while waiting for an Acknowledge Packet. This allows the appropriate action to be taken once an Acknowledge Packet is received. 
	private double mTempDoubleValue;											// A temporary variable used to store Double value, used mainly to store a value while waiting for an acknowledge packet (e.g. when writeSamplingRate() is called, the sampling rate is stored temporarily and used to update SamplingRate when the acknowledge packet is received.
	private byte mTempByteValue;												// A temporary variable used to store Byte value	
	private int mTempIntValue;													// A temporary variable used to store Integer value, used mainly to store a value while waiting for an acknowledge packet (e.g. when writeGRange() is called, the range is stored temporarily and used to update GSRRange when the acknowledge packet is received.
	private boolean mWaitForAck=false;                                          // This indicates whether the device is waiting for an acknowledge packet from the Shimmer Device  
	private boolean mWaitForResponse=false; 									// This indicates whether the device is waiting for a response packet from the Shimmer Device 
	private int mPacketSize=0; 													// Default 2 bytes for time stamp and 6 bytes for accelerometer 
	private int mAccelRange=0;														// This stores the current accelerometer range being used. The accelerometer range is stored during two instances, once an ack packet is received after a writeAccelRange(), and after a response packet has been received after readAccelRange()  	
	private int mGSRRange=4;														// This stores the current GSR range being used.
    private int mConfigByte0;
    private int mNChannels=0;                                                     // Default number of sensor channels set to three because of the on board accelerometer 
    private int mBufferSize;                   									// Buffer size is currently not used
    private int mShimmerVersion;
    private String mMyBluetoothAddress="";
    private String[] mSignalNameArray=new String[19];								// 19 is the maximum number of signal thus far
    private String[] mSignalDataTypeArray=new String[19];							// 19 is the maximum number of signal thus far
    private String[] mGetDataInstruction={"a"}; 									// This is the default value to return all data in both calibrated and uncalibrated format for now only 'a' is supported
    private boolean mDefaultCalibrationParametersAccel = true;
	private double[][] AlignmentMatrixAccel = {{1,0,0},{0,1,0},{0,0,1}}; 				//Default Values for Accelerometer Calibration
	private double[][] SensitivityMatrixAccel = {{38,0,0},{0,38,0},{0,0,38}}; 			//Default Values for Accelerometer Calibration
	private double[][] OffsetVectorAccel = {{2048},{2048},{2048}};						//Default Values for Accelerometer Calibration
    private boolean mDefaultCalibrationParametersGyro = true;
	private double[][] AlignmentMatrixGyro = {{0,-1,0},{-1,0,0},{0,0,-1}}; 				//Default Values for Gyroscope Calibration
	private double[][] SensitivityMatrixGyro = {{2.73,0,0},{0,2.73,0},{0,0,2.73}}; 		//Default Values for Gyroscope Calibration
	private double[][] OffsetVectorGyro = {{1843},{1843},{1843}};						//Default Values for Gyroscope Calibration
    private boolean mDefaultCalibrationParametersMag = true;
	private double[][] AlignmentMatrixMag = {{1,0,0},{0,1,0},{0,0,-1}}; 				//Default Values for Magnetometer Calibration
	private double[][] SensitivityMatrixMag = {{580,0,0},{0,580,0},{0,0,580}}; 			//Default Values for Magnetometer Calibration
	private double[][] OffsetVectorMag = {{0},{0},{0}};									//Default Values for Magnetometer Calibration
	private boolean mTransactionCompleted=true;									// Variable is used to ensure a command has finished execution prior to executing the next command (see initialize())
    private boolean mSync=true;													// Variable to keep track of sync
    private boolean mContinousSync=false;                                        // This is to select whether to continuously check the data packets 
    public boolean mInitializationCompleted=false;
    private boolean mSetupDevice=false;											// Used by the constructor when the user intends to write new settings to the Shimmer device after connection
    private Timer mTimer;							
    // Timer variable used when waiting for an ack or response packet
	  /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     * @param myname  To allow the user to set a unique identifier for each Shimmer device
     * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
     */
    public Shimmer(Context context, Handler handler, String myName, Boolean continousSync) {
    	mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mMyName=myName;
        mContinousSync=continousSync;
        mSetupDevice=false;
    }
	  /**
     * Constructor. Prepares a new BluetoothChat session. Additional fields allows the device to be set up immediately.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     * @param myname  To allow the user to set a unique identifier for each Shimmer device
     * @param samplingRate Defines the sampling rate
     * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
     * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
     * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
     * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
     */
    public Shimmer(Context context, Handler handler, String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, boolean continousSync) {
    	mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mSamplingRate=samplingRate;
        mAccelRange=accelRange;
        mGSRRange=gsrRange;
        mSetEnabledSensors=setEnabledSensors;
        mMyName=myName;
        mSetupDevice=true;
        mContinousSync=continousSync;
    }
    
    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Shimmer.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    
    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }
    
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }
    
    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
    	// Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        mMyBluetoothAddress = device.getName();
        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Shimmer.MESSAGE_DEVICE_NAME);
        mHandler.sendMessage(msg);
        while(!mConnectedThread.isAlive()){}; 
        setState(STATE_CONNECTED);
        initialize();
    }
    
    /**
     * Stop all threads
     */
    public synchronized void stop() {
    	while(getInstructionStatus()==false);
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
    
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_NONE);
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_NONE);
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    public class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(mSPP_UUID); // If your device fails to pair try: device.createInsecureRfcommSocketToServiceRecord(mSPP_UUID)
            } catch (IOException e) {}
            mmSocket = tmp;
        }

        public void run() {
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
            	connectionFailed();
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

           
            // Reset the ConnectThread because we're done
            synchronized (Shimmer.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
          
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    
    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {}

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        
        /**
         *The received packets are processed here 
         */
        public void run() {
            
            byte[] tb ={0};
    		Stack<Byte> packetStack = new Stack<Byte>();
    		byte[] newPacket=new byte[mPacketSize+1];
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                	
                	mmInStream.read(tb,0,1);
                      	
                	//Is the device waiting for an Ack/Response if so look out for the appropriate command
                	if (mWaitForAck==true) {
                		
                		if ((byte)tb[0]==ACK_COMMAND_PROCESSED)
                		{	
                		    mTimer.cancel(); //cancel the ack timer
                		   
                            Log.d("Shimmer", "ACK Received for Device: " + mMyBluetoothAddress + "; Command Issued: " + mCurrentCommand );
                           
                		    mWaitForAck=false;
                			if (mCurrentCommand==START_STREAMING_COMMAND) {
                			    mStreaming=true;
                			    mTransactionCompleted=true;
                			    isNowStreaming();
                			    }
                    	    if (mCurrentCommand==STOP_STREAMING_COMMAND) {
                    		    mStreaming=false;
                    		    mTransactionCompleted=true;
                    		    }
                    		if (mCurrentCommand==SET_SENSORS_COMMAND) {
                    		    mEnabledSensors=mTempIntValue; //if transaction successful execute update enabledSensors
                    		    packetStack.clear(); // Always clear the packetStack after setting the sensors, this is to ensure a fresh start
                    		    mTransactionCompleted=true;
                    		    inquiry();
                    			}
                    		if (mCurrentCommand==SET_SAMPLING_RATE_COMMAND) {
                    		    mSamplingRate=mTempDoubleValue;
                    		    mTransactionCompleted=true;
                    		    }
                    		if (mCurrentCommand==INQUIRY_COMMAND) {
                    		    mWaitForResponse=true;
                    		    }
                    		if (mCurrentCommand==GET_ACCEL_SENSITIVITY_COMMAND) {
                    		    mWaitForResponse=true;
                    		    }
                    		if (mCurrentCommand==GET_GSR_RANGE_COMMAND) {
                    		    mWaitForResponse = true;
                    		    }
                    		if (mCurrentCommand==SET_GSR_RANGE_COMMAND) {
                    		    mGSRRange=mTempIntValue;
                    		    mTransactionCompleted = true;
                    		    }
                    		if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND) {
                    		    mWaitForResponse=true;
                    		    }
                    		if (mCurrentCommand==GET_CONFIG_BYTE0_COMMAND) {
                    		    mWaitForResponse=true;
                    		    }
                    		if (mCurrentCommand==SET_CONFIG_BYTE0_COMMAND) {
                    		    mTransactionCompleted=true;
                    		    mConfigByte0=mTempByteValue;
                    		    }
                    		if (mCurrentCommand==SET_PMUX_COMMAND) {
                    		    mTransactionCompleted=true;
                    		    if ((mTempByteValue & (byte)64)!=0) {
                    				//then set ConfigByte0 at bit position 7
                    			    mConfigByte0 = mConfigByte0 | (1 << 6);
                    		    } else{
                    			    mConfigByte0 = mConfigByte0 & ~(1 << 6);
                    			}
                    		}
                    		if (mCurrentCommand==SET_5V_REGULATOR_COMMAND) {
                    		    mTransactionCompleted=true;
                    		    if ((mTempByteValue & (byte)128)!=0) {
                    				//then set ConfigByte0 at bit position 8
                    			    mConfigByte0 = mConfigByte0 | (1 << 7);
                    		    } else{
                    			    mConfigByte0 = mConfigByte0 & ~(1 << 7);
                    		    }
                    		}
                    		if (mCurrentCommand==SET_ACCEL_SENSITIVITY_COMMAND) {
                    		    mTransactionCompleted=true;
                    		    mAccelRange=mTempIntValue;
                    		    } 
                    		if (mCurrentCommand==GET_ACCEL_CALIBRATION_COMMAND || mCurrentCommand==GET_GYRO_CALIBRATION_COMMAND || mCurrentCommand==GET_MAG_CALIBRATION_COMMAND) {
                    		    mWaitForResponse = true;
                    		    }	
                    		if (mCurrentCommand==GET_SHIMMER_VERSION_COMMAND) {
                    		    mWaitForResponse = true;
                    		    }
                		}
                	} else if (mWaitForResponse==true) {
                		if (tb[0]==INQUIRY_RESPONSE) {
                			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                		    try {
								Thread.sleep(200);	// Due to the nature of  
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
                		    byte[] bufferInquiry = new byte[30]; // set to maximum packet size
                		    mmInStream.read(bufferInquiry, 0, 30);
                		    mPacketSize = 2+bufferInquiry[3]*2; 
                		    mSamplingRate = 1024/bufferInquiry[0];
                    	    mAccelRange = bufferInquiry[1];
                    	    mConfigByte0 = bufferInquiry[2];
                    	    mNChannels = bufferInquiry[3];
                    	    mBufferSize = bufferInquiry[4];
                    	    byte[] signalIdArray = new byte[mNChannels];
                    	    System.arraycopy(bufferInquiry, 5, signalIdArray, 0, mNChannels);
                    	    interpretdatapacketformat(mNChannels,signalIdArray);
                            mWaitForResponse = false;
                            Log.d("Shimmer","Inquiry Response Received for Device-> "+mMyBluetoothAddress + " "+ bufferInquiry[0]+ " "+ bufferInquiry[1]+ " "+bufferInquiry[2]+ " " +bufferInquiry[3]+ " "+bufferInquiry[4]+ " " +bufferInquiry[5]+ " " +bufferInquiry[6]+ " " +bufferInquiry[7]+ " "+bufferInquiry[8]+ " " +bufferInquiry[9]+ " " +bufferInquiry[10] + " " +bufferInquiry[11]+ " " +bufferInquiry[12]+ " " +bufferInquiry[13]+ " " +bufferInquiry[14]+ " " +bufferInquiry[15] + " " +bufferInquiry[16] + " " +bufferInquiry[17] + " " +bufferInquiry[18] );
                            inquiryDone();
                            mTransactionCompleted=true;
                		} else if(tb[0] == GSR_RANGE_RESPONSE) {
                			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                		    mWaitForResponse=false;
                		    mTransactionCompleted=true;
                		    byte[] bufferGSRRange = new byte[1]; // set to maximum packet size
                 		    mmInStream.read(bufferGSRRange, 0, 1);
                 	        mGSRRange=bufferGSRRange[0];
                		} else if(tb[0]==ACCEL_SENSITIVITY_RESPONSE) {
                			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                		    mWaitForResponse=false;
                		    mTransactionCompleted=true;
                		    byte[] bufferAccelSensitivity = new byte[1]; // set to maximum packet size
                 		    mmInStream.read(bufferAccelSensitivity, 0, 1);
                 	        mAccelRange=bufferAccelSensitivity[0];
                		} else if (tb[0]==SAMPLING_RATE_RESPONSE) {
                			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                		    mWaitForResponse=false;
                		    if(mStreaming==false) {
                			    byte[] bufferSR = new byte[30]; // set to maximum packet size
                        	    mmInStream.read(bufferSR, 0, 1); //read the sampling rate
                        	    if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND) { // this is a double check, not necessary 
                        		    double val=(double)(bufferSR[0] & (byte) ACK_COMMAND_PROCESSED);
                        		    mSamplingRate=1024/val;
                        		    }
                			}
                			mTransactionCompleted=true;
                		} else if (tb[0]==ACCEL_CALIBRATION_RESPONSE ) {
                			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                			try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
                		    mWaitForResponse=false;
                		    byte[] bufferCalibrationParameters = new byte[21]; // set to maximum packet size
                 		    mmInStream.read(bufferCalibrationParameters, 0, 21);
                 	        int packetType=tb[0];
                 	        retrievecalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
                 	        mTransactionCompleted=true;
                 		} else if (tb[0]==GYRO_CALIBRATION_RESPONSE) {
                 			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                 			try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
                		    mWaitForResponse=false;
                		    byte[] bufferCalibrationParameters = new byte[21]; // set to maximum packet size
                 		    mmInStream.read(bufferCalibrationParameters, 0, 21);
                 	        int packetType=tb[0];
                 	        retrievecalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
                 	        mTransactionCompleted=true;
                 		} else if (tb[0]==MAG_CALIBRATION_RESPONSE ) {
                 			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                		    mWaitForResponse=false;
                		    try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
                		    byte[] bufferCalibrationParameters = new byte[21]; // set to maximum packet size
                 		    mmInStream.read(bufferCalibrationParameters, 0, 21);
                 	        int packetType=tb[0];
                 	        retrievecalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
                 	        mTransactionCompleted=true;
                 		} else if(tb[0]==CONFIG_BYTE0_RESPONSE) {
                 			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                		    byte[] bufferConfigByte0 = new byte[1]; // set to maximum packet size
                 		    mmInStream.read(bufferConfigByte0, 0, 1);
                 		    mTransactionCompleted=true;
                 		    mConfigByte0=(int)bufferConfigByte0[0];
                 		} else if(tb[0]==GET_SHIMMER_VERSION_RESPONSE) {
                 			Log.d("Shimmer","Response received: " + Integer.toString(mCurrentCommand));
                			byte[] bufferShimmerVersion = new byte[1]; // set to maximum packet size
                 			mmInStream.read(bufferShimmerVersion, 0, 1);
                 			mShimmerVersion=(int)bufferShimmerVersion[0];
                 			mTransactionCompleted=true;
                		}
                	}      	
                	if (mStreaming==true) {
                		//Log.d("IncomingBytes","Byte: " + Byte.toString(tb[0]));
        			    if (packetStack.size()==mPacketSize+1) {        //if the stack is full
            			    if (tb[0]==DATA_PACKET && packetStack.firstElement()==DATA_PACKET && mSync==true) { //check for the starting zero of the packet, and the starting zero of the subsequent packet, this causes a delay equivalent to the transmission suration between two packets
	            			    newPacket=convertstacktobytearray(packetStack,mPacketSize);
	            			    ObjectCluster objectCluster=buildMsg(newPacket, mGetDataInstruction); 
	            			    //printtofile(newmsg.UncalibratedData);
	            		        mHandler.obtainMessage(MESSAGE_READ, objectCluster)
	                        	        .sendToTarget();
	                            packetStack.clear();
	                            if (mContinousSync==false) {         //disable continuous synchronizing 
	                         	    mSync=false;
	                           	}
	            			} else if(packetStack.firstElement()==DATA_PACKET && mSync==false) {         //only used when continous sync is disabled
	            			    newPacket=convertstacktobytearray(packetStack,mPacketSize);
	            			    ObjectCluster objectCluster=buildMsg(newPacket, mGetDataInstruction);    //the packet which is an array of bytes is converted to the data structure
	            			    //printtofile(newmsg.UncalibratedData);
	            		        mHandler.obtainMessage(MESSAGE_READ, objectCluster)
	                        			.sendToTarget();
	            		        packetStack.clear();
	                         	}
            				}
        			    packetStack.push((tb[0])); //push new sensor data into the stack
            			    if (packetStack.size()>mPacketSize+1) { //if the stack has reached the packet size remove an element from the stack
            				    packetStack.removeElementAt(0);
            		        }
        		    }
        	    } catch (IOException e) {
        	    	  Log.d("Shimmer", e.toString());
        	    	  connectionLost();
                      break;
                }
            }
        }
         
    
        
        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        private void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                Log.d("Shimmer", "Command transmitted: " + mMyBluetoothAddress + "; Command Issued: " + mCurrentCommand );
                
            } catch (IOException e) {}
        }
             
        public void cancel() {
        	if(mmInStream != null) {
        		try {
        			mmInStream.close();
        		} catch (IOException e) {}
        	}
        	if(mmOutStream != null) {
        		try {
        			mmOutStream.close();
        		} catch (IOException e) {}
        	}
        	if(mmSocket != null) {
	            try {
	                mmSocket.close();
	            } catch (IOException e) {}
        	}
        }
    }
    
    public void setgetdatainstruction(String... instruction) {
    	mGetDataInstruction=instruction;
    }
    
	public void startStreaming() {
		while(getInstructionStatus()==false) {};
		mCurrentCommand=START_STREAMING_COMMAND;
    	write(new byte[]{START_STREAMING_COMMAND});
		mWaitForAck=true;
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION+10); //Some Shimmer devices require a longer response time
		}
	public void inquiry() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=INQUIRY_COMMAND;
    	write(new byte[]{INQUIRY_COMMAND});
		mWaitForAck=true;
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION);		
	}

	public void stopStreaming() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=STOP_STREAMING_COMMAND;
    	write(new byte[]{STOP_STREAMING_COMMAND});
		mWaitForAck=true;
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION);
	}
	
  public synchronized void responseTimer(int seconds) {
        mTimer = new Timer();
        mTimer.schedule(new responseTask(), seconds*1000);
	}
    
    class responseTask extends TimerTask {
        public void run() {
        	if (mWaitForAck==true) {
        		Log.d("Shimmer", "Command " + Integer.toString(mCurrentCommand) +" failed; Killing Connection");
                mTimer.cancel(); //Terminate the timer thread
                mWaitForAck=false;
                mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
                stop(); //If command fail exit device 
                
            }
        }
    }
    
	/**
	 * This returns the variable mTransactionCompleted which indicates whether the Shimmer device is in the midst of a command transaction. True when no transaction is taking place.
	 * @return mTransactionCompleted
	 */
	public boolean getInstructionStatus()
	{
		return mTransactionCompleted;
	}
	
	public double getSamplingRate(){
		return mSamplingRate;
	}
	
	private void initialize() {	
    	//see two constructors for Shimmer
    	Log.d("Shimmer","Device " + mMyBluetoothAddress + " initializing");
		readCalibrationParameters("Accelerometer");
		readCalibrationParameters("Magnetometer");
		readCalibrationParameters("Gyroscope");
    	if (mSetupDevice==true){
    		writeSamplingRate(mSamplingRate);	
 			writeAccelRange(mAccelRange);
 			writeGSRRange(mGSRRange);
 			writeEnabledSensors(mSetEnabledSensors);
 			setContinuousSync(false);
    	} else {
    		inquiry();
    	}
    	while(getInstructionStatus()==false) {}; // Ensure the last command has finished execution
    }
	    
    private void inquiryDone() {
    	Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Inquiry done for device-> " + mMyBluetoothAddress);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        isReadyForStreaming();
    }
	    
	
    private void isReadyForStreaming(){
		Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device " + mMyBluetoothAddress +" is ready for Streaming");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        Log.d("Shimmer","Shimmer " + mMyBluetoothAddress +" Initialization completed and is ready for Streaming");
    }
	    
    private void isNowStreaming() {
    	
    	Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device " + mMyBluetoothAddress + " is now Streaming");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        Log.d("Shimmer","Shimmer " + mMyBluetoothAddress +" is now Streaming");
    }
    
   /*
    * Set and Get Methods
    * */    
   public void setContinuousSync(boolean continousSync){
	   mContinousSync=continousSync;
   }

   public boolean getStreamingStatus(){
	   return mStreaming;
   }
   
   public String getBluetoothAddress(){
	   return  mMyBluetoothAddress;
   }

   public int getEnabledSensors() {
	   return mEnabledSensors;
   }
   
  	/*
  	 * Data Methods
  	 * */  
   
    /**
    * Converts the raw packet byte values, into the corresponding calibrated and uncalibrated sensor values, the Instruction String determines the output 
    * @param newPacket a byte array containing the current received packet
    * @param Instructions an array string containing the commands to execute. It is currently not fully supported
    * @return
    */
   
   private int[] parsedData(byte[] data,String[] dataType)
	{
		int iData=0;
		int[] formattedData=new int[dataType.length];

		for (int i=0;i<dataType.length;i++)
			if (dataType[i]=="u8") {
				formattedData[i]=(int)data[iData];
				iData=iData+1;
			}
			else if (dataType[i]=="i8") {
				formattedData[i]=calculatetwoscomplement((int)((int)0xFF & data[iData]),8);
				iData=iData+1;
			}
			else if (dataType[i]=="u12") {
				
				formattedData[i]=(int)((int)(data[iData] & 0xFF) + ((int)(data[iData+1] & 0xFF) << 8));
				iData=iData+2;
			}
			else if (dataType[i]=="u16") {
				
				formattedData[i]=(int)((int)(data[iData] & 0xFF) + ((int)(data[iData+1] & 0xFF) << 8));
				iData=iData+2;
			}
			else if (dataType[i]=="i16") {
				
				formattedData[i]=calculatetwoscomplement((int)((int)(data[iData] & 0xFF) + ((int)(data[iData+1] & 0xFF) << 8)),16);
				iData=iData+2;
			}
		return formattedData;
	}
	
	private int[] formatdatapacketreverse(byte[] data,String[] dataType)
	{
		int iData=0;
		int[] formattedData=new int[dataType.length];

		for (int i=0;i<dataType.length;i++)
			if (dataType[i]=="u8") {
				formattedData[i]=(int)data[iData];
				iData=iData+1;
			}
			else if (dataType[i]=="i8") {
				formattedData[i]=calculatetwoscomplement((int)((int)0xFF & data[iData]),8);
				iData=iData+1;
			}
			else if (dataType[i]=="u12") {
				
				formattedData[i]=(int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData] & 0xFF) << 8));
				iData=iData+2;
			}
			else if (dataType[i]=="u16") {
				
				formattedData[i]=(int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData] & 0xFF) << 8));
				iData=iData+2;
			}
			else if (dataType[i]=="i16") {
				
				formattedData[i]=calculatetwoscomplement((int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData] & 0xFF) << 8)),16);
				iData=iData+2;
			}
		return formattedData;
	}
	
	private int calculatetwoscomplement(int signedData, int bitLength)
	{
		int newData=signedData;
		if (signedData>(1<<(bitLength-1))) {
			newData=-((signedData^(int)(Math.pow(2, bitLength)-1))+1);
		}

		return newData;
	}
	
	private int getSignalIndex(String signalName) {
	int iSignal=-1;
		for (int i=0;i<mSignalNameArray.length;i++) {
		if (signalName==mSignalNameArray[i]) {
			iSignal=i;
		}
		}

		return iSignal;
	}
	
	private void interpretdatapacketformat(int nC, byte[] signalid)
	{
		String [] signalNameArray=new String[19];
		String [] signalDataTypeArray=new String[19];
		signalNameArray[0]="TimeStamp";
		signalDataTypeArray[0]="u16";
		int packetSize=2; // Time stamp
		int enabledSensors= 0x00;
		for (int i=0;i<nC;i++) {
			if ((byte)signalid[i]==(byte)0x00)
			{
				signalNameArray[i+1]="Accelerometer X";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_ACCEL);
			}
			else if ((byte)signalid[i]==(byte)0x01)
			{
				signalNameArray[i+1]="Accelerometer Y";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_ACCEL);
			}
			else if ((byte)signalid[i]==(byte)0x02)
			{
				signalNameArray[i+1]="Accelerometer Z";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_ACCEL);
			}
			else if ((byte)signalid[i]==(byte)0x03)
			{
				signalNameArray[i+1]="Gyroscope X";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_GYRO);
			}
			else if ((byte)signalid[i]==(byte)0x04)
			{
				signalNameArray[i+1]="Gyroscope Y";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_GYRO);
			}
			else if ((byte)signalid[i]==(byte)0x05)
			{
				signalNameArray[i+1]="Gyroscope Z";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_GYRO);
			}
			else if ((byte)signalid[i]==(byte)0x06)
			{
				signalNameArray[i+1]="Magnetometer X";
				signalDataTypeArray[i+1] = "i16";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_MAG);
			}
			else if ((byte)signalid[i]==(byte)0x07)
			{
				signalNameArray[i+1]="Magnetometer Y";
				signalDataTypeArray[i+1] = "i16";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_MAG);
			}
			else if ((byte)signalid[i]==(byte)0x08)
			{
				signalNameArray[i+1]="Magnetometer Z";
				signalDataTypeArray[i+1] = "i16";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_MAG);
			}
			else if ((byte)signalid[i]==(byte)0x09)
			{
				signalNameArray[i+1]="ECG RA LL";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_ECG);
			}
			else if ((byte)signalid[i]==(byte)0x0A)
			{
				signalNameArray[i+1]="ECG LA LL";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_ECG);
			}
			else if ((byte)signalid[i]==(byte)0x0B)
			{
				signalNameArray[i+1]="GSR Raw";
				signalDataTypeArray[i+1] = "u16";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_GSR);
			}
			else if ((byte)signalid[i]==(byte)0x0C)
			{
				signalNameArray[i+1]="GSR Res";
				signalDataTypeArray[i+1] = "u16";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_GSR);
			}
			else if ((byte)signalid[i]==(byte)0x0D)
			{
				signalNameArray[i+1]="EMG";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_EMG);
			}
			else if ((byte)signalid[i]==(byte)0x0E)
			{
				signalNameArray[i+1]="Exp Board A0";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_EXP_BOARD_A0);
			}
			else if ((byte)signalid[i]==(byte)0x0F)
			{
				signalNameArray[i+1]="Exp Board A7";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_EXP_BOARD_A7);
			}
			else if ((byte)signalid[i]==(byte)0x10)
			{
				signalNameArray[i+1]="Strain Gauge High";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_STRAIN);
			}
			else if ((byte)signalid[i]==(byte)0x11)
			{
				signalNameArray[i+1]="Strain Gauge Low";
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
				enabledSensors= (enabledSensors|SENSOR_STRAIN);
			}
			else if ((byte)signalid[i]==(byte)0x12)
			{
				signalNameArray[i+1]="Heart Rate";
				signalDataTypeArray[i+1] = "u8";
				packetSize=packetSize+1;
				enabledSensors= (enabledSensors|SENSOR_HEART);
			}
			else
			{
				signalNameArray[i+1]=Byte.toString(signalid[i]);
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
			}
		}
		mSignalNameArray=signalNameArray;
		mSignalDataTypeArray=signalDataTypeArray;
		mPacketSize=packetSize;
		mEnabledSensors=enabledSensors;
	}
	
   private void retrievecalibrationparametersfrompacket(byte[] bufferCalibrationParameters, int packetType)
	{
		String[] dataType={"u16","u16","u16","u16","u16","u16","i8","i8","i8","i8","i8","i8","i8","i8","i8"};
		int[] formattedPacket=formatdatapacketreverse(bufferCalibrationParameters,dataType);
   	double[] AM=new double[9];
   	for (int i=0;i<9;i++)
		{
			AM[i]=((double)formattedPacket[6+i])/100;
		}
		
		double[][] AlignmentMatrix = {{AM[0],AM[1],AM[2]},{AM[3],AM[4],AM[5]},{AM[6],AM[7],AM[8]}}; 				
		double[][] SensitivityMatrix = {{formattedPacket[3],0,0},{0,formattedPacket[4],0},{0,0,formattedPacket[5]}}; 
		double[][] OffsetVector = {{formattedPacket[0]},{formattedPacket[1]},{formattedPacket[2]}};
		
		
		if (packetType==ACCEL_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]!=65535) {   
			mDefaultCalibrationParametersAccel = false;
			Log.d("Shimmer","Accel Offet Vector(0,0): " + Double.toString(OffsetVector[0][0]) + "; Accel Sen Matrix(0,0): " + Double.toString(SensitivityMatrix[0][0]) +"; Accel Align Matrix(0,0): " + Double.toString(AlignmentMatrix[0][0]));
			AlignmentMatrixAccel = AlignmentMatrix;
			OffsetVectorAccel = OffsetVector;
			SensitivityMatrixAccel = SensitivityMatrix;
		} else if (packetType==GYRO_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]!=65535) {
			mDefaultCalibrationParametersGyro = false;
			AlignmentMatrixGyro = AlignmentMatrix;
			OffsetVectorGyro = OffsetVector;
			SensitivityMatrixGyro = SensitivityMatrix;
			SensitivityMatrixGyro[0][0] = SensitivityMatrixGyro[0][0]/100;
			SensitivityMatrixGyro[1][1] = SensitivityMatrixGyro[1][1]/100;
			SensitivityMatrixGyro[2][2] = SensitivityMatrixGyro[2][2]/100;
			Log.d("Shimmer","Gyro Offet Vector(0,0): " + Double.toString(OffsetVector[0][0]) + "; Gyro Sen Matrix(0,0): " + Double.toString(SensitivityMatrix[0][0]) +"; Gyro Align Matrix(0,0): " + Double.toString(AlignmentMatrix[0][0]));
		} else if (packetType==MAG_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]!=65535) {
			mDefaultCalibrationParametersMag = false;
			AlignmentMatrixMag = AlignmentMatrix;
			OffsetVectorMag = OffsetVector;
			SensitivityMatrixMag = SensitivityMatrix;
			Log.d("Shimmer","Mag Offet Vector(0,0): " + Double.toString(OffsetVector[0][0]) + "; Mag Sen Matrix(0,0): " + Double.toString(SensitivityMatrix[0][0]) +"; Mag Align Matrix(0,0): " + Double.toString(AlignmentMatrix[0][0]));
		}
	}

   private double[][] matrixinverse3x3(double[][] data) {
	    double a,b,c,d,e,f,g,h,i;
	    a=data[0][0];
	    b=data[0][1];
	    c=data[0][2];
	    d=data[1][0];
	    e=data[1][1];
	    f=data[1][2];
	    g=data[2][0];
	    h=data[2][1];
	    i=data[2][2];
	    //
	    double deter=a*e*i+b*f*g+c*d*h-c*e*g-b*d*i-a*f*h;
	    double[][] answer=new double[3][3];
	    answer[0][0]=(1/deter)*(e*i-f*h);
	    
	    answer[0][1]=(1/deter)*(c*h-b*i);
	    answer[0][2]=(1/deter)*(b*f-c*e);
	    answer[1][0]=(1/deter)*(f*g-d*i);
	    answer[1][1]=(1/deter)*(a*i-c*g);
	    answer[1][2]=(1/deter)*(c*d-a*f);
	    answer[2][0]=(1/deter)*(d*h-e*g);
	    answer[2][1]=(1/deter)*(g*b-a*h);
	    answer[2][2]=(1/deter)*(a*e-b*d);
	    return answer;
	    }
	private double[][] matrixminus(double[][] a ,double[][] b) {
		          int aRows = a.length,
			      aColumns = a[0].length,
			      bRows = b.length,
			      bColumns = b[0].length;
		          if (( aColumns != bColumns )&&( aRows != bRows )) {
		    		    throw new IllegalArgumentException(" Matrix did not match");
		    		  }
	    		  double[][] resultant = new double[aRows][bColumns];
		          for(int i = 0; i < aRows; i++) { // aRow
		        	  for(int k = 0; k < aColumns; k++) { // aColumn
		        	
		        		  resultant[i][k]=a[i][k]-b[i][k];
		        		  
		        	  }
		          }
		        	return resultant;
	}
   
   private double[][] matrixmultiplication(double[][] a,double[][] b) {
   
	          int aRows = a.length,
		      aColumns = a[0].length,
		      bRows = b.length,
		      bColumns = b[0].length;
   		 
   		  if ( aColumns != bRows ) {
   		    throw new IllegalArgumentException("A:Rows: " + aColumns + " did not match B:Columns " + bRows + ".");
   		  }
   		 
   		  double[][] resultant = new double[aRows][bColumns];
   		 
   		  for(int i = 0; i < aRows; i++) { // aRow
   		    for(int j = 0; j < bColumns; j++) { // bColumn
   		      for(int k = 0; k < aColumns; k++) { // aColumn
   		        resultant[i][j] += a[i][k] * b[k][j];
   		      }
   		    }
   		  }
   		 
   		  return resultant;
   }
	    
   private double calibrateTimeStamp(double timeStamp){
   	//first convert to continuous time stamp
   	double calibratedTimeStamp=0;
   	if (mLastReceivedTimeStamp>(timeStamp+(65536*mCurrentTimeStampCycle))){ 
   		mCurrentTimeStampCycle=mCurrentTimeStampCycle+1;
   	}
   	
   	mLastReceivedTimeStamp=(timeStamp+(65536*mCurrentTimeStampCycle));
   	calibratedTimeStamp=mLastReceivedTimeStamp/32768*1000;   // to convert into mS
   return calibratedTimeStamp;
   }
	    
   private double[] calibrateInertialSensorData(double[] data, double[][] AM, double[][] SM, double[][] OV) {
		/*  Based on the theory outlined by Ferraris F, Grimaldi U, and Parvis M.  
           in "Procedure for effortless in-field calibration of three-axis rate gyros and accelerometers" Sens. Mater. 1995; 7: 311-30.            
           C = [R^(-1)] .[K^(-1)] .([U]-[B])
			where.....
			[C] -> [3 x n] Calibrated Data Matrix 
			[U] -> [3 x n] Uncalibrated Data Matrix
			[B] ->  [3 x n] Replicated Sensor Offset Vector Matrix 
			[R^(-1)] -> [3 x 3] Inverse Alignment Matrix
			[K^(-1)] -> [3 x 3] Inverse Sensitivity Matrix
			n = Number of Samples
			*/
      double [][] data2d=new double [3][1];
      data2d[0][0]=data[0];
      data2d[1][0]=data[1];
      data2d[2][0]=data[2];
      data2d= matrixmultiplication(matrixmultiplication(matrixinverse3x3(AM),matrixinverse3x3(SM)),matrixminus(data2d,OV));
      data[0]=data2d[0][0];
      data[1]=data2d[1][0];
      data[2]=data2d[2][0];
      return data;
	}
	    
   private double calibrateU12AdcValue(double uncalibratedData,double offset,double vRefP,double gain){
		double calibratedData=(uncalibratedData-offset)*(((vRefP*1000)/gain)/4095);
   	return calibratedData;
   }
	    
   private double calibrateGsrData(double gsrUncalibratedData,double p1, double p2, double p3, double p4, double p5){
       gsrUncalibratedData = (double)((int)gsrUncalibratedData & 4095); 
       double gsrCalibratedData = (p1*Math.pow(gsrUncalibratedData,4)+p2*Math.pow(gsrUncalibratedData,3)+p3*Math.pow(gsrUncalibratedData,2)+p4*gsrUncalibratedData+p5)/1000;
       return gsrCalibratedData;  
   }
	    
   public ObjectCluster buildMsg(byte[] newPacket,String... Instructions)
   {
   	ObjectCluster objectCluster=new ObjectCluster(mMyName);
   	double [] calibratedData=new double[mNChannels + 1]; //plus 1 because of the time stamp
   	int[] newPacketInt=parsedData(newPacket,mSignalDataTypeArray);
   	double[] tempData=new double[3];
   	for (int i=0;i<Instructions.length;i++) {
   		if ((Instructions[i]=="a" || Instructions[i]=="c")) {
   			int iTimeStamp=getSignalIndex("TimeStamp"); //find index
   			tempData[0]=(double)newPacketInt[1];
   			objectCluster.mPropertyCluster.put("TimeStamp",new FormatCluster("Uncalibrated","u16",(double)newPacketInt[iTimeStamp]));
			    objectCluster.mPropertyCluster.put("TimeStamp",new FormatCluster("Calibrated","ms",calibrateTimeStamp((double)newPacketInt[iTimeStamp])));
				
   		    if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
			    int iAccelX=getSignalIndex("Accelerometer X"); //find index
			    int iAccelY=getSignalIndex("Accelerometer Y"); //find index
			    int iAccelZ=getSignalIndex("Accelerometer Z"); //find index
			    tempData[0]=(double)newPacketInt[iAccelX];
			    tempData[1]=(double)newPacketInt[iAccelY];
			    tempData[2]=(double)newPacketInt[iAccelZ];
			    double[] accelCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixAccel, SensitivityMatrixAccel, OffsetVectorAccel);
			    calibratedData[iAccelX]=accelCalibratedData[0];
			    calibratedData[iAccelY]=accelCalibratedData[1];
			    calibratedData[iAccelZ]=accelCalibratedData[2];
			    
			    objectCluster.mPropertyCluster.put("AccelerometerX",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iAccelX]));
			    objectCluster.mPropertyCluster.put("AccelerometerY",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iAccelY]));
			    objectCluster.mPropertyCluster.put("AccelerometerZ",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iAccelZ]));
			    if (mDefaultCalibrationParametersAccel == true) {
    				    objectCluster.mPropertyCluster.put("AccelerometerX",new FormatCluster("Calibrated","m/(sec^2)*",accelCalibratedData[0]));
    				    objectCluster.mPropertyCluster.put("AccelerometerY",new FormatCluster("Calibrated","m/(sec^2)*",accelCalibratedData[1]));
    				    objectCluster.mPropertyCluster.put("AccelerometerZ",new FormatCluster("Calibrated","m/(sec^2)*",accelCalibratedData[2]));
			    } else {
			    	objectCluster.mPropertyCluster.put("AccelerometerX",new FormatCluster("Calibrated","m/(sec^2)",accelCalibratedData[0]));
   				    objectCluster.mPropertyCluster.put("AccelerometerY",new FormatCluster("Calibrated","m/(sec^2)",accelCalibratedData[1]));
   				    objectCluster.mPropertyCluster.put("AccelerometerZ",new FormatCluster("Calibrated","m/(sec^2)",accelCalibratedData[2]));
			    }
			    	
			}
   		    
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				    int iGyroX=getSignalIndex("Gyroscope X");
				    int iGyroY=getSignalIndex("Gyroscope Y");
				    int iGyroZ=getSignalIndex("Gyroscope Z");
				    tempData[0]=(double)newPacketInt[iGyroX];
				    tempData[1]=(double)newPacketInt[iGyroY];
				    tempData[2]=(double)newPacketInt[iGyroZ];
				    double[] gyroCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixGyro, SensitivityMatrixGyro, OffsetVectorGyro);
				    calibratedData[iGyroX]=gyroCalibratedData[0];
				    calibratedData[iGyroY]=gyroCalibratedData[1];
				    calibratedData[iGyroZ]=gyroCalibratedData[2];
			    
				    objectCluster.mPropertyCluster.put("GyroscopeX",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iGyroX]));
			    objectCluster.mPropertyCluster.put("GyroscopeY",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iGyroY]));
			    objectCluster.mPropertyCluster.put("GyroscopeZ",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iGyroZ]));
			    if (mDefaultCalibrationParametersGyro == true) {
    				    objectCluster.mPropertyCluster.put("GyroscopeX",new FormatCluster("Calibrated","deg/s*",gyroCalibratedData[0]));
    				    objectCluster.mPropertyCluster.put("GyroscopeY",new FormatCluster("Calibrated","deg/s*",gyroCalibratedData[1]));
    				    objectCluster.mPropertyCluster.put("GyroscopeZ",new FormatCluster("Calibrated","deg/s*",gyroCalibratedData[2]));
			    } else {
    				    objectCluster.mPropertyCluster.put("GyroscopeX",new FormatCluster("Calibrated","deg/s",gyroCalibratedData[0]));
    				    objectCluster.mPropertyCluster.put("GyroscopeY",new FormatCluster("Calibrated","deg/s",gyroCalibratedData[1]));
    				    objectCluster.mPropertyCluster.put("GyroscopeZ",new FormatCluster("Calibrated","deg/s",gyroCalibratedData[2]));
			    } 
			    
				}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				    int iMagX=getSignalIndex("Magnetometer X");
				    int iMagY=getSignalIndex("Magnetometer Y");
				    int iMagZ=getSignalIndex("Magnetometer Z");
				    tempData[0]=(double)newPacketInt[iMagX];
				    tempData[1]=(double)newPacketInt[iMagY];
				    tempData[2]=(double)newPacketInt[iMagZ];
				    double[] magCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixMag, SensitivityMatrixMag, OffsetVectorMag);
				    calibratedData[iMagX]=magCalibratedData[0];
				    calibratedData[iMagY]=magCalibratedData[1];
				    calibratedData[iMagZ]=magCalibratedData[2];
				    
				    objectCluster.mPropertyCluster.put("MagnetometerX",new FormatCluster("Uncalibrated","i16",(double)newPacketInt[iMagX]));
			    objectCluster.mPropertyCluster.put("MagnetometerY",new FormatCluster("Uncalibrated","i16",(double)newPacketInt[iMagY]));
			    objectCluster.mPropertyCluster.put("MagnetometerZ",new FormatCluster("Uncalibrated","i16",(double)newPacketInt[iMagZ]));
			    if (mDefaultCalibrationParametersMag == true) {
    				    objectCluster.mPropertyCluster.put("MagnetometerX",new FormatCluster("Calibrated","local*",magCalibratedData[0]));
    				    objectCluster.mPropertyCluster.put("MagnetometerY",new FormatCluster("Calibrated","local*",magCalibratedData[1]));
    				    objectCluster.mPropertyCluster.put("MagnetometerZ",new FormatCluster("Calibrated","local*",magCalibratedData[2]));
			    } else {
			    	objectCluster.mPropertyCluster.put("MagnetometerX",new FormatCluster("Calibrated","local",magCalibratedData[0]));
    				    objectCluster.mPropertyCluster.put("MagnetometerY",new FormatCluster("Calibrated","local",magCalibratedData[1]));
    				    objectCluster.mPropertyCluster.put("MagnetometerZ",new FormatCluster("Calibrated","local",magCalibratedData[2]));
			    }
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_GSR) > 0) {
				    int iGSR = getSignalIndex("GSR Raw");
				    tempData[0] = (double)newPacketInt[iGSR];
				    int newGSRRange = -1; // initialized to -1 so it will only come into play if mGSRRange = 4  
				    
				    double p1=0,p2=0,p3=0,p4=0,p5=0;
				    		if (mGSRRange==4){
				    		    newGSRRange=(49152 & (int)tempData[0])>>14; 
				    		}
		                    if (mGSRRange==0 || newGSRRange==0) {
		                        p1 = 6.5995E-9;
		                        p2 = -6.895E-5;
		                        p3 = 2.699E-1;
		                        p4 = -4.769835E+2;
		                        p5 = 3.403513341E+5;
		                    } else if (mGSRRange==1 || newGSRRange==1) {
		                        p1 = 1.3569627E-8;
		                        p2 = -1.650399E-4;
		                        p3 = 7.54199E-1;
		                        p4 = -1.5726287856E+3;
		                        p5 = 1.367507927E+6;
		                    } else if (mGSRRange==2 || newGSRRange==2) {
		                        p1 = 2.550036498E-8;
		                        p2 = -3.3136E-4;
		                        p3 = 1.6509426597E+0;
		                        p4 = -3.833348044E+3;
		                        p5 = 3.8063176947E+6;
		                    } else if (mGSRRange==3  || newGSRRange==3) {
		                        p1 = 3.7153627E-7;
		                        p2 = -4.239437E-3;
		                        p3 = 1.7905709E+1;
		                        p4 = -3.37238657E+4;
		                        p5 = 2.53680446279E+7;
		                    }
				    
		                    calibratedData[iGSR] = calibrateGsrData(tempData[0],p1,p2,p3,p4,p5);
		                    objectCluster.mPropertyCluster.put("GSR",new FormatCluster("Uncalibrated","u16",(double)newPacketInt[iGSR]));
	    				    objectCluster.mPropertyCluster.put("GSR",new FormatCluster("Calibrated","kohms",calibratedData[iGSR]));
			}
			
			if (((mEnabledSensors & 0xFF) & SENSOR_ECG) > 0) {
				    int iECGRALL = getSignalIndex("ECG RA LL");
				    int iECGLALL = getSignalIndex("ECG LA LL");
				    tempData[0] = (double)newPacketInt[iECGRALL];
				    tempData[1] = (double)newPacketInt[iECGLALL];
				    calibratedData[iECGRALL]=calibrateU12AdcValue(tempData[0],2060,3,175);
				    calibratedData[iECGLALL]=calibrateU12AdcValue(tempData[1],2060,3,175);
				    objectCluster.mPropertyCluster.put("ECGRALL",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iECGRALL]));
			    objectCluster.mPropertyCluster.put("ECGRALL",new FormatCluster("Calibrated","mV",calibratedData[iECGRALL]));
			    objectCluster.mPropertyCluster.put("ECGLALL",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iECGLALL]));
			    objectCluster.mPropertyCluster.put("ECGLALL",new FormatCluster("Calibrated","mV",calibratedData[iECGLALL]));
			}
			
			if (((mEnabledSensors & 0xFF) & SENSOR_EMG) > 0) {
				    int iEMG = getSignalIndex("EMG");
				    tempData[0] = (double)newPacketInt[iEMG];
				    calibratedData[iEMG]=calibrateU12AdcValue(tempData[0],2060,3,750);
				    objectCluster.mPropertyCluster.put("EMG",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iEMG]));
			    objectCluster.mPropertyCluster.put("EMG",new FormatCluster("Calibrated","mV",calibratedData[iEMG]));
				}
			if (((mEnabledSensors & 0xFF00) & SENSOR_STRAIN) > 0) {
				    int iSGHigh = getSignalIndex("Strain Gauge High");
				    int iSGLow = getSignalIndex("Strain Gauge Low");
				    tempData[0] = (double)newPacketInt[iSGHigh];
				    tempData[1] = (double)newPacketInt[iSGLow];
				    calibratedData[iSGHigh]=calibrateU12AdcValue(tempData[0],60,3,551*2.8);
				    calibratedData[iSGLow]=calibrateU12AdcValue(tempData[0],1950,3,183.7*2.8);
				    objectCluster.mPropertyCluster.put("Strain Gauge High",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iSGHigh]));
			    objectCluster.mPropertyCluster.put("Strain Gauge High",new FormatCluster("Calibrated","mV",calibratedData[iSGHigh]));
			    objectCluster.mPropertyCluster.put("Strain Gauge Low",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iSGLow]));
			    objectCluster.mPropertyCluster.put("Strain Gauge Low",new FormatCluster("Calibrated","mV",calibratedData[iSGLow]));
			    
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_HEART) > 0) {
				    int iHeartRate = getSignalIndex("Heart Rate");
				    tempData[0] = (double)newPacketInt[iHeartRate];
				    calibratedData[iHeartRate]=tempData[0];
				    objectCluster.mPropertyCluster.put("Heart Rate",new FormatCluster("Calibrated","bpm",calibratedData[iHeartRate]));
    		
			}
			
			if (((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0) {
				    int iA0 = getSignalIndex("Exp Board A0");
				    tempData[0] = (double)newPacketInt[iA0];
				    calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
				    objectCluster.mPropertyCluster.put("ExpBoardA0",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iA0]));
			    objectCluster.mPropertyCluster.put("ExpBoardA0",new FormatCluster("Calibrated","mV",calibratedData[iA0]));
				}
			
			if (((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0) {
				int iA7 = getSignalIndex("Exp Board A7");
				    tempData[0] = (double)newPacketInt[iA7];
				    calibratedData[iA7]=calibrateU12AdcValue(tempData[0],0,3,1);
				    objectCluster.mPropertyCluster.put("ExpBoardA7",new FormatCluster("Uncalibrated","u12",(double)newPacketInt[iA7]));
			    objectCluster.mPropertyCluster.put("ExpBoardA7",new FormatCluster("Calibrated","mV",calibratedData[iA7]));
				}
		}
   	}
   	return objectCluster;
   }
       
   	public byte[] convertstacktobytearray(Stack<Byte> b,int packetSize) {
   	    byte[] returnByte=new byte[packetSize];
   	    b.remove(0); //remove the Data Packet identifier 
   		for (int i=0;i<packetSize;i++) {
   		    returnByte[packetSize-1-i]=(byte) b.pop();
   		}
   		return returnByte;
   	}
   	
   	
   	/*
   	 * Configure/Read Settings Methods
   	 * */
   	
   	public void writeEnabledSensors(int enabledSensors) {
    	while(getInstructionStatus()==false) {};
    	byte highByte=(byte)((enabledSensors&65280)>>8);
    	byte lowByte=(byte)(enabledSensors & 0xFF);
    	mCurrentCommand=SET_SENSORS_COMMAND;
    	write(new byte[]{SET_SENSORS_COMMAND,(byte) lowByte, highByte});
    	mTempIntValue=enabledSensors;
    	mWaitForAck=true;
    	mTransactionCompleted=false;
    	responseTimer(ACK_TIMER_DURATION+9); // This command takes a little longer for the shimmer device to process thus a longer timer duration
	}
        
    public void writeAccelRange(int range) {
    	  /*  INPUT: accelRange - Numeric value defining the desired accelerometer range.
            Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) 
            and 3 (+/- 6g). 
            Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).*/
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=SET_ACCEL_SENSITIVITY_COMMAND;
        write(new byte[]{SET_ACCEL_SENSITIVITY_COMMAND, (byte)range});
        mTransactionCompleted=false;
        responseTimer(ACK_TIMER_DURATION);
        mTempIntValue=range;
        mWaitForAck=true;
    }
    
    public void writeGSRRange(int range) {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=SET_GSR_RANGE_COMMAND;
    	write(new byte[]{SET_GSR_RANGE_COMMAND, (byte)range});
    	mTransactionCompleted=false;
    	responseTimer(ACK_TIMER_DURATION);
    	mTempIntValue=range;
    	mWaitForAck=true;
    }
    
    public void readGSRRange() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=GET_GSR_RANGE_COMMAND;
    	write(new byte[]{GET_GSR_RANGE_COMMAND});
    	mTransactionCompleted=false;
    	mWaitForAck=true;
    	responseTimer(ACK_TIMER_DURATION);
    }
    
    public void readAccelRange() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=GET_ACCEL_SENSITIVITY_COMMAND;
    	write(new byte[]{GET_ACCEL_SENSITIVITY_COMMAND});
		mWaitForAck=true;
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION);
    }
    public void readShimmerVersion() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=GET_SHIMMER_VERSION_COMMAND;
		write(new byte[]{GET_SHIMMER_VERSION_COMMAND});
		mWaitForAck=true;
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION);	
	}
	
	public void readConfigByte0() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=GET_CONFIG_BYTE0_COMMAND;   
		write(new byte[]{GET_CONFIG_BYTE0_COMMAND});
		mWaitForAck=true;
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION);		
	}
	
	public void writeConfigByte0(int configByte0) {
    	while(getInstructionStatus()==false) {};
		if (getState() == STATE_CONNECTED) {
			mCurrentCommand=SET_CONFIG_BYTE0_COMMAND;
			write(new byte[]{SET_CONFIG_BYTE0_COMMAND,(byte) configByte0});
			mTempByteValue=(byte) configByte0;
			mWaitForAck=true;
			mTransactionCompleted=false;
			responseTimer(ACK_TIMER_DURATION);		
		}
	}

	/**
	 * @param rate Defines the sampling rate to be set (e.g.51.2 sets the sampling rate to 51.2Hz). User should refer to the document Sampling Rate Table to see all possible values.
	 */
	public void writeSamplingRate(double rate) {
    	while(getInstructionStatus()==false) {};
		if (getState() == STATE_CONNECTED) {
			mCurrentCommand=SET_SAMPLING_RATE_COMMAND;
			mWaitForAck=true;
			mTempDoubleValue=rate;
			rate=1024/rate; //the equivalent hex setting
			write(new byte[]{SET_SAMPLING_RATE_COMMAND, (byte)Math.rint(rate), 0x00}); // set to stream accel only;
			responseTimer(ACK_TIMER_DURATION);
			mTransactionCompleted=false;
		}
	}
	
	public void readSamplingRate() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=GET_SAMPLING_RATE_COMMAND;
		mWaitForAck=true;
		write(new byte[]{GET_SAMPLING_RATE_COMMAND});
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION);
	}
	
	public void readCalibrationParameters(String sensor) {
    	while(getInstructionStatus()==false) {};
		if (getState() == STATE_CONNECTED) {
			mTransactionCompleted=false;
			if (sensor=="Accelerometer") {
				mCurrentCommand=GET_ACCEL_CALIBRATION_COMMAND;
				write(new byte[]{GET_ACCEL_CALIBRATION_COMMAND});
				}
			else if (sensor=="Gyroscope") {
				mCurrentCommand=GET_GYRO_CALIBRATION_COMMAND;
				write(new byte[]{GET_GYRO_CALIBRATION_COMMAND});
				}
			else if (sensor=="Magnetometer") {
				mCurrentCommand=GET_MAG_CALIBRATION_COMMAND;
				write(new byte[]{GET_MAG_CALIBRATION_COMMAND});
				}
			mWaitForAck=true;
			responseTimer(ACK_TIMER_DURATION);
		}
	}
	
	public void writePMux(int setBit) {
    	while(getInstructionStatus()==false) {};
		//Bit value defining the desired setting of the PMux (1=ON, 0=OFF).
		byte newConfigByte0=(byte)mConfigByte0;
		if (setBit==1) {
			newConfigByte0=(byte) (mConfigByte0|64); 
		}
		else if (setBit==0) {
			newConfigByte0=(byte)(mConfigByte0 ^ 191);
		}
		mTempByteValue=newConfigByte0;
		mCurrentCommand=SET_PMUX_COMMAND;
		write(new byte[]{SET_PMUX_COMMAND,(byte) newConfigByte0});
		mWaitForAck=true;
		mTransactionCompleted=false;
		responseTimer(ACK_TIMER_DURATION);		
	}
	
	public void writeFiveVoltReg(int setBit) {
    	while(getInstructionStatus()==false) {};
		//Bit value defining the desired setting of the 5VReg (1=ON, 0=OFF).
				byte newConfigByte0=(byte)mConfigByte0;
				if (setBit==1) {
					newConfigByte0=(byte) (mConfigByte0|128); 
				}
				else if (setBit==0) {
					newConfigByte0=(byte)(mConfigByte0 ^ 127);
				}
				mTempByteValue=newConfigByte0;
				mCurrentCommand=SET_5V_REGULATOR_COMMAND;
				write(new byte[]{SET_5V_REGULATOR_COMMAND,(byte) newConfigByte0});
				mTransactionCompleted=false;
				mWaitForAck=true;
				responseTimer(ACK_TIMER_DURATION);	
	}
	public void toggleLed() {
    	while(getInstructionStatus()==false) {};
    	mCurrentCommand=TOGGLE_LED_COMMAND;
		write(new byte[]{TOGGLE_LED_COMMAND});
		mWaitForAck=true;
		responseTimer(ACK_TIMER_DURATION);	
	}
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
}