package wwckl.projectmiki.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;

import wwckl.projectmiki.R;

public class WelcomeActivity extends Activity {
    CheckBox checkBoxShowWelcome;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        checkBoxShowWelcome = (CheckBox) findViewById(R.id.cbShowWelcome);
        loadSavedPreferences();
    }

    private void loadSavedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(this);

        boolean checkBoxValue = sharedPreferences.getBoolean("pref_display_welcome", true);

        if (checkBoxValue) {
            checkBoxShowWelcome.setChecked(true);
        } else {
            checkBoxShowWelcome.setChecked(false);
        }
    }

    private void savePreferencesBool(String key, boolean value) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    private void savePreferencesString(String key, String value) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    @Override
    public boolean onCreateOptionsMenu (Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_welcome, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        // Action bar menu.
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_help:
                Intent myWebLink = new Intent(android.content.Intent.ACTION_VIEW);
                myWebLink.setData(Uri.parse("https://github.com/WomenWhoCode/KL-network/wiki/Project-Miki-Help-File"));
                startActivity(myWebLink);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void resultInputMethodSelected(View view){
        // Save preference of check box value.
        savePreferencesBool("pref_display_welcome", checkBoxShowWelcome.isChecked());

        Intent returnIntent = new Intent();
        String inputMethod = "";

        // return the selected input Method to Main activity
        switch (view.getId()) {
            case R.id.btnEdit:
                inputMethod = getString(R.string.edit);
                break;
            case R.id.btnCamera:
                inputMethod = getString(R.string.camera);
                break;
            case R.id.btnGallery:
                inputMethod = getString(R.string.gallery);
                break;
        }

        savePreferencesString("pref_input_method", inputMethod);

        returnIntent.putExtra("result_input_method", inputMethod);
        setResult(RESULT_OK, returnIntent);
        finish();
    }
}
