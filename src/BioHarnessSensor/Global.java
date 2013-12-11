package BioHarnessSensor;

import java.util.ArrayList;

public class Global {
	
	// variable for filename
	static String profile = null;
	
	// variables for passing data
	public final static int HEART_RATE = 1;
	public final static int HRV = 3;
	public final static int RESPIRATION_RATE = 5;
	public final static int SKIN_TEMPERATURE = 7;
	public final static int BHGSR = 9;


	// variables for computing average HRV
	static ArrayList<Integer> avgHRV = new ArrayList<Integer> ();
	static int HRVcounter = 0;
		
	static int hrtRateMax = 0;
	static int hrtRateMin = 100;
	static int hrvMax = 0;
	static int hrvMin = 100;
	static float respRateMax = 0;
	static float respRateMin = 100;
	static float skinTempMax = 0;
	static float skinTempMin = 100;
	static double gsrMax = 0;
	static double gsrMin = 100;

}