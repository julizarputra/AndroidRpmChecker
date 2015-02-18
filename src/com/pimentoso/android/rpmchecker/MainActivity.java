package com.pimentoso.android.rpmchecker;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Main activity for Mini4WD RPM Checker.
 * 
 * Credits to xdebugx.net (Jeremiah McLeod) 8-8-2010
 * for the zero crossing algorhythm
 * 
 * @author Pimentoso
 */
public class MainActivity extends Activity implements OnClickListener {
	
	private static final int MOTOR_DETECT_FREQUENCY_THRESHOLD = 20;
	
	private TextView label;	
	private Button buttonStart;
	private MyAsyncTask recorderTask;
	private ArrayList<Integer> frequencies;
	private boolean recording = false;
	
	private class MyAsyncTask extends AsyncTask<Void, String, Void> {
		
		private int frequency;
		private boolean tuneOk = false;
		private boolean error = false;
		
		@Override
		protected Void doInBackground(Void... params) {

			AudioRecord recorder;
			int numCrossing, numSamples, p;
			short audioData[];
			int bufferSize;

			bufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 3;
			recorder = new AudioRecord(
					AudioSource.VOICE_RECOGNITION, // use this instead of AudioSource.MIC for better results
					8000, 
					AudioFormat.CHANNEL_IN_MONO, 
					AudioFormat.ENCODING_PCM_16BIT, 
					bufferSize);

			frequencies = new ArrayList<Integer>();
			audioData = new short[bufferSize]; //short array that pcm data is put into.
			
			while (recording) {
				if (recorder.getState() == android.media.AudioRecord.STATE_INITIALIZED) {
					if (recorder.getRecordingState() == android.media.AudioRecord.RECORDSTATE_STOPPED) {
						recorder.startRecording();
					}
					else {
						recorder.read(audioData, 0, bufferSize); //read the PCM audio data into the audioData array

						//Now we need to decode the PCM data using the Zero Crossings Method

						numCrossing = 0;
						numSamples = 0;
						recorder.read(audioData, 0, bufferSize);
						int mod = (int) (bufferSize / 4) * 4;
						for (p = 0; p < mod; p += 4) {
							if (audioData[p] > 0 && audioData[p + 1] <= 0)
								numCrossing++;
							if (audioData[p] < 0 && audioData[p + 1] >= 0)
								numCrossing++;
							if (audioData[p + 1] > 0 && audioData[p + 2] <= 0)
								numCrossing++;
							if (audioData[p + 1] < 0 && audioData[p + 2] >= 0)
								numCrossing++;
							if (audioData[p + 2] > 0 && audioData[p + 3] <= 0)
								numCrossing++;
							if (audioData[p + 2] < 0 && audioData[p + 3] >= 0)
								numCrossing++;
							if (audioData[p + 3] > 0 && audioData[p + 4] <= 0)
								numCrossing++;
							if (audioData[p + 3] < 0 && audioData[p + 4] >= 0)
								numCrossing++;
							numSamples += 4;
						}

						for (p = 0; p < bufferSize; p++) {
							if (audioData[p] > 0 && audioData[p + 1] <= 0)
								numCrossing++;
							if (audioData[p] < 0 && audioData[p + 1] >= 0)
								numCrossing++;
							numSamples++;
						}

						frequency = (int) ((8000.0 / (float) numSamples) * (float) numCrossing);
						frequencies.add(frequency);
						
						if (frequencies.size() == 10) {
							tuneOk = motorDetected();
							
							if (!tuneOk) {
								error = true;
								recording = false;
							}
						}
						
						if (tuneOk) {
							publishProgress(Integer.toString(frequency));
						}
						
						Log.i("Mini4WD Rpm Checker", Integer.toString(frequency));
					}
				}
			} //while recording
			
			if (recorder.getState() == android.media.AudioRecord.RECORDSTATE_RECORDING) {
				recorder.stop(); //stop the recorder before ending the thread
			}
			
			recorder.release(); //release the recorders resources
			recorder = null; //set the recorder to be garbage collected.

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			if (error) {
				label.setText("Motor not detected");
			}
			else {
				calculate();
			}
		}

		@Override
		protected void onProgressUpdate(String... values) {
			int rpm = frequency; // * 60;
			label.setText(Integer.toString(rpm));
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// force localization, for debugging purposes
		/*
		String languageToLoad  = "ja";
		Locale locale = new Locale(languageToLoad); 
		Locale.setDefault(locale);
		Configuration config = new Configuration();
		config.locale = locale;
		getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
		*/

		setContentView(R.layout.main);
		
		label = (TextView) findViewById(R.id.text_timer);
		buttonStart = (Button) findViewById(R.id.button_start);
		buttonStart.setOnClickListener(this);
	}

	@Override
	public void onStart() {

		super.onStart();

		// show help
		if (DefaultPreferences.get(this, "first_time", "1").equals("1")) {
			showAlertBox();
			DefaultPreferences.put(this, "first_time", "0");
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.button_start: {
				if (recording) {
					recording = false;
					buttonStart.setText("Start");
					label.setText("Detecting motor...");
				}
				else {
					recording = true;
					recorderTask = new MyAsyncTask();
					recorderTask.execute();
					buttonStart.setText("Stop");
				}
			}
		}
	}
	
	private boolean motorDetected() {
		List<Integer> period = frequencies.subList(frequencies.size()-10, frequencies.size());
		int avg = 0;
		for (int f : period) {
			avg += f;
		}
		avg = avg/10;
		int peaks = 0;
		for (int f : period) {
			if (f < avg-MOTOR_DETECT_FREQUENCY_THRESHOLD || f > avg+MOTOR_DETECT_FREQUENCY_THRESHOLD)
				peaks++;
		}
		return peaks < 3;
	}

	private void calculate() {
		int avg = 0;
		int min = 0;
		int max = 0;
		for (int f : frequencies) {
			avg += f;
			if (min == 0 || f < min)
				min = f;
			if (max == 0 || f > max)
				max = f;
		}
		avg = avg / frequencies.size();
		label.setText("average " + avg);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_tutorial: {
			showAlertBox();
			return true;
		}
		}
		return false;
	}

	public void showAlertBox() {
		new AlertDialog.Builder(this).setMessage(getString(R.string.dialog_tutorial_text)).setTitle(getString(R.string.dialog_tutorial_title)).setCancelable(true).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int whichButton) {

			}
		}).show();
	}
}
