package wwckl.projectmiki.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import wwckl.projectmiki.R;
import wwckl.projectmiki.models.Receipt;


public class MainActivity extends AppCompatActivity {
    final int REQUEST_INPUT_METHOD = 1;  // for checking of requestCode onActivityResult
    final int REQUEST_PICTURE_MEDIASTORE = 2;

    private String mInputMethod = ""; // whether to start Gallery or Camera
    private String mPicturePath = ""; // path of where the picture is saved.
    private ActionMode mActionMode = null; // for Context Action Bar
    private Bitmap mReceiptPicture = null; // bitmap image of the receipt
    private Boolean mDoubleBackToExitPressedOnce = false;
    private Uri mPictureUri = null; // for passing to image editor to crop image
    private float[] mColorMatrix = new float[] { // Default black and white matrix
            0.5f, 0.5f, 0.5f, 0, 0,
            0.5f, 0.5f, 0.5f, 0, 0,
            0.5f, 0.5f, 0.5f, 0, 0,
            0, 0, 0,  1, 0};

    private ImageView mImageView;
    private TextView mTextView;
    private TextView mAdjustContrastTextView;
    private SeekBar mBrightnessBar;
    private SeekBar mContrastBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get layout objects for manipulation later.
        mTextView = (TextView)findViewById(R.id.textView);
        mAdjustContrastTextView = (TextView)findViewById(R.id.tvAdjustContrast);
        mBrightnessBar = (SeekBar)findViewById(R.id.brightnessBar);
        mContrastBar = (SeekBar)findViewById(R.id.contrastBar);

