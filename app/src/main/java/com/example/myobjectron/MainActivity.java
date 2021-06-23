package com.example.myobjectron;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;


import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

//import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
//import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
//import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.glutil.EglManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import java.util.logging.SocketHandler;

/**
 * Main activity of MediaPipe example apps.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String BINARY_GRAPH_NAME = "mobile_gpu_binary_graph.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";

    private static final String OBJ_TEXTURE = "texture.jpg";
    private static final String OBJ_FILE = "model.obj.uuu";
    private static final String BOX_TEXTURE = "classic_colors.png";
    private static final String BOX_FILE = "box.obj.uuu";

    private Bitmap objTexture = null;
    private Bitmap boxTexture = null;

    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.BACK;
    // Flips the camera-preview frames vertically before sending them into FrameProcessor to be
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());

        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        //socketHandlerThread = new SocketHandlerThread();
        //socketHandlerThread.start();

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);


        String categoryName = applicationInfo.metaData.getString("categoryName");
        int maxNumObjects = applicationInfo.metaData.getInt("maxNumObjects");
        float[] modelScale = parseFloatArrayFromString(
                applicationInfo.metaData.getString("modelScale"));
        float[] modelTransform = parseFloatArrayFromString(
                applicationInfo.metaData.getString("modelTransformation"));
        prepareDemoAssets();
        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        Map<String, Packet> inputSidePackets = new HashMap<>();
        inputSidePackets.put("obj_asset_name", packetCreator.createString(OBJ_FILE));
        inputSidePackets.put("box_asset_name", packetCreator.createString(BOX_FILE));
        inputSidePackets.put("obj_texture", packetCreator.createRgbaImageFrame(objTexture));
        inputSidePackets.put("box_texture", packetCreator.createRgbaImageFrame(boxTexture));
        inputSidePackets.put("allowed_labels", packetCreator.createString(categoryName));
        inputSidePackets.put("max_num_objects", packetCreator.createInt32(maxNumObjects));
        inputSidePackets.put("model_scale", packetCreator.createFloat32Array(modelScale));
        inputSidePackets.put("model_transformation", packetCreator.createFloat32Array(modelTransform));
        processor.setInputSidePackets(inputSidePackets);

        PermissionHelper.checkAndRequestCameraPermissions(this);


    }

    // Used to obtain the content view for this application. If you are extending this class, and
    // have a custom layout, override this method and return the custom layout.
    protected int getContentViewLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onResume() {
        super.onResume();

        converter =
                new ExternalTextureConverter(
                        eglManager.getContext()
                );
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE); // **////

    }
    @Override
    public void onDestroy(){
        //socketHandlerThread.quit();
        super.onDestroy();
    }


    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
