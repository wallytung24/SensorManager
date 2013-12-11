package com.sensormanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class OptionsActivity extends Activity {

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_activity_options);

        // Sensor 
        final Button btSensor = (Button) findViewById(R.id.sensorButton);
        btSensor.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent sensor_selection_activity = new Intent(OptionsActivity.this, SensorSelectionActivity.class);
				startActivity(sensor_selection_activity);
				finish();
			}
		});
        
        final Button bToThreshold = (Button) findViewById(R.id.btToThreshold);
		bToThreshold.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent thresholdActivity = new Intent(OptionsActivity.this, ThresholdActivity.class);
				startActivity(thresholdActivity);
				finish();
				
			}
		});

        
        // Back to Start Screen 
        final Button btBackToStart = (Button) findViewById(R.id.btBackToStart);
        btBackToStart.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent game_start_activity = new Intent(OptionsActivity.this, StartMenuActivity.class);
				startActivity(game_start_activity);
				finish();
			}
		});
        
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
        	finish();
        	
        	//Attempting to go back to the Title Screen
			Intent title_intent = new Intent(OptionsActivity.this, StartMenuActivity.class);
			startActivity(title_intent);
			finish();

        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
    }

}
