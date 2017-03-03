package com.qwildz.carcamera;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.LinearLayoutCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.qwildz.blunolibrary.BlunoLibrary;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import jaron.simpleserialization.SerializationSerialConnection;
import timber.log.Timber;

public class CameraActivity extends RxAppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2, BlunoLibrary.BlunoListener {

    static {
        System.loadLibrary("native-lib");
    }

    private final int REQUEST_ENABLE_BT = 1;

    private volatile double contourArea = 7;
    private Mat rgbaImage;
    private int iLowH, iLowS, iLowV, iHighH, iHighS, iHighV;
    private boolean isHsv = false;

    private volatile Point centerPoint = new Point(-1, -1);
    private Point screenCenterCoordinates = new Point(-1, -1);

    private CameraBridgeViewBase mOpenCvCameraView;

    private double throttle, steer = 0;
//    private String sdirection, sgas;
    private int yaw = 90;

    private BlunoLibrary blunoLibrary;
    private BlunoLibrary.ConnectionStateEnum blunoState = BlunoLibrary.ConnectionStateEnum.isNull;
    private SerializationSerialConnection simpleSerialization = new SerializationSerialConnection();
    private CommandData commandData = new CommandData();
    private CommandData lastCommand = new CommandData();

    public enum STEER_MODE {
        PWM_MODE(0), OFF_STEER(1), DEC_INC(2);

        private final int value;

