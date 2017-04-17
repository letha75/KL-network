package wwckl.projectmiki.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import wwckl.projectmiki.R;
import wwckl.projectmiki.models.Receipt;


public class MainActivity extends AppCompatActivity {
    final int REQUEST_INPUT_METHOD = 1;  // for checking of requestCode onActivityResult
    final int REQUEST_PICTURE_MEDIASTORE = 2;
    final int RESUME_FROM_OTHER = 0;
    final int RESUME_FROM_CAMERA = 1;
    final int RESUME_FROM_GALLERY = 2;
    final int RESUME_FROM_CROP = 3;
    final long fFileSizeToScale = 1000000; // 1 MB in bytes

    private int mResumeFrom = 0; // what instruction to display if any.
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
    private TextView mSelectTextView;
    private TextView mAdjustContrastTextView;
    private TextView mAdjustThresholdTextView;
    private SeekBar mColorThresholdBar;
    private SeekBar mContrastBar;
    private Button mNextButton;
    private SharedPreferences mSharedPrefs;
    int mLtGray, mDkGray, mLightishGray, mDarkishGray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get preference manager
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // get colors from resource
        mLtGray = getResources().getColor(R.color.light_gray);
        mDkGray = getResources().getColor(R.color.dark_gray);
        mLightishGray = getResources().getColor(R.color.lightish_gray);
        mDarkishGray = getResources().getColor(R.color.darkish_gray);

        // Get layout objects for manipulation later.
        mSelectTextView = (TextView)findViewById(R.id.tvSelect);
        mAdjustContrastTextView = (TextView)findViewById(R.id.tvAdjustContrast);
        mAdjustThresholdTextView = (TextView)findViewById(R.id.tvAdjustThreshold);
        mNextButton = (Button)findViewById(R.id.button_next);

