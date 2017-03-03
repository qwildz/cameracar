package com.qwildz.carcamera;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.jmedeisis.bugstick.Joystick;
import com.jmedeisis.bugstick.JoystickListener;
import com.qwildz.blunolibrary.BlunoLibrary;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import jaron.simpleserialization.SerializationSerialConnection;
import rx.Observable;
import rx.Subscriber;
import rx.android.MainThreadSubscription;
import rx.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

import static rx.android.MainThreadSubscription.verifyMainThread;

public class ManualControlActivity extends RxAppCompatActivity implements BlunoLibrary.BlunoListener {

    private final int REQUEST_ENABLE_BT = 1;

//    TextView tv;

    private TextView throttleStatus, steerStatus;
    private Joystick throttleStick, steerStick;
    private double throttle, steer;
    private String sdirection, sgas;

    BlunoLibrary blunoLibrary;

    private SerializationSerialConnection simpleSerialization = new SerializationSerialConnection();
    private CommandData commandData = new CommandData();

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

    class LoopSend extends Thread {
        public boolean running = true;

        @Override
        public void run() {
            steer = 1;
            while (running) {
                try {
                    throttle++;
                    if (throttle > 100) {
                        throttle = 50;
                        steer *= -1;
                    }


                    blunoLibrary.serialSend(simpleSerialization.write(calculatePower()));

                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    LoopSend loop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        // Example of a call to a native method
//        tv = (TextView) findViewById(R.id.sample_text);
//        tv.setText(stringFromJNI());

        blunoLibrary = CarCameraApplication.getBluno(this);
        prepareBluno();

        throttleStatus = (TextView) findViewById(R.id.throttle_status);
        steerStatus = (TextView) findViewById(R.id.steer_status);

        throttleStick = (Joystick) findViewById(R.id.throttle_stick);
        steerStick = (Joystick) findViewById(R.id.steer_stick);

        Observable<CommandData> ob1 = Observable.create(new Observable.OnSubscribe<CommandData>() {

            private int tDegree, tOffset = 0;

            @Override
            public void call(final Subscriber<? super CommandData> subscriber) {
                verifyMainThread();

                subscriber.add(new MainThreadSubscription() {
                    @Override
                    protected void onUnsubscribe() {
//                        throttleStick.setOnJoystickMoveListener(null, 100);
                        throttleStick.setJoystickListener(null);
                    }
                });

                throttleStick.setJoystickListener(new JoystickListener() {
                    @Override
                    public void onDown() {

                    }

                    @Override
                    public void onDrag(float degrees, float offset) {
                        int nDegree = (int) degrees;
                        int nOffset = (int) (offset * 100);

                        if (nDegree == tDegree && nOffset == tOffset) return;

                        tDegree = nDegree;
                        tOffset = nOffset;

                        if (tDegree > 0) {
                            sgas = "Foward";
                            throttle = nOffset;
                        } else {
                            sgas = "Reverse";
                            throttle = nOffset * -1;
                        }

                        subscriber.onNext(calculatePower());
                    }

                    @Override
                    public void onUp() {
                        tDegree = 0;
                        tOffset = 0;
                        sgas = "Stop";
                        throttle = 0;
                        subscriber.onNext(calculatePower());
                    }
                });

//                throttleStick.setOnJoystickMoveListener((angle, power, direction) -> {
//
//                    if(angle == tAngle && power == tPower && direction == tDirection) return;
//
//                    tAngle = angle;
//                    tPower = power;
//                    tDirection = direction;
//
//                    switch (direction) {
//                        // Maju
//                        case JoystickView.FRONT:
//                        case JoystickView.FRONT_RIGHT:
//                        case JoystickView.LEFT_FRONT:
//                            sgas = "Foward";
//                            throttle = power;
//                            break;
//
//                        //Mundur?????
//                        case JoystickView.RIGHT_BOTTOM:
//                        case JoystickView.BOTTOM:
//                        case JoystickView.BOTTOM_LEFT:
//                            sgas = "Reverse";
//                            throttle = power * -1;
//                            break;
//
//                        default:
//                            sgas = "Stop";
//                            throttle = 0;
//                    }
//
//                    subscriber.onNext(calculatePower());
//                }, 100L);
            }
        });

        Observable<CommandData> ob2 = Observable.create(new Observable.OnSubscribe<CommandData>() {

            private int tDegree, tOffset = 0;

            @Override
            public void call(final Subscriber<? super CommandData> subscriber) {
                verifyMainThread();

                subscriber.add(new MainThreadSubscription() {
                    @Override
                    protected void onUnsubscribe() {
                        steerStick.setJoystickListener(null);
//                        steerStick.setOnJoystickMoveListener(null, 100);
                    }
                });

                steerStick.setJoystickListener(new JoystickListener() {
                    @Override
                    public void onDown() {

                    }

                    @Override
                    public void onDrag(float degrees, float offset) {
                        int nDegree = (int) degrees;
                        int nOffset = (int) (offset * 100);

                        if (nDegree == tDegree && nOffset == tOffset) return;

                        tDegree = nDegree;
                        tOffset = nOffset;

                        if (tDegree != 0) {
                            sdirection = "Left";
                            steer = nOffset * -1;
                        } else {
                            sdirection = "Right";
                            steer = nOffset;
                        }

                        subscriber.onNext(calculatePower());
                    }

                    @Override
                    public void onUp() {
                        tDegree = 0;
                        tOffset = 0;
                        sdirection = "Straight";
                        steer = 0;
                        subscriber.onNext(calculatePower());
                    }
                });

//                steerStick.setOnJoystickMoveListener((angle, power, direction) -> {
//
//                    if(angle == tAngle && power == tPower && direction == tDirection) return;
//
//                    tAngle = angle;
//                    tPower = power;
//                    tDirection = direction;
//
//                    switch (direction) {
//                        // Kiri
//                        case JoystickView.BOTTOM_LEFT:
//                        case JoystickView.LEFT:
//                        case JoystickView.LEFT_FRONT:
//                            sdirection = "Right";
//                            steer = power;
//                            break;
//
//                        // Kanan
//                        case JoystickView.FRONT_RIGHT:
//                        case JoystickView.RIGHT:
//                        case JoystickView.RIGHT_BOTTOM:
//                            sdirection = "Left";
//                            steer = power * -1;
//                            break;
//
//                        default:
//                            sdirection = "Straight";
//                            steer = 0;
//                    }
//
//                    subscriber.onNext(calculatePower());
//                }, 100L);
            }
        });

//        Observable<byte[]> ob1 = Observable.create(new Observable.OnSubscribe<byte[]>() {
//
//            private int tAngle, tPower, tDirection = 0;
//
//            @Override
//            public void call(final Subscriber<? super byte[]> subscriber) {
//                verifyMainThread();
//
//                subscriber.add(new MainThreadSubscription() {
//                    @Override
//                    protected void onUnsubscribe() {
//                         throttleStick.setOnJoystickMoveListener(null, 100);
//                    }
//                });
//
//                throttleStick.setOnJoystickMoveListener((angle, power, direction) -> {
//
//                    if(angle == tAngle && power == tPower && direction == tDirection) return;
//
//                    tAngle = angle;
//                    tPower = power;
//                    tDirection = direction;
//
//                    switch (direction) {
//                        // Maju
//                        case JoystickView.FRONT:
//                        case JoystickView.FRONT_RIGHT:
//                        case JoystickView.LEFT_FRONT:
//                            sgas = "Foward";
//                            throttle = power;
//                            break;
//
//                        //Mundur?????
//                        case JoystickView.RIGHT_BOTTOM:
//                        case JoystickView.BOTTOM:
//                        case JoystickView.BOTTOM_LEFT:
//                            sgas = "Reverse";
//                            throttle = power * -1;
//                            break;
//
//                        default:
//                            sgas = "Stop";
//                            throttle = 0;
//                    }
//
//                    subscriber.onNext(calculatePower());
//                }, 100L);
//            }
//        });
//
//        Observable<byte[]> ob2 = Observable.create(new Observable.OnSubscribe<byte[]>() {
//
//            private int tAngle, tPower, tDirection = 0;
//
//            @Override
//            public void call(final Subscriber<? super byte[]> subscriber) {
//                verifyMainThread();
//
//                subscriber.add(new MainThreadSubscription() {
//                    @Override
//                    protected void onUnsubscribe() {
//                        steerStick.setOnJoystickMoveListener(null, 100);
//                    }
//                });
//
//                steerStick.setOnJoystickMoveListener((angle, power, direction) -> {
//
//                    if(angle == tAngle && power == tPower && direction == tDirection) return;
//
//                    tAngle = angle;
//                    tPower = power;
//                    tDirection = direction;
//
//                    switch (direction) {
//                        // Kiri
//                        case JoystickView.FRONT:
//                        case JoystickView.FRONT_RIGHT:
//                        case JoystickView.LEFT_FRONT:
//                            sdirection = "Forward";
//                            steer = power;
//                            break;
//
//                        // Kanan
//                        case JoystickView.RIGHT_BOTTOM:
//                        case JoystickView.BOTTOM:
//                        case JoystickView.BOTTOM_LEFT:
//                            sdirection = "Reverse";
//                            steer = power * -1;
//                            break;
//
//                        default:
//                            sdirection = "Stop";
//                            steer = 0;
//                    }
//
//                    subscriber.onNext(calculatePower());
//                }, 100L);
//            }
//        });

        Observable.combineLatest(Observable.merge(ob1, ob2).onBackpressureBuffer(),
                Observable.interval(300, TimeUnit.MILLISECONDS).onBackpressureBuffer(),
                (commandData1, aLong) -> commandData1)
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::sendCommand);
    }


    @Override
    protected void onPause() {
        //blunoLibrary.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!blunoLibrary.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        blunoLibrary.resume();
    }

    @Override
    protected void onDestroy() {
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

    // Initiating Menu XML file (menu.xml)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.steer_mode_menu, menu);
        return true;
    }

    /**
     * Event Handling for Individual menu item selected
     * Identify single menu item by it's id
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.steer_mode:
                temp_mode = steerMode.getValue();

                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                CharSequence items[] = new CharSequence[]{"PWM Mode", "Off Steer", "Decrease & Increase Speed"};
                adb.setSingleChoiceItems(items, temp_mode, (dialog, which) -> temp_mode = which);
                adb.setPositiveButton("OK", (dialog, which) -> steerMode = STEER_MODE.values()[temp_mode]);
                adb.setNegativeButton("Cancel", null);
                adb.setTitle("Steer Mode");
                adb.show();

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
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

        switch (state) {
            case isNull:
                break;
            case isScanning:
                break;
            case isToScan:
//                if(loop != null) {
//                    loop.running = false;
//                    loop = null;
//                }
                blunoLibrary.connect("C8:A0:30:F8:A8:C2");
                break;
            case isConnecting:
                break;
            case isConnected:
//                loop = new LoopSend();
//                loop.start();
                break;
            case isDisconnecting:
                break;
        }
    }

    @Override
    public void onSerialReceived(String data) {
        Timber.d("Data received: " + data);
//        tv.append(data);
//        ((ScrollView) tv.getParent()).fullScroll(View.FOCUS_DOWN);
    }

    public static int randInt(int min, int max) {

        // NOTE: This will (intentionally) not run as written so that folks
        // copy-pasting have to think about how to initialize their
        // Random instance.  Initialization of the Random instance is outside
        // the main scope of the question, but some decent options are to have
        // a field that is initialized once and then re-used as needed or to
        // use ThreadLocalRandom (if using at least Java 1.7).
        Random rand = new Random();

        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive

        return rand.nextInt((max - min) + 1) + min;
    }

    private CommandData lastCommand = new CommandData();

    private void sendCommand(CommandData command) {
//        if(command.equals(lastCommand)) return;

        lastCommand.assign(command);

        byte[] data = simpleSerialization.write(command);


        steerStatus.setText("Power:" + steer + " Dir: " + sdirection);
        throttleStatus.setText("Power:" + throttle + " Dir: " + sgas);

        Timber.d(
                commandData.leftMotor + " " + commandData.leftDirection + " " +
                        commandData.rightMotor + " " + commandData.rightDirection
                        + " | L" + commandData.leftDirection + " " + commandData.leftMotor + " R" + commandData.rightDirection + " " + commandData.rightMotor);


        blunoLibrary.serialSend(data);
    }

    CommandData calculatePower() {
        int totalPower = Math.min((int) (150 * (Math.abs(throttle) / 100)), 150);

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

//        commandData.leftMotor = (int) Math.round(255 * (Math.min((Math.abs(steer)) / 100, 100)));
//        commandData.leftDirection = (steer < 0) ? 0 : 1;
//
//        commandData.rightMotor = (int) Math.round(255 * (Math.min(Math.abs(throttle) / 100, 100)));
//        commandData.rightDirection = (throttle < 0) ? 0 : 1;

//        return simpleSerialization.write(commandData);

        return commandData;
    }
}
