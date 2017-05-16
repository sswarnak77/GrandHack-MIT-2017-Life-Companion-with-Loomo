package com.segway.robot.locomotionsample;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.locomotion.sbv.AngularVelocity;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.locomotion.sbv.LinearVelocity;

import java.util.Timer;
import java.util.TimerTask;

public class BaseActivity extends Activity {

    private static final int RUN_TIME = 2000;
    Base mBase;
    boolean isBind = false;
    EditText mLinearVelocity;
    EditText mAngularVelocity;
    View mEditTextFocus;

    TextView mLeftTicks;
    TextView mRightTicks;
    TextView mTicksTime;
    Timer mTimer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        mLinearVelocity = (EditText) findViewById(R.id.linear_speed_value);
        mAngularVelocity = (EditText) findViewById(R.id.v_speed_value);

        mLeftTicks = (TextView) findViewById(R.id.left_ticks);
        mRightTicks = (TextView) findViewById(R.id.right_ticks);
        mTicksTime = (TextView) findViewById(R.id.ticks_time);

        mLinearVelocity.setOnFocusChangeListener(mOnFocusChangeListener);
        mAngularVelocity.setOnFocusChangeListener(mOnFocusChangeListener);

        initBase();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        initBase();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }

        if (isBind){
            mBase.unbindService();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mEditTextFocus != null) {
                    hideKeyboard(mEditTextFocus);
                    mEditTextFocus = null;
                } else {
                    onBackPressed();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onClick(View view) {
        if (!isBind) {
            return;
        }
        switch (view.getId()) {
            case R.id.set_linear_speed:
                new Thread() {
                    @Override
                    public void run() {
                        if (!Util.isEditTextEmpty(mLinearVelocity)) {
                            // set robot base linearVelocity, unit is rad/s, rand is -PI ~ PI.
                            mBase.setLinearVelocity(Util.getEditTextFloatValue(mLinearVelocity));
                        }

                        // let the robot run for 2 seconds
                        try {
                            Thread.sleep(RUN_TIME);
                        } catch (InterruptedException e) {
                        }

                        // stop
                        mBase.setLinearVelocity(0);
                    }
                }.start();
                break;
            case R.id.set_v_speed:
                new Thread() {
                    @Override
                    public void run() {
                        if (!Util.isEditTextEmpty(mAngularVelocity)) {
                            // set robot base ANGULARVelocity, unit is rad/s, rand is -PI ~ PI.
                            mBase.setAngularVelocity(Util.getEditTextFloatValue(mAngularVelocity));
                        }

                        // let the robot run for 2 seconds
                        try {
                            Thread.sleep(RUN_TIME);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        // stop
                        mBase.setAngularVelocity(0);
                    }
                }.start();
                break;
            case R.id.stop:
                // stop robot
                mBase.stop();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBase.unbindService();
    }

    View.OnFocusChangeListener mOnFocusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus) {
                mEditTextFocus = null;
                hideKeyboard(v);
            } else {
                mEditTextFocus = v;
            }
        }
    };

    public void hideKeyboard(View view) {
        InputMethodManager inputMethodManager =(InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void initBase() {
        // get Base Instance
        mBase = Base.getInstance();
        // bindService, if not, all Base api will not work.
        mBase.bindService(getApplicationContext(), new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                isBind = true;
                mTimer = new Timer();
                mTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        final AngularVelocity av = mBase.getAngularVelocity();
                        final LinearVelocity lv = mBase.getLinearVelocity();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mLeftTicks.setText("AngularVelocity:" + av.getSpeed());
                                mRightTicks.setText("LinearVelocity:" + lv.getSpeed());
                                mTicksTime.setText("Timestamp:" + av.getTimestamp());
                            }
                        });
                    }
                }, 50, 200);
            }

            @Override
            public void onUnbind(String reason) {
                isBind = false;
                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer = null;
                }
            }
        });
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