        // Setup Listener for Brightness Seek Bar
        mBrightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float contrastValue = convertContrastValue(mContrastBar.getProgress());
                adjustContrastBrightness(contrastValue, convertBrightnessValue(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Setup Listener for Contrast Seek Bar
        mContrastBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float brightnessValue = convertBrightnessValue(mBrightnessBar.getProgress());
                adjustContrastBrightness(convertContrastValue(progress), brightnessValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Set up listener for menu for onClick of Image
        // to allow user to rotate and crop image
        mImageView = (ImageView) findViewById(R.id.imageView);
        mImageView.setOnClickListener(new View.OnClickListener() {
            // Called when the user clicks on ImageView
            @Override
            public void onClick(View view) {
                if (mActionMode != null) {
                    return;
                }
                // Start the CAB using the ActionMode.Callback defined above
                mActionMode = MainActivity.this.startActionMode(mActionModeCallback);
                view.setSelected(true);
            }
        });

        // if this is the first time loading this activity
        if (savedInstanceState == null) {
            // Check to run Welcome Activity
            // or retrieve default input method
            getDefaultInputMethod();
        }
    }

    // on returning to activity from another activity.
    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first

        if(mPicturePath.isEmpty()){
            // Prompt user to Get image of receipt
            mTextView.setText(getString(R.string.take_a_photo_receipt)
                    + "\n or \n"
                    + getString(R.string.select_image_from_gallery));
            mAdjustContrastTextView.setVisibility(View.INVISIBLE);
            mBrightnessBar.setVisibility(View.INVISIBLE);
            mContrastBar.setVisibility(View.INVISIBLE);
        }
        else{ // image will be displayed, hide text.
            mTextView.setText(getString(R.string.adjust_brightness));
            applyFilter();
            mAdjustContrastTextView.setVisibility(View.VISIBLE);
            mBrightnessBar.setVisibility(View.VISIBLE);
            mContrastBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Action bar menu; perform activity based on menu item selected.
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_gallery:
                startGallery();
                return true;
            case R.id.action_camera:
                startCamera();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            // Retrieve Result from Welcome Screen
            case REQUEST_INPUT_METHOD:
                if (resultCode == RESULT_OK) {
                    mInputMethod = data.getStringExtra("result_input_method");
                }
                else {
                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                    mInputMethod = sharedPrefs.getString("pref_input_method", getString(R.string.gallery));
                }
                // Get receipt image based on selected/default input method.
                getReceiptPicture();
                break;

            // Retrieve Image from Gallery / Camera
            case REQUEST_PICTURE_MEDIASTORE:
                if (resultCode == RESULT_OK && data != null) {
                    mPictureUri = data.getData();
                    String[] filePathColumn = { MediaStore.Images.Media.DATA };

                    Cursor cursor = getContentResolver().query(mPictureUri,
                            filePathColumn, null, null, null);
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    mPicturePath = cursor.getString(columnIndex);
                    cursor.close();

                    mReceiptPicture = BitmapFactory.decodeFile(mPicturePath);
                    mImageView.setImageBitmap(mReceiptPicture);
                }
                break;

            default:
                // Not the intended intent
                break;
        }
    }

    @Override
    public void onBackPressed() {
    // Confirm exit application on back button by requesting BACK again.
        if (mDoubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.mDoubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
            // This handle allows the flag to be reset after 2 seconds(i.e. Toast.LENGTH_SHORT's duration)
            @Override
            public void run() {
                mDoubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    // retrieves the selected or default input method
    private void getDefaultInputMethod() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean displayWelcome = sharedPrefs.getBoolean("pref_display_welcome", true);

        if (displayWelcome) {
            startWelcomeActivity();
        }
        else {
            mInputMethod = sharedPrefs.getString("pref_input_method", getString(R.string.gallery));
            // Get receipt image based on selected/default input method.
            getReceiptPicture();
        }
    }

    // retrieves the receipt image
    private void getReceiptPicture() {
        // Retrieve image
        if (mInputMethod.equalsIgnoreCase(getString(R.string.gallery))) {
            startGallery();
        }
        else if (mInputMethod.equalsIgnoreCase(getString(R.string.camera))) {
            startCamera();
        }
        else {
            Log.d("getReceiptImage", "NOT gallery or camera.");
        }
    }

    // display welcome activity and returns with result
    public void startWelcomeActivity() {
        Intent intentInputMethod = new Intent(MainActivity.this, WelcomeActivity.class);
        startActivityForResult(intentInputMethod, REQUEST_INPUT_METHOD);
    }

    // start gallery
    private void startGallery() {
        Intent intentGallery = new Intent(
                Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(intentGallery, REQUEST_PICTURE_MEDIASTORE);
    }

    // Start Camera
    private void startCamera() {
        Intent intentCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // start the image capture Intent
        startActivityForResult(intentCamera, REQUEST_PICTURE_MEDIASTORE);
    }

    // onClick of next button
    public void startLoadingAcitivty(View view){
        Receipt.receiptBitmap = setFilter(mReceiptPicture);
        Intent intent = new Intent(this, LoadingActivity.class);
        startActivity(intent);
    }

    private static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void performCrop(){
        if (mPictureUri == null)
            return;

        try {
            //call the standard crop action intent (the user device may not support it)
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            cropIntent.setDataAndType(mPictureUri, "image/*");

            startActivityForResult(cropIntent, REQUEST_PICTURE_MEDIASTORE);
        }
        catch(ActivityNotFoundException anfe){
            //display an error message
            String errorMessage = "Whoops - your device doesn't support the crop action!";
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    // adjust imageView filter
    private void applyFilter(){
        ColorFilter colorFilter = new ColorMatrixColorFilter(mColorMatrix);
        mImageView.setColorFilter(colorFilter);
    }

    // set the Image with new filter, before proceed to next activity
    private Bitmap setFilter(Bitmap bitmapToConvert){
        final ColorMatrixColorFilter colorFilter= new ColorMatrixColorFilter(mColorMatrix);
        Bitmap bitmap = bitmapToConvert.copy(Bitmap.Config.ARGB_8888, true);
        Paint paint=new Paint();
        paint.setColorFilter(colorFilter);

        Canvas myCanvas =new Canvas(bitmap);
        myCanvas.drawBitmap(bitmap, 0, 0, paint);

        return bitmap;
    }

    // Get value of range -0.5 ~ 1.5
    private float convertContrastValue(int progress) {
        float new_contrast = -0.5f + (((float) progress) / 50);
        return new_contrast;
    }

    // Get a range of -50.0 ~ 10 for RGB offset value
    private float convertBrightnessValue(int progress) {
        float new_brightness = (((float) progress ) - 50) * 3;
        if(new_brightness > 0){
            new_brightness = new_brightness / 5;
        }
        return new_brightness;
    }

    // Adjust the contrast and brightness
    private void adjustContrastBrightness(float contrast, float brightness)
    {
        mColorMatrix = new float[]{
                contrast, 0.5f, 0.5f, 0, brightness,
                0.5f, contrast, 0.5f, 0, brightness,
                0.5f, 0.5f, contrast, 0, brightness,
                0, 0, 0, 1, 0
        };
        applyFilter();
    }

    // Setting up call backs for Action Bar that will
    // overlay existing when long click on image
    // for editing of image. rotate/crop
    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_image, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            ImageView imageView = (ImageView) findViewById(R.id.imageView);

            switch (item.getItemId()) {
                case R.id.rotate_left:
                    mReceiptPicture = RotateBitmap(mReceiptPicture, 270);
                    imageView.setImageBitmap(mReceiptPicture);
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                case R.id.rotate_right:
                    mReceiptPicture = RotateBitmap(mReceiptPicture, 90);
                    imageView.setImageBitmap(mReceiptPicture);
                    mode.finish();
                    return true;
                case R.id.crop:
                    mode.finish(); // Action picked, so close the CAB
                    performCrop();
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    };
}