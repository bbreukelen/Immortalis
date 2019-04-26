package eu.futuresoftware.immortalis;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MyApplication.getContext().getImmortalis().appStarted(getIntent()); // Log on respawn

        setContentView(R.layout.activity_main);

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("Test", "Crash button pressed");
                throw new NullPointerException(); // Force crash
            }
        });
    }

    // Add this part to disable app restarts when back button has been pressed.
    // Also works with escape on keyboard and right mouse button)
    @Override
    public void onBackPressed() {
        MyApplication.getContext().getImmortalis().onBackPressed();
    }
}