        STEER_MODE(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private static STEER_MODE steerMode = STEER_MODE.PWM_MODE;
    int temp_mode = steerMode.getValue();

    GestureDetectorCompat gestureDetector;

    @BindView(R.id.connect_btn)
    Button btnConnect;

    @BindView(R.id.status_tv)
    TextView tvConnectionStatus;

    @BindView(R.id.panel_layout)
    LinearLayoutCompat llPanel;

    @BindView(R.id.low_h_sb)
    AppCompatSeekBar sbLowH;

    @BindView(R.id.low_s_sb)
    AppCompatSeekBar sbLowS;

    @BindView(R.id.low_v_sb)
    AppCompatSeekBar sbLowV;

    @BindView(R.id.high_h_sb)
    AppCompatSeekBar sbHighH;

    @BindView(R.id.high_s_sb)
    AppCompatSeekBar sbHighS;

    @BindView(R.id.high_v_sb)
    AppCompatSeekBar sbHighV;

    @BindView(R.id.low_h_tv)
    TextView tvLowH;

    @BindView(R.id.low_s_tv)
    TextView tvLowS;

    @BindView(R.id.low_v_tv)
    TextView tvLowV;

    @BindView(R.id.high_h_tv)
    TextView tvHighH;

    @BindView(R.id.high_s_tv)
    TextView tvHighS;

    @BindView(R.id.high_v_tv)
    TextView tvHighV;

    @OnClick(R.id.connect_btn)
    void toggleConnection() {
        if(blunoState == BlunoLibrary.ConnectionStateEnum.isToScan) {
             blunoLibrary.connect("C8:A0:30:F8:A8:C2");
        } else if(blunoState == BlunoLibrary.ConnectionStateEnum.isConnecting) {

        } else if(blunoState == BlunoLibrary.ConnectionStateEnum.isConnected) {
            blunoLibrary.disconnect();
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Timber.d("OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
//                    _hsvMat = new Mat();
//                    _processedMat = new Mat();
//                    _dilatedMat = new Mat();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekbarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (seekBar == sbLowH) {
                iLowH = progress;
                tvLowH.setText("" + progress);
            } else if (seekBar == sbLowS) {
                iLowS = progress;
                tvLowS.setText("" + progress);
            } else if (seekBar == sbLowV) {
                iLowV = progress;
                tvLowV.setText("" + progress);
            } else if (seekBar == sbHighH) {
                iHighH = progress;
                tvHighH.setText("" + progress);
            } else if (seekBar == sbHighS) {
                iHighS = progress;
                tvHighS.setText("" + progress);
            } else if (seekBar == sbHighV) {
                iHighV = progress;
                tvHighV.setText("" + progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    class LoopSend extends Thread {
        public boolean running = true;

        static final int MIN_CONTOUR_AREA = 100;
//        static final int MAX_NEUTRAL_CONTOUR_AREA = 1700;
        static final int MAX_NEUTRAL_CONTOUR_AREA = 12000;
//        static final int MIN_NEUTRAL_CONTOUR_AREA = 800;
        static final int MIN_NEUTRAL_CONTOUR_AREA = 5000;

        static final int MOTOR_FORWARD_PWM = 60; // 15
        static final int MOTOR_REVERSE_PWM = -40; // 40
        static final int MOTOR_NEUTRAL_PWM = 0; // 0

        int _countOutOfFrame = 0;
        boolean _wasMoving = false;
        int _pulseCounter = 0;

        MiniPID pidPan;
        MiniPID pidSteer;
        MiniPID pidThrottle;

        LoopSend() {
            pidPan = new MiniPID(0.08, 0.05, 0.05);
            pidPan.setOutputLimits(50);

            pidSteer = new MiniPID(0.08, 0.05, 0.05);
            pidSteer.setOutputLimits(100);
            pidSteer.setDirection(true);

            pidThrottle = new MiniPID(0.0005, 0.005, 0.005);
            pidThrottle.setOutputLimits(50);
        }

        @Override
        public void run() {
            while (running) {
                try {
                    doAction();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Timber.e(e);
                }
            }
        }

        void doAction() {
            synchronized (this) {
                if (contourArea > MIN_CONTOUR_AREA) {
//                    updatePanTiltPWM(screenCenterCoordinates, centerPoint);
//                            _mainController._irSensors.updateIRSensorsVoltage(_sideLeftIR.getVoltage(), _sideRightIR.getVoltage(), _frontRightIR.getVoltage(), _frontLeftIR.getVoltage());
//                            _mainController.updateMotorPWM(contourArea);

                    double throttleSetpoint = 0;
//                    if (contourArea > MIN_NEUTRAL_CONTOUR_AREA && contourArea < MAX_NEUTRAL_CONTOUR_AREA) {
//                        throttle = (_wasMoving) ? MOTOR_REVERSE_PWM : MOTOR_NEUTRAL_PWM;
//                        _wasMoving = false;
//                        _pulseCounter = 2;
//                    } else if (contourArea < MIN_NEUTRAL_CONTOUR_AREA) {
//                        throttle = MOTOR_FORWARD_PWM;
//                        _wasMoving = true;
//                        _pulseCounter = 2;
//                    } else if (contourArea > MAX_NEUTRAL_CONTOUR_AREA) {
//                        throttle = reverseSequence(_pulseCounter);
//                        if (_pulseCounter > 0)
//                            _pulseCounter--;
//                        _wasMoving = false;
//                    }

                    if(contourArea > MIN_NEUTRAL_CONTOUR_AREA && contourArea < MAX_NEUTRAL_CONTOUR_AREA) {
                        throttleSetpoint = contourArea;
                        throttle = 0;
                        pidThrottle.reset();
                    } else if(contourArea < MIN_NEUTRAL_CONTOUR_AREA) {
                        throttleSetpoint = MIN_NEUTRAL_CONTOUR_AREA;
                        pidThrottle.setOutputLimits(100);
                        throttle = pidThrottle.getOutput(contourArea, throttleSetpoint);
                    } else if(contourArea > MAX_NEUTRAL_CONTOUR_AREA) {
                        throttleSetpoint = MIN_NEUTRAL_CONTOUR_AREA;
                        pidThrottle.setOutputLimits(70);
                        throttle = pidThrottle.getOutput(contourArea, throttleSetpoint);
                    }

                   // steer = 0;

                    Timber.d(contourArea + " --- " + throttleSetpoint);

                    double yawOutput = pidPan.getOutput(screenCenterCoordinates.x, centerPoint.x);
                    yaw = (int) (90 + yawOutput);
                    steer = (int) (pidSteer.getOutput(yaw, 90));


                    _countOutOfFrame = 0;
                } else {
                    if (_countOutOfFrame > 5) {
                        steer = 0;
                        throttle = 0;
                        yaw = 90;

                        pidPan.reset();
                        pidThrottle.reset();

                        _countOutOfFrame = 0;
                    }
                    _countOutOfFrame++;
                }

                //throttle = 0;
                sendCommand(calculatePower());
            }
        }

        private int reverseSequence(int pulseCounter) {
            return (pulseCounter == 2) ? MOTOR_REVERSE_PWM - 18 : (pulseCounter == 1) ? MOTOR_NEUTRAL_PWM + 1 : MOTOR_REVERSE_PWM;
        }

//        static final int MIN_PAN_PWM = 50;
//        static final int MAX_PAN_PWM = 140;
//
//
//        static final int MID_PAN_PWM = 90;
//
//        static final int RANGE_PAN_PWM = MAX_PAN_PWM - MID_PAN_PWM;
//
//        double _pwmPan;
//        double _lastPanPWM;
//        double _pwmTilt;
//
//        Point increment = new Point(0, 0);
//        double target_tilt_position = 0.0;
//        static final double kD_X = 0.8;// 003901;//018; // Derivative gain (Kd)
//        static final int MID_SCREEN_BOUNDARY = 15;
//        Point _lastCenterPoint = new Point(0, 0);
//
//        private void updatePanTiltPWM(Point screenCenterPoint, Point currentCenterPoint) {
//
//
//            Point derivativeTerm = new Point(0, 0);
//
//            // --- Set up objects to calculate the error and derivative error
//            Point error = new Point(0, 0); // The position error
//            Point setpoint = new Point(0, 90);
//
//            setpoint.x = (screenCenterPoint.x - currentCenterPoint.x) * 1.35;
//            if ((setpoint.x < -MID_SCREEN_BOUNDARY || setpoint.x > MID_SCREEN_BOUNDARY) && currentCenterPoint.x > 0) {
//                if (_lastCenterPoint.x != currentCenterPoint.x) {
//                    increment.x = setpoint.x * 0.18;
//                    _lastPanPWM = _pwmPan;
//                }
//                error.x = (_pwmPan - increment.x);
//
//                derivativeTerm.x = (_pwmPan - _lastPanPWM);
//
//                _lastPanPWM = _pwmPan;
//
//                _pwmPan = error.x - constrain(kD_X * derivativeTerm.x, -9, 9);
//
//                _pwmPan = constrain(_pwmPan, MIN_PAN_PWM, MAX_PAN_PWM);
//
//                // if (_pwmPan >= MAX_PAN_PWM) {
//                // reverse = true;
//                // _pwmPan = MID_PAN_PWM;
//                // }
//
//                _lastCenterPoint.x = currentCenterPoint.x;
//            }
//        }
//
//        public double constrain(double input, double min, double max) {
//            return (input < min) ? min : (input > max) ? max : input;
//        }
    }

    LoopSend loop = new LoopSend();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        sbLowH.setOnSeekBarChangeListener(mSeekbarChangeListener);
        sbLowS.setOnSeekBarChangeListener(mSeekbarChangeListener);
        sbLowV.setOnSeekBarChangeListener(mSeekbarChangeListener);
        sbHighH.setOnSeekBarChangeListener(mSeekbarChangeListener);
        sbHighS.setOnSeekBarChangeListener(mSeekbarChangeListener);
        sbHighV.setOnSeekBarChangeListener(mSeekbarChangeListener);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera);
        mOpenCvCameraView.setMaxFrameSize(352, 288);
        mOpenCvCameraView.setCvCameraViewListener(this);

        iLowH = 0;
        iLowS = 125;
        iLowV = 150;
        iHighH = 60;
        iHighS = 255;
        iHighV = 255;

        sbLowH.setProgress(iLowH);
        sbLowS.setProgress(iLowS);
        sbLowV.setProgress(iLowV);
        sbHighH.setProgress(iHighH);
        sbHighS.setProgress(iHighS);
        sbHighV.setProgress(iHighV);

        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public void onLongPress(MotionEvent e) {
                if (llPanel.getVisibility() == View.GONE) {
                    llPanel.setVisibility(View.VISIBLE);
                    isHsv = true;
                } else {
                    llPanel.setVisibility(View.GONE);
                    isHsv = false;
                }
            }
        });

//        if (_trackingColor == 0) {
//            _lowerThreshold = new Scalar(60, 100, 30); // Green
//            _upperThreshold = new Scalar(130, 255, 255);
//        } else if (_trackingColor == 1) {
//            _lowerThreshold = new Scalar(160, 50, 90); // Purple
//            _upperThreshold = new Scalar(255, 255, 255);
//        } else if (_trackingColor == 2) {
//            _lowerThreshold = new Scalar(1, 50, 150); // Orange
//            _upperThreshold = new Scalar(60, 255, 255);
//        }

        blunoLibrary = CarCameraApplication.getBluno(this);
        prepareBluno();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Timber.d("Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Timber.d("OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        if (!blunoLibrary.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        blunoLibrary.resume();
    }

    public void onDestroy() {
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        blunoLibrary.destroy();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
            } else {
                prepareBluno();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    //    // Initiating Menu XML file (menu.xml)
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        MenuInflater menuInflater = getMenuInflater();
//        menuInflater.inflate(R.menu.steer_mode_menu, menu);
//        return true;
//    }
//
//    /**
//     * Event Handling for Individual menu item selected
//     * Identify single menu item by it's id
//     */
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//
//        switch (item.getItemId()) {
//            case R.id.steer_mode:
//                temp_mode = steerMode.getValue();
//
//                AlertDialog.Builder adb = new AlertDialog.Builder(this);
//                CharSequence items[] = new CharSequence[]{"PWM Mode", "Off Steer", "Decrease & Increase Speed"};
//                adb.setSingleChoiceItems(items, temp_mode, (dialog, which) -> temp_mode = which);
//                adb.setPositiveButton("OK", (dialog, which) -> steerMode = STEER_MODE.values()[temp_mode]);
//                adb.setNegativeButton("Cancel", null);
//                adb.setTitle("Steer Mode");
//                adb.show();
//
//                return true;
//
//            default:
//                return super.onOptionsItemSelected(item);
//        }
//    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        rgbaImage = new Mat(height, width, CvType.CV_8UC4);
        screenCenterCoordinates.x = rgbaImage.size().width / 2;
        screenCenterCoordinates.y = rgbaImage.size().height / 2;
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        synchronized (inputFrame) {
            rgbaImage = inputFrame.rgba();

            double[] data = detectTennisBall2(rgbaImage.getNativeObjAddr(), iLowH, iLowS, iLowV, iHighH, iHighS, iHighV, isHsv);

            contourArea = data[0];
            centerPoint.x = data[1];
            centerPoint.y = data[2];

            Timber.d("Contour: " + contourArea + " X: " + centerPoint.x + " Y: " + centerPoint.y);
            if(loop != null)
                loop.doAction();
        }
        return rgbaImage;
    }

    private void prepareBluno() {
        blunoLibrary.setBlunoListener(this);
        blunoLibrary.initialize();
    }

    @Override
    public void onDeviceDetected(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Timber.d("Device: " + device.getAddress());
    }

    @Override
    public void onConectionStateChange(BlunoLibrary.ConnectionStateEnum state) {
        Timber.d("State: " + state.name());
        blunoState = state;

        if(state == BlunoLibrary.ConnectionStateEnum.isConnecting) {
            btnConnect.setEnabled(false);
        } else {
            btnConnect.setEnabled(true);
        }

        switch (state) {
            case isNull:
                tvConnectionStatus.setText("Initializing...");
                break;
            case isScanning:
                tvConnectionStatus.setText("Scanning...");
                break;
            case isToScan:
                tvConnectionStatus.setText("Disconnected");
                btnConnect.setText("Connect");

//                if (loop != null) {
//                    loop.running = false;
//                    loop = null;
//                }
                break;
            case isConnecting:
                tvConnectionStatus.setText("Connecting...");
                break;
            case isConnected:
                tvConnectionStatus.setText("Connected");
                btnConnect.setText("Disconnect");

//                loop = new LoopSend();
//                loop.start();
                break;
            case isDisconnecting:
                tvConnectionStatus.setText("Disconnected");
                btnConnect.setText("Connect");
                break;
        }
    }

    @Override
    public void onSerialReceived(String data) {
        Timber.d("Data received: " + data);
    }

    private void sendCommand(CommandData command) {
//        if(command.equals(lastCommand)) return;

        lastCommand.assign(command);

        byte[] data = simpleSerialization.write(command);

//        steerStatus.setText("Power:" + steer + " Dir: " + sdirection);
//        throttleStatus.setText("Power:" + throttle + " Dir: " + sgas);

        Timber.d(
                commandData.leftMotor + " " + commandData.leftDirection + " " +
                        commandData.rightMotor + " " + commandData.rightDirection
                        + " | L" + commandData.leftDirection + " " + commandData.leftMotor + " R" + commandData.rightDirection + " " + commandData.rightMotor
                        + " | Y" + commandData.yawServo);


        blunoLibrary.serialSend(data);
    }

    CommandData calculatePower() {
        int totalPower = Math.min((int) (255 * (Math.abs(throttle) / 100)), 255);

        //Timber.simpleLog(TAG, "totalPower: " + totalPower);

        if (Math.abs(steer) > 0) {
            int power = (int) (0.8 * totalPower);
            int th = (int) ((Math.abs(steer) / 100) * power);
            if (steer < 0) {
                if (steerMode == STEER_MODE.OFF_STEER) {
                    if (steer < -20) {
                        commandData.leftMotor = 0;
                    }
                } else {
                    commandData.leftMotor = power - th;
                }

                if (commandData.leftMotor < 0) {
                    commandData.leftMotor = 0;
                }

                if (steerMode == STEER_MODE.DEC_INC) {
                    commandData.rightMotor = power + th;
                } else {
                    commandData.rightMotor = power;
                }
            } else if (steer > 0) {
                if (steerMode == STEER_MODE.OFF_STEER) {
                    if (steer > 20) {
                        commandData.rightMotor = 0;
                    }
                } else {
                    commandData.rightMotor = power - th;
                }

                if (commandData.rightMotor < 0) {
                    commandData.rightMotor = 0;
                }

                if (steerMode == STEER_MODE.DEC_INC) {
                    commandData.leftMotor = power + th;
                } else {
                    commandData.leftMotor = power;
                }
            }
        } else {
            commandData.leftMotor = totalPower;
            commandData.rightMotor = totalPower;
        }

        // Reverse
        if (throttle < 0) {
            commandData.leftDirection = false;
            commandData.rightDirection = false;

            if (Math.abs(steer) > 0) {
                int temp = commandData.rightMotor;
                commandData.rightMotor = commandData.leftMotor;
                commandData.leftMotor = temp;
            }
        } else {
            commandData.leftDirection = true;
            commandData.rightDirection = true;
        }

        commandData.yawServo = yaw;

        return commandData;
    }

    public native void detectRed(long matAddrGray, int iLowH, int iLowS, int iLowV, int iHighH, int iHighS, int iHighV);

    public native double detectTennisBall(long matAddrGray);

    public native double[] detectTennisBall2(long matAddrGray, int iLowH, int iLowS, int iLowV, int iHighH, int iHighS, int iHighV, boolean showHsv);
}
