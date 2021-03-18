package ca.yorku.eecs.mack.demotiltball78040;

import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class ResultsActivity extends Activity {
    int number_laps ;
    double lap_time;
    double in_path_time;
    int wall_hints;

    TextView wall_hints_tv, in_path_time_tv, lap_time_tv, number_laps_tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        wall_hints_tv = (TextView) findViewById(R.id.wall_hints_status);
        in_path_time_tv = (TextView) findViewById(R.id.in_path_time_status);
        lap_time_tv = (TextView) findViewById(R.id.lap_time_status);
        number_laps_tv = (TextView) findViewById(R.id.number_laps_status);

        Bundle b = getIntent().getExtras();
        wall_hints = b.getInt("wall_hints");
        in_path_time = b.getDouble("in_path_time");
        lap_time = b.getDouble("lap_time");
        number_laps = b.getInt("number_laps");


        wall_hints_tv.setText(Integer.toString(wall_hints));
        in_path_time_tv.setText(Double.toString(in_path_time)+"%");
        lap_time_tv.setText(Double.toString(lap_time)+"s (mean/lap)");
        number_laps_tv.setText(Integer.toString(number_laps));
    }

    // called when the "setup_click" button is tapped
    public void setup_click(View view)
    {
        // start experiment activity
        Intent i = new Intent(getApplicationContext(), DemoTiltBallSetup.class);
        startActivity(i);
        finish();
    }

    /** Called when the "Exit" button is pressed. */
    public void clickExit(View view)
    {
        super.onDestroy(); // cleanup
        finish(); // terminate
    }

}