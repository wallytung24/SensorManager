package com.sensormanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;

public class SensorSelectionActivity extends Activity{

	private CheckBox cbBioHarness_BR;
	private CheckBox cbBioHarness_HR;
	private CheckBox cbBioHarness_HRV;
	private CheckBox cbShimmer_SCR;
	private CheckBox cbShimmer_SCL;
//	private boolean isCheck_BH_BR;
//	private boolean isCheck_BH_HR;
//	private boolean isCheck_BH_HRV;
//	private boolean isCheck_Shim_SCR;
//	private boolean isCheck_Shim_SCL;
	private Button backButton;

	public SensorSelectionActivity() {
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.game_activity_option_sensor);
		
		cbBioHarness_BR = (CheckBox) findViewById(R.id.cbBioHarness_BR);
		cbBioHarness_HR = (CheckBox) findViewById(R.id.cbBioHarness_HR);
		cbBioHarness_HRV = (CheckBox) findViewById(R.id.cbBioHarness_HRV);
		cbShimmer_SCR  = (CheckBox) findViewById(R.id.cbShimmer_SCR);
		cbShimmer_SCL  = (CheckBox) findViewById(R.id.cbShimmer_SCL);
		
		cbBioHarness_BR.setChecked(GlobalConstant.isCheck_BH_BR);
		cbBioHarness_HR.setChecked(GlobalConstant.isCheck_BH_HR);
		cbBioHarness_HRV.setChecked(GlobalConstant.isCheck_BH_HRV);
		cbShimmer_SCL.setChecked(GlobalConstant.isCheck_Shim_SCL);
		cbShimmer_SCR.setChecked(GlobalConstant.isCheck_Shim_SCR);
		
		backButton = (Button) findViewById(R.id.btBackToMenu);
		backButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent menu_activity = new Intent(SensorSelectionActivity.this, OptionsActivity.class);
				startActivity(menu_activity);
				finish();
			}
		});
	}
	
	public void onCheckboxClicked(View view) {
		
		boolean checked = ((CheckBox) view).isChecked();
		
		switch(view.getId()) {
        case R.id.cbBioHarness_BR:
        	GlobalConstant.isCheck_BH_BR = checked;
            break;
        case R.id.cbBioHarness_HR:
        	GlobalConstant.isCheck_BH_HR = checked;
            break;
        case R.id.cbBioHarness_HRV:
        	GlobalConstant.isCheck_BH_HRV = checked;
            break;
        case R.id.cbShimmer_SCL:
        	GlobalConstant.isCheck_Shim_SCL = checked;
            break;
        case R.id.cbShimmer_SCR:
        	GlobalConstant.isCheck_Shim_SCR = checked;
            break;
        // TODO: Veggie sandwich
    }
		
	}
}
