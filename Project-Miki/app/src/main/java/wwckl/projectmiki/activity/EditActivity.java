package wwckl.projectmiki.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import wwckl.projectmiki.R;
import wwckl.projectmiki.fragment.EditFragment;
import wwckl.projectmiki.models.Bill;
import wwckl.projectmiki.models.ParseBill;
import wwckl.projectmiki.models.Receipt;

public class EditActivity extends AppCompatActivity {
    private Bill mBill;
    private SharedPreferences mSharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set app icon to be displayed on action bar
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Initialise Bill contents
        if (Receipt.getRecognizedText().isEmpty()) {
            // assume new bill
            mBill = (Bill) new ParseBill("1 Item 1.00");
            // no associated image
            Receipt.setReceiptBitmap(null);
        }
        else {
            mBill = (Bill) new ParseBill(Receipt.getRecognizedText());
            if(!mBill.isBillBalanced()){
                displayToast(getString(R.string.bill_not_balanced), false);
            }
        }

        // get preference manager
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new EditFragment())
                .commit();
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

    public void startBillSplitting(){
        Intent intent = new Intent(this, BillSplitterActivity.class);
        intent.putExtra("Bill", mBill);
        startActivity(intent);
    }

    public Bill getActivityBill() {
        return mBill;
    }

    public void setActivityBill(Bill bill) {
        mBill = bill;
    }

    public void displayToast(String toastString, Boolean isHelper) {
        if (isHelper) {
            boolean displayHelper = mSharedPrefs.getBoolean(getString(R.string.pref_show_help_messages), true);

            if (!displayHelper)
                return;
        }

        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast,
                (ViewGroup) findViewById(R.id.toast_layout_root));

        TextView text = (TextView) layout.findViewById(R.id.text);
        text.setText(toastString);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}
