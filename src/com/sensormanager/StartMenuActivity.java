package com.sensormanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class StartMenuActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.game_activity_start_screen);
		
		Button start = (Button) findViewById(R.id.startButton);
		start.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent dataStreamActivity = new Intent(StartMenuActivity.this, DataStreamActivity.class);
				startActivity(dataStreamActivity);
			}
		});
		
		Button options = (Button) findViewById(R.id.optionsButton);
		options.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent optionActivity = new Intent(StartMenuActivity.this, OptionsActivity.class);
				startActivity(optionActivity);
				finish();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