        // Setup Listener for Contrast Seek Bar
        mContrastBar = (SeekBar)findViewById(R.id.contrastBar);
        mContrastBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                adjustContrast(convertContrastValue(progress));
                // allow user to start OCR
                mNextButton.setEnabled(true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Setup Color threshold bar setup Listener
        mColorThresholdBar = (SeekBar)findViewById(R.id.colorThresholdBar);
        mColorThresholdBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                adjustThreshold(progress);
                // allow user to start OCR
                mNextButton.setEnabled(true);
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
        mImageView.setOnLongClickListener(new View.OnLongClickListener() {
            // Called when the user long-clicks on someView
            public boolean onLongClick(View view) {
                if (mActionMode != null) {
                    return false;
                }
                // Start the CAB using the ActionMode.Callback defined above
                mActionMode = MainActivity.this.startActionMode(mActionModeCallback);
                view.setSelected(true);
                return true;
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
            mSelectTextView.setVisibility(View.VISIBLE);
            mAdjustContrastTextView.setVisibility(View.INVISIBLE);
            mAdjustThresholdTextView.setVisibility(View.INVISIBLE);
            mContrastBar.setVisibility(View.INVISIBLE);
            mColorThresholdBar.setVisibility(View.INVISIBLE);

            mNextButton.setEnabled(false);
        }
        else{ // image will be displayed, change text.
            mSelectTextView.setVisibility(View.GONE);
            mAdjustContrastTextView.setVisibility(View.VISIBLE);
            mAdjustThresholdTextView.setVisibility(View.VISIBLE);
            mContrastBar.setVisibility(View.VISIBLE);
            mColorThresholdBar.setVisibility(View.VISIBLE);

            applyFilter();
            adjustThreshold(mColorThresholdBar.getProgress());

            switch (mResumeFrom) {
                case RESUME_FROM_CAMERA:
                case RESUME_FROM_GALLERY:
                    displayToast(getString(R.string.crop_picture_instructions), true);
                    performCrop();
                    break;
                case RESUME_FROM_CROP:
                    displayToast(getString(R.string.adjust_picture_instructions), true);
                    break;
                default:
                    break;
            }
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
            case R.id.action_help:
                Intent myWebLink = new Intent(android.content.Intent.ACTION_VIEW);
                myWebLink.setData(Uri.parse("https://github.com/WomenWhoCode/KL-network/wiki/Project-Miki-Help-File"));
                startActivity(myWebLink);
                return true;
            case R.id.action_gallery:
                startGallery();
                return true;
            case R.id.action_camera:
                startCamera();
                return true;
            case R.id.action_edit:
                startEdit();
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
                    mInputMethod = mSharedPrefs.getString("pref_input_method", getString(R.string.gallery));
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
                            null, null, null, null);
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    mPicturePath = cursor.getString(columnIndex);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    long fileSize = cursor.getLong(sizeIndex);
                    Log.d("image fileSize", Long.toString(fileSize));
                    cursor.close();

                    // We do not require high resolution images as it may cause OutOfMemoryError
                    BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
                    // Scale output picture if file size is too large.
                    if (fileSize > fFileSizeToScale)
                        bmpOptions.inSampleSize = 2;
                    mReceiptPicture = BitmapFactory.decodeFile(mPicturePath, bmpOptions);

                    // Check picture orientation
                    // Rotate image if needed.
                    try {
                        ExifInterface ei = new ExifInterface(mPicturePath);
                        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL);

                        switch (orientation) {
                            case ExifInterface.ORIENTATION_ROTATE_90:
                                mReceiptPicture = RotateBitmap(mReceiptPicture, 90);
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_180:
                                mReceiptPicture = RotateBitmap(mReceiptPicture, 180);
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_270:
                                mReceiptPicture = RotateBitmap(mReceiptPicture, 270);
                                break;
                            default:
                                break;
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }

                    // Display picture on screen
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
        displayToast(getString(R.string.click_back_again_to_exit), false);

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
        boolean displayWelcome = mSharedPrefs.getBoolean(getString(R.string.pref_display_welcome), true);

        if (displayWelcome) {
            startWelcomeActivity();
        }
        else {
            mInputMethod = mSharedPrefs.getString(getString(R.string.pref_input_method), getString(R.string.gallery));
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
        else if (mInputMethod.equalsIgnoreCase(getString(R.string.edit))) {
            startEdit();
        }
        else {
            Log.d("getReceiptImage", "NOT gallery/camera/manual.");
        }
    }

    // display welcome activity and returns with result
    public void startWelcomeActivity() {
        Intent intentInputMethod = new Intent(MainActivity.this, WelcomeActivity.class);
        startActivityForResult(intentInputMethod, REQUEST_INPUT_METHOD);
    }

    // start gallery
    private void startGallery() {
        mResumeFrom = RESUME_FROM_GALLERY;
        Intent intentGallery = new Intent(
                Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(intentGallery, REQUEST_PICTURE_MEDIASTORE);
    }

    // Start Camera
    private void startCamera() {
        mResumeFrom = RESUME_FROM_CAMERA;
        Intent intentCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // start the image capture Intent
        startActivityForResult(intentCamera, REQUEST_PICTURE_MEDIASTORE);
    }

    // onClick of next button
    public void startLoadingAcitivty(View view) {
        // clear instructions flag
        mResumeFrom = RESUME_FROM_OTHER;

        if (mColorThresholdBar.getProgress() >0)
            mReceiptPicture = changeColor(mReceiptPicture, mColorThresholdBar.getProgress());

        Receipt.setReceiptBitmap(setFilter(mReceiptPicture));
        Intent intent = new Intent(this, LoadingActivity.class);
        startActivity(intent);
    }

    // Start Edit Fragment on BillSplitterActivity
    private void startEdit() {
        // clear instructions flag
        mResumeFrom = RESUME_FROM_OTHER;

        // Make sure recognized text is empty
        Receipt.setRecognizedText("");
        Intent intentEdit = new Intent(this, EditActivity.class);
        startActivity(intentEdit);
    }

    /*----------- FUNCTIONS FOR IMAGE MANIPULATION: -------------*/

    private Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void performCrop(){
        if (mPictureUri == null)
            return;

        try {
            // set instructions for after cropping
            mResumeFrom = RESUME_FROM_CROP;

            //call the standard crop action intent (the user device may not support it)
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            cropIntent.setDataAndType(mPictureUri, "image/*");

            startActivityForResult(cropIntent, REQUEST_PICTURE_MEDIASTORE);
        }
        catch(ActivityNotFoundException anfe){
            //display an error message
            displayToast(getString(R.string.crop_error_message), false);
        }
    }

    // adjust imageView filter, do not set image with filter yet
    private void applyFilter(){
        ColorFilter colorFilter = new ColorMatrixColorFilter(mColorMatrix);
        mImageView.setColorFilter(colorFilter);
    }

    // set the Image with new filter, before proceed to next activity
    private Bitmap setFilter(Bitmap bitmapToConvert){
        ColorMatrixColorFilter colorFilter= new ColorMatrixColorFilter(mColorMatrix);
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

    // Adjust the contrast
    private void adjustContrast(float contrast)
    {
        mColorMatrix = new float[]{
                contrast, 0.5f, 0.5f, 0, 0,
                0.5f, contrast, 0.5f, 0, 0,
                0.5f, 0.5f, contrast, 0, 0,
                0, 0, 0, 1, 0
        };
        applyFilter();
    }

    // Get the threshold value to change image colors
    private void adjustThreshold(int progress){
        if((progress == 0) || (progress == mColorThresholdBar.getMax())) {
            mImageView.setImageBitmap(mReceiptPicture);
            return;
        }

        mImageView.setImageBitmap(changeColor(mReceiptPicture, progress));
    }

    // Change bitmap image colours to 4 shades: black, dark gray, light gray or white
    // We want contrasting shades, so no mid-shades such as gray.
    private Bitmap changeColor(Bitmap src, int progress) {
        final int absBlack = Math.abs(Color.BLACK);
        final int white = Color.WHITE;
        int gray;
        int absLtGray = Math.abs(Color.LTGRAY);
        int absDkGray = Math.abs(Color.DKGRAY);
        int absGray = Math.abs(Color.GRAY);
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = new int[width * height];
        int maxProgress = mColorThresholdBar.getMax();
        int factor = absBlack / maxProgress;
        int threshold;

        if ((progress == 0) || (progress == maxProgress))
            return src;

        //threshold to change to white or black
        threshold = factor * progress;
        if (progress < (maxProgress/2)){
            absLtGray = threshold / 2;
            absDkGray = threshold + ( (absBlack - threshold) / 3 * 2 );
            absGray = threshold + ( (absDkGray - threshold) / 2 );
            // set to darker gray.
            gray = mDarkishGray;
        }
        else {
            absDkGray = threshold + ((absBlack - threshold) / 2);
            absLtGray = threshold / 3;
            absGray = threshold;
            threshold = absLtGray * 2;
            gray = mLightishGray;
        }
        // by end of calculations: white < ltGray < threshold < gray < dkGray < black

        /* DEBUGGING
        Log.d("color", "threshold " + Integer.toString(threshold));
        Log.d("color", "absLtGray " + Integer.toString(absLtGray));
        Log.d("color", "absGray   " + Integer.toString(absGray));
        Log.d("color", "absDkGray " + Integer.toString(absDkGray));
        */

        // get pixel array from source
        src.getPixels(pixels, 0, width, 0, 0, width, height);
        Bitmap bmOut = Bitmap.createBitmap(width, height, src.getConfig());

        int pixel;
        // iteration through pixels
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                // get current index in 2D-matrix
                int index = y * width + x;
                pixel = Math.abs(pixels[index]);
                if(pixel < absLtGray){
                    pixels[index] = white;
                }
                else if(pixel < threshold){
                    pixels[index] = mLtGray;
                }
                else if(pixel < absGray){
                    pixels[index] = gray;
                }
                else if(pixel < absDkGray){
                    pixels[index] = mDkGray;
                }
                else{
                    pixels[index] = Color.BLACK;
                }
            }
        }
        bmOut.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmOut;
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

            switch (item.getItemId()) {
                case R.id.rotate_left:
                    mReceiptPicture = RotateBitmap(mReceiptPicture, 270);
                    adjustThreshold(mColorThresholdBar.getProgress());
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                case R.id.rotate_right:
                    mReceiptPicture = RotateBitmap(mReceiptPicture, 90);
                    adjustThreshold(mColorThresholdBar.getProgress());
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

    private void displayToast(String toastString, Boolean isHelper) {
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