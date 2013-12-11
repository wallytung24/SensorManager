package com.sensormanager;

import java.util.Vector;

public class GlobalConstant {
	// Defaults to turn on all of the data 
	public static boolean isCheck_BH_BR = false;
	public static boolean isCheck_BH_HR = false;
	public static boolean isCheck_BH_HRV = false;
	public static boolean isCheck_Shim_SCR = false;
	public static boolean isCheck_Shim_SCL = false;
	
	public static boolean isCalibrated = false;
	
	
	public static Vector<float[]> thresholds;
	
	public static double windowShimmer = 30;
	
	public static float thresholdBHBR = 0;
	public static float thresholdBHHR = 0;
	public static float thresholdBHHRV = 0;
	public static float thresholdSHSCL = 0;
	public static float thresholdSHSCR = 0;
	public static float thresholdBHBRupper = 0;
	public static float thresholdBHHRupper = 0;
	public static float thresholdBHHRVupper = 0;
	public static float thresholdSHSCLupper = 0;
	public static float thresholdSHSCRupper = 0;

	
	enum Device {
		BioHarness,
		Shimmer,
	}
	
	enum Sensor {
		BreathRateBioHarness,
		HeartRateBioHarness,
		HRVBioHarness,
		SCLShimmer, 
		SCRSHimmer,
	}
	
	public GlobalConstant() {
		
	}

}