//        super.onPreviewDisplaySurfaceChanged(holder, format, width, height);


        boolean isCameraRotated = cameraHelper.isCameraRotated();
        Size cameraImageSize = cameraHelper.getFrameSize();
        processor.setOnWillAddFrameListener(
                (timestamp) -> {
                    try {
                        int cameraTextureWidth =
                                isCameraRotated ? cameraImageSize.getHeight() : cameraImageSize.getWidth();
                        int cameraTextureHeight =
                                isCameraRotated ? cameraImageSize.getWidth() : cameraImageSize.getHeight();

                        // Find limiting side and scale to 3:4 aspect ratio
                        float aspectRatio = (float) cameraTextureWidth / (float) cameraTextureHeight;
                        if (aspectRatio > 3.0 / 4.0) {
                            // width too big
                            cameraTextureWidth = (int) ((float) cameraTextureHeight * 3.0 / 4.0);
                        } else {
                            // height too big
                            cameraTextureHeight = (int) ((float) cameraTextureWidth * 4.0 / 3.0);
                        }
                        Packet widthPacket = processor.getPacketCreator().createInt32(cameraTextureWidth);
                        Packet heightPacket = processor.getPacketCreator().createInt32(cameraTextureHeight);

                        try {
                            processor.getGraph().addPacketToInputStream("input_width", widthPacket, timestamp);
                            processor.getGraph().addPacketToInputStream("input_height", heightPacket, timestamp);
                        } catch (RuntimeException e) {
                            Log.e(
                                    TAG,
                                    "MediaPipeException encountered adding packets to input_width and input_height"
                                            + " input streams.", e);
                        }
                        widthPacket.release();
                        heightPacket.release();
                    } catch (IllegalStateException ise) {
                        Log.e(TAG, "Exception while adding packets to width and height input streams.");
                    }
                });


        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        //boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.

        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());

    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        //return null;
        return new Size(1280, 960); // Prefer 4:3 aspect ratio (camera size is in landscape).
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
//        cameraHelper.startCamera(
//                this, CAMERA_FACING, /*unusedSurfaceTexture=*/ null, cameraTargetResolution());
        cameraHelper.startCamera(
                this, CAMERA_FACING, null
        );
    }



    protected Size computeViewSize(int width, int height) {
        return new Size(width, height*3/4);
    }



    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);


                                boolean isCameraRotated = cameraHelper.isCameraRotated();
                                Size cameraImageSize = cameraHelper.getFrameSize();
                                processor.setOnWillAddFrameListener(
                                        (timestamp) -> {
                                            try {
                                                int cameraTextureWidth =
                                                        isCameraRotated ? cameraImageSize.getHeight() : cameraImageSize.getWidth();
                                                int cameraTextureHeight =
                                                        isCameraRotated ? cameraImageSize.getWidth() : cameraImageSize.getHeight();

                                                // Find limiting side and scale to 3:4 aspect ratio
                                                float aspectRatio = (float) cameraTextureWidth / (float) cameraTextureHeight;
                                                if (aspectRatio > 3.0 / 4.0) {
                                                    // width too big
                                                    cameraTextureWidth = (int) ((float) cameraTextureHeight * 3.0 / 4.0);
                                                } else {
                                                    // height too big
                                                    cameraTextureHeight = (int) ((float) cameraTextureWidth * 4.0 / 3.0);
                                                }
                                                Packet widthPacket = processor.getPacketCreator().createInt32(cameraTextureWidth);
                                                Packet heightPacket = processor.getPacketCreator().createInt32(cameraTextureHeight);

                                                try {
                                                    processor.getGraph().addPacketToInputStream("input_width", widthPacket, timestamp);
                                                    processor.getGraph().addPacketToInputStream("input_height", heightPacket, timestamp);
                                                } catch (RuntimeException e) {
                                                    Log.e(
                                                            TAG,
                                                            "MediaPipeException encountered adding packets to width and height"
                                                                    + " input streams.");
                                                }
                                                widthPacket.release();
                                                heightPacket.release();
                                            } catch (IllegalStateException ise) {
                                                Log.e(TAG, "Exception while adding packets to width and height input streams.");
                                            }
                                        });
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    private void prepareDemoAssets() {
        AndroidAssetUtil.initializeNativeAssetManager(this);
        // We render from raw data with openGL, so disable decoding preprocessing
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inScaled = false;
        decodeOptions.inDither = false;
        decodeOptions.inPremultiplied = false;

        try {
            InputStream inputStream = getAssets().open(OBJ_TEXTURE);
            objTexture = BitmapFactory.decodeStream(inputStream, null /*outPadding*/, decodeOptions);
            inputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing object texture; error: " + e);
            throw new IllegalStateException(e);
        }

        try {
            InputStream inputStream = getAssets().open(BOX_TEXTURE);
            boxTexture = BitmapFactory.decodeStream(inputStream, null /*outPadding*/, decodeOptions);
            inputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing box texture; error: " + e);
            throw new RuntimeException(e);
        }
    }

    private static float[] parseFloatArrayFromString(String string) {
        String[] elements = string.split(",", -1);
        float[] array = new float[elements.length];
        for (int i = 0; i < elements.length; ++i) {
            array[i] = Float.parseFloat(elements[i]);
        }
        return array;
    }

}