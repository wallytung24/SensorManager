package com.sensormanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class ThresholdActivity extends Activity {
	
	private EditText etBHBR;
	private EditText etBHHR;
	private EditText etBHHRV;
	private EditText etSHSCL;
	private EditText etSHSCR;
	private EditText etWindow;
	private EditText etBHBRupper;
	private EditText etBHHRupper;
	private EditText etBHHRVupper;
	private EditText etSHSCLupper;
	private EditText etSHSCRupper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.game_activity_threshold);
		
		TextView shwin = (TextView) findViewById(R.id.tvWindow);
		etWindow = (EditText) findViewById(R.id.etWindow);
		etWindow.setText(String.valueOf(GlobalConstant.windowShimmer));
		
		TextView vbhbr = (TextView) findViewById(R.id.tvBHBR);
		etBHBR = (EditText) findViewById(R.id.etBHBR);
		etBHBRupper = (EditText) findViewById(R.id.etBHBRupper);
		
		if(GlobalConstant.isCheck_BH_BR) {
			etBHBR.setVisibility(View.VISIBLE);
			etBHBRupper.setVisibility(View.VISIBLE);
			vbhbr.setVisibility(View.VISIBLE); 
		}
		else {
			etBHBR.setVisibility(View.INVISIBLE);
			etBHBRupper.setVisibility(View.INVISIBLE);
			vbhbr.setVisibility(View.INVISIBLE);
		}
		etBHBR.setText(String.valueOf(GlobalConstant.thresholdBHBR));
		etBHBRupper.setText(String.valueOf(GlobalConstant.thresholdBHBRupper));
		
		etBHHR = (EditText) findViewById(R.id.etBHHR);
		etBHHRupper = (EditText) findViewById(R.id.etBHHRupper);
		TextView vbhhr = (TextView) findViewById(R.id.tvBHHR);
		if(GlobalConstant.isCheck_BH_HR) {
			etBHHR.setVisibility(View.VISIBLE);
			etBHHRupper.setVisibility(View.VISIBLE);
			vbhhr.setVisibility(View.VISIBLE);
		}
		else { 
			etBHHR.setVisibility(View.INVISIBLE);
			etBHHRupper.setVisibility(View.INVISIBLE);
			vbhhr.setVisibility(View.INVISIBLE);
		}
		etBHHR.setText(String.valueOf(GlobalConstant.thresholdBHHR));
		etBHHRupper.setText(String.valueOf(GlobalConstant.thresholdBHHRupper));
		
		etBHHRV = (EditText) findViewById(R.id.etBHHRV);
		etBHHRVupper = (EditText) findViewById(R.id.etBHHRVupper);
		TextView vbhhrv = (TextView) findViewById(R.id.tvBHHRV);
		if(GlobalConstant.isCheck_BH_HRV) {
			etBHHRV.setVisibility(View.VISIBLE);
			etBHHRVupper.setVisibility(View.VISIBLE);
			vbhhrv.setVisibility(View.VISIBLE);
		}
		else { 
			etBHHRV.setVisibility(View.INVISIBLE);
			etBHHRVupper.setVisibility(View.INVISIBLE);
			vbhhrv.setVisibility(View.INVISIBLE);
		}
		etBHHRV.setText(String.valueOf(GlobalConstant.thresholdBHHRV));
		etBHHRVupper.setText(String.valueOf(GlobalConstant.thresholdBHHRVupper));

		etSHSCL = (EditText) findViewById(R.id.etSHSCL);
		etSHSCLupper = (EditText) findViewById(R.id.etSHSCLupper);
		TextView vshscl = (TextView) findViewById(R.id.tvSHSCL); 
		if(GlobalConstant.isCheck_Shim_SCL) {
			etSHSCL.setVisibility(View.VISIBLE);
			etSHSCLupper.setVisibility(View.VISIBLE);
			vshscl.setVisibility(View.VISIBLE);
		}
		else { 
			etSHSCL.setVisibility(View.INVISIBLE);
			etSHSCLupper.setVisibility(View.INVISIBLE);
			vshscl.setVisibility(View.INVISIBLE);
		}
		etSHSCL.setText(String.valueOf(GlobalConstant.thresholdSHSCL));
		etSHSCLupper.setText(String.valueOf(GlobalConstant.thresholdSHSCLupper));

		etSHSCR = (EditText) findViewById(R.id.etSHSCR);
		etSHSCRupper = (EditText) findViewById(R.id.etSHSCRupper);
		TextView vshscr = (TextView) findViewById(R.id.tvSHSCR);
		if(GlobalConstant.isCheck_Shim_SCR) {
			etSHSCR.setVisibility(View.VISIBLE);
			etSHSCRupper.setVisibility(View.VISIBLE);
			vshscr.setVisibility(View.VISIBLE);
		}
		else {
			etSHSCR.setVisibility(View.INVISIBLE);
			etSHSCRupper.setVisibility(View.INVISIBLE);
			vshscr.setVisibility(View.INVISIBLE);
		}
		etSHSCR.setText(String.valueOf(GlobalConstant.thresholdSHSCR));
		etSHSCRupper.setText(String.valueOf(GlobalConstant.thresholdSHSCRupper));
		
		if(GlobalConstant.isCheck_Shim_SCL || GlobalConstant.isCheck_Shim_SCR)
		{
			etWindow.setVisibility(View.VISIBLE);
			shwin.setVisibility(View.VISIBLE);
		}
		else
		{
			etWindow.setVisibility(View.INVISIBLE);
			shwin.setVisibility(View.INVISIBLE);
		}
		Button btThreshold = (Button) findViewById(R.id.btThreshold);
		btThreshold.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				GlobalConstant.windowShimmer = Float.parseFloat(etWindow.getText().toString());
				GlobalConstant.thresholdBHBR = Float.parseFloat(etBHBR.getText().toString());
				GlobalConstant.thresholdBHBRupper = Float.parseFloat(etBHBRupper.getText().toString());
				GlobalConstant.thresholdBHHR = Float.parseFloat(etBHHR.getText().toString());
				GlobalConstant.thresholdBHHRupper = Float.parseFloat(etBHHRupper.getText().toString());
				GlobalConstant.thresholdBHHRV = Float.parseFloat(etBHHRV.getText().toString());
				GlobalConstant.thresholdBHHRVupper = Float.parseFloat(etBHHRVupper.getText().toString());
				GlobalConstant.thresholdSHSCL = Float.parseFloat(etSHSCL.getText().toString());
				GlobalConstant.thresholdSHSCLupper = Float.parseFloat(etSHSCLupper.getText().toString());
				GlobalConstant.thresholdSHSCR = Float.parseFloat(etSHSCR.getText().toString());
				GlobalConstant.thresholdSHSCRupper = Float.parseFloat(etSHSCRupper.getText().toString());
				Intent startActivity = new Intent(ThresholdActivity.this, OptionsActivity.class);
				startActivity(startActivity);
				finish();
			}
		});
	}
}
