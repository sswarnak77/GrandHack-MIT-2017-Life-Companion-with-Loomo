package com.segway.speechdemo;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.voice.Languages;
import com.segway.robot.sdk.voice.Recognizer;
import com.segway.robot.sdk.voice.Speaker;
import com.segway.robot.sdk.voice.VoiceException;
import com.segway.robot.sdk.voice.audiodata.RawDataListener;
import com.segway.robot.sdk.voice.grammar.GrammarConstraint;
import com.segway.robot.sdk.voice.grammar.Slot;
import com.segway.robot.sdk.voice.recognition.RecognitionListener;
import com.segway.robot.sdk.voice.recognition.RecognitionResult;
import com.segway.robot.sdk.voice.recognition.WakeupListener;
import com.segway.robot.sdk.voice.recognition.WakeupResult;
import com.segway.robot.sdk.voice.tts.TtsListener;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final String FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
    private static final int SHOW_MSG = 0x0001;
    private static final int APPEND = 0x000f;
    private static final int CLEAR = 0x00f0;
    private ServiceBinder.BindStateListener mRecognitionBindStateListener;
    private ServiceBinder.BindStateListener mSpeakerBindStateListener;
    private boolean isBeamForming = false;
    private boolean bindSpeakerService;
    private boolean bindRecognitionService;
    private int mSpeakerLanguage;
    private int mRecognitionLanguage;
    private Button mBindButton;
    private Button mUnbindButton;
    private Button mSpeakButton;
    private Button mStopSpeakButton;
    private Button mStartRecognitionButton;
    private Button mStopRecognitionButton;
    private Button mBeamFormListenButton;
    private Button mStopBeamFormListenButton;
    private Switch mEnableBeamFormSwitch;
    private TextView mStatusTextView;
    private Recognizer mRecognizer;
    private Speaker mSpeaker;
    private WakeupListener mWakeupListener;
    private RecognitionListener mRecognitionListener;
    private RawDataListener mRawDataListener;
    private TtsListener mTtsListener;
    private GrammarConstraint mTwoSlotGrammar;
    private GrammarConstraint mThreeSlotGrammar;
    private VoiceHandler mHandler = new VoiceHandler(this);

    public static class VoiceHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        private VoiceHandler(MainActivity instance) {
            mActivity = new WeakReference<>(instance);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity mainActivity = mActivity.get();
            if (mainActivity != null) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case SHOW_MSG:
                        mainActivity.showMessage((String) msg.obj, msg.arg1);
                        break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        Log.d("hello123", "hello kic");
        mRecognizer = Recognizer.getInstance();
        mSpeaker = Speaker.getInstance();
        initButtons();
        initListeners();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mStatusTextView.setText("");
    }

    // init UI.
    private void initButtons() {
        mBindButton = (Button) findViewById(R.id.button_bind);
        mUnbindButton = (Button) findViewById(R.id.button_unbind);
        mSpeakButton = (Button) findViewById(R.id.button_speak);
        mStopSpeakButton = (Button) findViewById(R.id.button_stop_speaking);
        mStartRecognitionButton = (Button) findViewById(R.id.button_start_recognition);
        mStopRecognitionButton = (Button) findViewById(R.id.button_stop_recognition);
        mStopBeamFormListenButton = (Button) findViewById(R.id.button_stop_beam_forming);
        mBeamFormListenButton = (Button) findViewById(R.id.button_get_beam_forming_raw_data);
        mStatusTextView = (TextView) findViewById(R.id.textView_status);
        mEnableBeamFormSwitch = (Switch) findViewById(R.id.switch_beam_forming);

        disableSampleFunctionButtons();

        mBindButton.setOnClickListener(this);
        mUnbindButton.setOnClickListener(this);
        mSpeakButton.setOnClickListener(this);
        mStopSpeakButton.setOnClickListener(this);
        mStartRecognitionButton.setOnClickListener(this);
        mStopRecognitionButton.setOnClickListener(this);
        mBeamFormListenButton.setOnClickListener(this);
        mStopBeamFormListenButton.setOnClickListener(this);
    }

    //init listeners.
    private void initListeners() {

        mRecognitionBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0,
                        getString(R.string.recognition_connected));
                mHandler.sendMessage(connectMsg);
                try {
                    //get recognition language when service bind.
                    mRecognitionLanguage = mRecognizer.getLanguage();
                    initControlGrammar();
                    switch (mRecognitionLanguage) {
                        case Languages.EN_US:
                            addEnglishGrammar();
                            break;
                        case Languages.ZH_CN:
                            addChineseGrammar();
                            break;
                    }
                } catch (VoiceException | RemoteException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                bindRecognitionService = true;
                if (bindSpeakerService) {
                    //both speaker service and recognition service bind, enable function buttons.
                    enableSampleFunctionButtons();
                    mUnbindButton.setEnabled(true);
                }
            }

            @Override
            public void onUnbind(String s) {
                //speaker service or recognition service unbind, disable function buttons.
                disableSampleFunctionButtons();
                mUnbindButton.setEnabled(false);
                Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0, getString(R.string.recognition_disconnected));
                mHandler.sendMessage(connectMsg);
            }
        };

        mSpeakerBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                try {
                    Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0,
                            getString(R.string.speaker_connected));
                    mHandler.sendMessage(connectMsg);
                    //get speaker service language.
                    mSpeakerLanguage = mSpeaker.getLanguage();
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                bindSpeakerService = true;
                if (bindRecognitionService) {
                    //both speaker service and recognition service bind, enable function buttons.
                    enableSampleFunctionButtons();
                    mUnbindButton.setEnabled(true);
                }
            }

            @Override
            public void onUnbind(String s) {
                //speaker service or recognition service unbind, disable function buttons.
                disableSampleFunctionButtons();
                Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0, getString(R.string.speaker_disconnected));
                mHandler.sendMessage(connectMsg);
            }
        };

        mWakeupListener = new WakeupListener() {
            @Override
            public void onStandby() {
                Log.d(TAG, "onStandby");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "wakeup start, you can say \"OK loomo\".");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public void onWakeupResult(WakeupResult wakeupResult) {
                //show the wakeup result and wakeup angle.
                Log.d(TAG, "wakeup word:" + wakeupResult.getResult() + ", angle: " + wakeupResult.getAngle());
                Message resultMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "wakeup result:" + wakeupResult.getResult() + ", angle:" + wakeupResult.getAngle());
                mHandler.sendMessage(resultMsg);
            }

            @Override
            public void onWakeupError(String s) {
                //show the wakeup error reason.
                Log.d(TAG, "onWakeupError");
                Message errorMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "wakeup error: " + s);
                mHandler.sendMessage(errorMsg);
            }
        };

        mRecognitionListener = new RecognitionListener() {
            @Override
            public void onRecognitionStart() {
                Log.d(TAG, "onRecognitionStart");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "recognition start, you can say \"turn left\".");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public boolean onRecognitionResult(RecognitionResult recognitionResult) {
                //show the recognition result and recognition result confidence.
                Log.d(TAG, "recognition phase: " + recognitionResult.getRecognitionResult() +
                        ", confidence:" + recognitionResult.getConfidence());
                String result = recognitionResult.getRecognitionResult();
                Message resultMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "recognition result:" + result + ", confidence:" + recognitionResult.getConfidence());
                mHandler.sendMessage(resultMsg);
                if (result.contains("hello") || result.contains("hi")) {
                    try {
                        mRecognizer.removeGrammarConstraint(mThreeSlotGrammar);
                    } catch (VoiceException e) {
                        Log.e(TAG, "Exception: ", e);
                    }
                    //true means continuing to recognition, false means wakeup.
                    return true;
                }
                return false;
            }

            @Override
            public boolean onRecognitionError(String s) {
                //show the recognition error reason.
                Log.d(TAG, "onRecognitionError: " + s);
                Message errorMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "recognition error: " + s);
                mHandler.sendMessage(errorMsg);
                return false; //to wakeup
            }
        };

        mRawDataListener = new RawDataListener() {
            @Override
            public void onRawData(byte[] bytes, int i) {
                createFile(bytes, "raw.pcm");
            }
        };

        mTtsListener = new TtsListener() {
            @Override
            public void onSpeechStarted(String s) {
                //s is speech content, callback this method when speech is starting.
                Log.d(TAG, "onSpeechStarted() called with: s = [" + s + "]");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "speech start");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public void onSpeechFinished(String s) {
                //s is speech content, callback this method when speech is finish.
                Log.d(TAG, "onSpeechFinished() called with: s = [" + s + "]");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "speech end");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public void onSpeechError(String s, String s1) {
                //s is speech content, callback this method when speech occurs error.
                Log.d(TAG, "onSpeechError() called with: s = [" + s + "], s1 = [" + s1 + "]");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "speech error: " + s1);
                mHandler.sendMessage(statusMsg);
            }
        };

        mEnableBeamFormSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                try {
                    mRecognizer.beamForming(b);
                    Message beamForming = mHandler.obtainMessage(SHOW_MSG);
                    if (b) {
                        beamForming.obj = "enable beam forming";
                    } else {
                        beamForming.obj = "disable beam forming";
                    }
                    mHandler.sendMessage(beamForming);
                    mEnableBeamFormSwitch.setText(b ? R.string.enabled : R.string.disabled);
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
            }
        });
    }

    //enable sample function buttons.
    private void enableSampleFunctionButtons() {
        mSpeakButton.setEnabled(true);
        mStopSpeakButton.setEnabled(true);
        mStartRecognitionButton.setEnabled(true);
        mBeamFormListenButton.setEnabled(true);
        mEnableBeamFormSwitch.setEnabled(true);
    }

    //disable sample function buttons.
    private void disableSampleFunctionButtons() {
        mUnbindButton.setEnabled(false);
        mSpeakButton.setEnabled(false);
        mStopSpeakButton.setEnabled(false);
        mStartRecognitionButton.setEnabled(false);
        mStopRecognitionButton.setEnabled(false);
        mStopBeamFormListenButton.setEnabled(false);
        mBeamFormListenButton.setEnabled(false);
        mEnableBeamFormSwitch.setEnabled(false);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_bind:
                showTip("bind recognition service and speaker service.");
                mStatusTextView.setText("");
                //bind the recognition service.
                mRecognizer.bindService(MainActivity.this, mRecognitionBindStateListener);

                //bind the speaker service.
                mSpeaker.bindService(MainActivity.this, mSpeakerBindStateListener);
                break;
            case R.id.button_unbind:
                showTip("unbind recognition service and speaker service.");
                //unbind the recognition service and speaker service.
                if (isBeamForming) {
                    try {
                        mRecognizer.stopBeamFormingListen();
                    } catch (VoiceException e) {
                        Log.e(TAG, "Exception: ", e);
                    }
                }
                mRecognizer.unbindService();
                mSpeaker.unbindService();
                mStatusTextView.setText(R.string.disconnect);
                disableSampleFunctionButtons();
                mBindButton.setEnabled(true);
                break;
            case R.id.button_speak:
                showTip("start to speak.");
                Log.d(TAG, "onClick speak");
                //tts
                /*try {
                    Log.d(TAG, "stopSpeak");
                    mSpeaker.stopSpeak();
                } catch (VoiceException | RemoteException e) {
                    Log.e(TAG, "Exception: ", e);
                }*/
                try {
                    if (mSpeakerLanguage == Languages.EN_US) {
                        Log.d(TAG, "start speak");
                        mSpeaker.speak("hello world, I am a segway robot.", mTtsListener);
                    } else if (mSpeakerLanguage == Languages.ZH_CN) {
                        mSpeaker.speak("你好，我是赛格威机器人。", mTtsListener);
                    } else {
                        Log.e(TAG, "It should not happen!");
                        break;
                    }
                    //block for 3 seconds, return true if speech time is smaller than 3 seconds, else return false.
                    /*boolean timeout = mSpeaker.waitForSpeakFinish(3000);*/
                } catch (VoiceException e) {
                    Log.w(TAG, "Exception: ", e);
                }
                break;
            case R.id.button_stop_speaking:
                showTip("stop speaking.");
                //stop speech.
                try {
                    mSpeaker.stopSpeak();
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                break;
            case R.id.button_start_recognition:
                showTip("start wakeup and recognition.");
                //start the wakeup and the recognition.
                mStartRecognitionButton.setEnabled(false);
                mStopRecognitionButton.setEnabled(true);
                mBeamFormListenButton.setEnabled(false);
                try {
                    mRecognizer.startRecognition(mWakeupListener, mRecognitionListener);
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                break;
            case R.id.button_stop_recognition:
                showTip("stop wakeup and recognition.");
                //stop getting results of the wakeup and the recognition.
                mStartRecognitionButton.setEnabled(true);
                mStopRecognitionButton.setEnabled(false);
                mBeamFormListenButton.setEnabled(true);
                try {
                    mRecognizer.stopRecognition();
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                break;
            case R.id.button_get_beam_forming_raw_data:
                showTip("start to get beam forming raw data.");
                isBeamForming = true;
                //start beam forming listening
                mBeamFormListenButton.setEnabled(false);
                mStopBeamFormListenButton.setEnabled(true);
                mStartRecognitionButton.setEnabled(false);
                mStatusTextView.setText(R.string.start_beam_forming);
                try {
                    mRecognizer.startBeamFormingListen(mRawDataListener);
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                break;
            case R.id.button_stop_beam_forming:
                showTip("stop beam forming listening.");
                isBeamForming = false;
                //stop beam forming listening
                mBeamFormListenButton.setEnabled(true);
                mStopBeamFormListenButton.setEnabled(false);
                mStartRecognitionButton.setEnabled(true);
                mStatusTextView.setText(R.string.close_beam_forming);
                try {
                    mRecognizer.stopBeamFormingListen();
                } catch (VoiceException  e) {
                    Log.e(TAG, "Exception: ", e);
                }
                break;
        }
    }

    private void addEnglishGrammar() throws VoiceException, RemoteException {
        String grammarJson = "{\n" +
                "         \"name\": \"play_media\",\n" +
                "         \"slotList\": [\n" +
                "             {\n" +
                "                 \"name\": \"play_cmd\",\n" +
                "                 \"isOptional\": false,\n" +
                "                 \"word\": [\n" +
                "                     \"play\",\n" +
                "                     \"close\",\n" +
                "                     \"pause\"\n" +
                "                 ]\n" +
                "             },\n" +
                "             {\n" +
                "                 \"name\": \"media\",\n" +
                "                 \"isOptional\": false,\n" +
                "                 \"word\": [\n" +
                "                     \"the music\",\n" +
                "                     \"the video\"\n" +
                "                 ]\n" +
                "             }\n" +
                "         ]\n" +
                "     }";
        mTwoSlotGrammar = mRecognizer.createGrammarConstraint(grammarJson);
        mRecognizer.addGrammarConstraint(mTwoSlotGrammar);
        mRecognizer.addGrammarConstraint(mThreeSlotGrammar);
    }

    private void addChineseGrammar() throws VoiceException, RemoteException {
        Slot play = new Slot("play", false, Arrays.asList("播放", "打开", "关闭", "暂停"));
        Slot media = new Slot("media", false, Arrays.asList("音乐", "视频", "电影"));
        List<Slot> slotList = new LinkedList<>();
        slotList.add(play);
        slotList.add(media);
        mTwoSlotGrammar = new GrammarConstraint("play_media", slotList);
        mRecognizer.addGrammarConstraint(mTwoSlotGrammar);
        mRecognizer.addGrammarConstraint(mThreeSlotGrammar);
    }

    // init control grammar, it can't control robot. :)
    private void initControlGrammar() {

        switch (mRecognitionLanguage) {
            case Languages.EN_US:
                Slot moveSlot = new Slot("move");
                Slot toSlot = new Slot("to");
                Slot orientationSlot = new Slot("orientation");
                List<Slot> controlSlotList = new LinkedList<>();
                moveSlot.setOptional(false);
                moveSlot.addWord("turn");
                moveSlot.addWord("move");
                controlSlotList.add(moveSlot);

                toSlot.setOptional(true);
                toSlot.addWord("to the");
                controlSlotList.add(toSlot);

                orientationSlot.setOptional(false);
                orientationSlot.addWord("right");
                orientationSlot.addWord("left");
                controlSlotList.add(orientationSlot);

                mThreeSlotGrammar = new GrammarConstraint("three slots grammar", controlSlotList);
                break;

            case Languages.ZH_CN:
                Slot helloSlot;
                Slot friendSlot;
                Slot otherSlot;
                List<Slot> sayHelloSlotList = new LinkedList<>();

                helloSlot = new Slot("hello", false, Arrays.asList(
                        "你好",
                        "你们好"));
                friendSlot = new Slot("friend", true, Arrays.asList(
                        "各位",
                        "我的朋友们"
                ));
                otherSlot = new Slot("other", false, Arrays.asList(
                        "我叫赛格威",
                        "很高兴在里见到大家"
                ));
                sayHelloSlotList.add(helloSlot);
                sayHelloSlotList.add(friendSlot);
                sayHelloSlotList.add(otherSlot);
                mThreeSlotGrammar = new GrammarConstraint("three slots grammar", sayHelloSlotList);
                break;
        }
    }

    private void showMessage(String msg, final int pattern) {
        switch (pattern) {
            case CLEAR:
                mStatusTextView.setText(msg);
                break;
            case APPEND:
                mStatusTextView.append(msg);
                break;
        }
    }

    private void createFile(byte[] buffer, String fileName) {
        RandomAccessFile randomFile = null;
        try {
            randomFile = new RandomAccessFile(FILE_PATH + fileName, "rw");
            long fileLength = randomFile.length();
            randomFile.seek(fileLength);
            randomFile.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (randomFile != null) {
                try {
                    randomFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void showTip(String tip) {
        Toast.makeText(this, tip, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (null != this.getCurrentFocus()) {
            InputMethodManager mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            return mInputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (mRecognizer != null) {
            mRecognizer = null;
        }
        if (mSpeaker != null) {
            mSpeaker = null;
        }
        super.onDestroy();
    }
}
