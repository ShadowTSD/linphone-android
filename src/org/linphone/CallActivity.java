package org.linphone;
/*
CallActivity.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphonePlayer;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PresenceActivityType;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.ui.Numpad;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.app.Fragment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


/**
 * @author Sylvain Berfini
 */
public class CallActivity extends Activity implements OnClickListener,
        SensorEventListener,
        AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener
{
    private final static int SECONDS_BEFORE_HIDING_CONTROLS = 4000;
    private final static int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;

    private static CallActivity instance;

    private Handler mControlsHandler = new Handler();
    private Runnable mControls;
    private ImageView switchCamera;
    private RelativeLayout mActiveCallHeader, sideMenuContent, avatar_layout;
    private ImageView pause, dialer, video, micro, speaker, options, addCall, transfer, conference, conferenceStatus, contactPicture, maskPicture;
    private ImageView audioRoute, routeSpeaker, routeEarpiece, routeBluetooth, menu, chat;
    private LinearLayout mNoCurrentCall, callInfo, mCallPaused;
    private ProgressBar videoProgress;
    private StatusFragment status;
    private CallAudioFragment audioCallFragment;
    private CallVideoFragment videoCallFragment;
    private boolean isSpeakerEnabled = false, isMicMuted = true, isTransferAllowed, isAnimationDisabled;
    private LinearLayout mControlsLayout;
    private Numpad numpad;
    private int cameraNumber;
    private Animation slideOutLeftToRight, slideInRightToLeft, slideInBottomToTop, slideInTopToBottom, slideOutBottomToTop, slideOutTopToBottom;
    private CountDownTimer timer;
    private boolean isVideoCallPaused = false;

    private static PowerManager powerManager;
    private static PowerManager.WakeLock wakeLock;
    private static int field = 0x00000020;
    private SensorManager mSensorManager;
    private Sensor mProximity;

    private LinearLayout callsList, conferenceList;
    private LayoutInflater inflater;
    private ViewGroup container;
    private boolean isConferenceRunning = false;
    private LinphoneCoreListenerBase mListener;
    private DrawerLayout sideMenu;
    private Button speakRequest, hangUp, speakToAll;
    private static final String domain = "116.228.237.226";
    public static CallActivity instance() {
        return instance;
    }

    public static boolean isInstanciated() {
        return instance != null;
    }
    private ContactsListAdapter contactsListAdapter = null;
    private ListView buddyList;
    private Button refresh;
    private MediaSession mMediaSession;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setContentView(R.layout.call);

        isTransferAllowed = getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);

        if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            BluetoothManager.getInstance().initBluetooth();
        }

        isAnimationDisabled = getApplicationContext().getResources().getBoolean(R.bool.disable_animations) || !LinphonePreferences.instance().areAnimationsEnabled();
        cameraNumber = AndroidCameraConfiguration.retrieveCameras().length;

        try {
            // Yeah, this is hidden field.
            field = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
        } catch (Throwable ignored) {
        }

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(field, getLocalClassName());

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        mListener = new LinphoneCoreListenerBase() {

            @Override
            public void callState(LinphoneCore lc, final LinphoneCall call, State state, String message) {
                if (LinphoneManager.getLc().getCallsNb() == 0) {
                    Log.i("getCallsNb======================================"+
                            LinphoneManager.getLcIfManagerNotDestroyedOrNull().getCallsNb());
                    finish();
                    return;
                }
                Log.i("call state@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" + state);
                if (state == State.IncomingReceived) {

                    startIncomingCallActivity();
                    return;
                }

                if (state == State.Paused || state == State.PausedByRemote || state == State.Pausing) {

                    if (LinphoneManager.getLc().getCurrentCall() != null) {
                        enabledVideoButton(false);
                    }
                    if (isVideoEnabled(call)) {
                        showAudioView();
                    }
                }

                if (state == State.Resuming) {

                    if (LinphonePreferences.instance().isVideoEnabled()) {
                        status.refreshStatusItems(call, isVideoEnabled(call));
                        if (call.getCurrentParamsCopy().getVideoEnabled()) {
                            showVideoView();
                        }
                    }
                    if (LinphoneManager.getLc().getCurrentCall() != null) {
                        enabledVideoButton(true);
                    }
                }

                if (state == State.StreamsRunning) {
                    switchVideo(isVideoEnabled(call));
                    //Check media in progress
                    if (!call.mediaInProgress()) {
                        if (LinphonePreferences.instance().isVideoEnabled()) {
                            enabledVideoButton(true);
                        }
                        enabledPauseButton(true);
                    } else {
                        enabledPauseButton(false);
                    }
                    enableAndRefreshInCallActions();

                    if (status != null) {
                        videoProgress.setVisibility(View.GONE);
                        status.refreshStatusItems(call, isVideoEnabled(call));
                    }
                    try {
                        if (LinphoneService.instance().isSpeaking()) {

                            lc.muteMic(false);
                            LinphoneManager.getLcIfManagerNotDestroyedOrNull().sendDtmf('C');    //'C' 表示申请对讲
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (state == State.CallUpdatedByRemote) {
                    //if (true) return;
                    // If the correspondent proposes video while audio call
                    boolean videoEnabled = LinphonePreferences.instance().isVideoEnabled();
                    if (!videoEnabled) {
                        acceptCallUpdate(false);
                        return;
                    }

                    boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
                    boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
                    boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
                    if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
                        showAcceptCallUpdateDialog();

                        timer = new CountDownTimer(SECONDS_BEFORE_DENYING_CALL_UPDATE, 1000) {
                            public void onTick(long millisUntilFinished) {
                            }

                            public void onFinish() {
                                //TODO dismiss dialog
                                acceptCallUpdate(false);
                            }
                        }.start();
                    }
//        			else if (remoteVideo && !LinphoneManager.getLc().isInConference() && autoAcceptCameraPolicy) {
//        				mHandler.post(new Runnable() {
//        					@Override
//        					public void run() {
//        						acceptCallUpdate(true);
//        					}
//        				});
//        			}
                }

                refreshIncallUi();
                transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
            }

            @Override
            public void callEncryptionChanged(LinphoneCore lc, final LinphoneCall call, boolean encrypted, String authenticationToken) {
                if (status != null) {
                    if (call.getCurrentParamsCopy().getMediaEncryption().equals(LinphoneCore.MediaEncryption.ZRTP) && !call.isAuthenticationTokenVerified()) {
                        status.showZRTPDialog(call);
                    }
                    status.refreshStatusItems(call, call.getCurrentParamsCopy().getVideoEnabled());
                }
            }

        };

        if (findViewById(R.id.fragmentContainer) != null) {
            initUI();

            if (LinphoneManager.getLc().getCallsNb() > 0) {
                LinphoneCall call = LinphoneManager.getLc().getCalls()[0];

                if (LinphoneUtils.isCallEstablished(call)) {
                    enableAndRefreshInCallActions();
                }
            }

            if (savedInstanceState != null) {
                // Fragment already created, no need to create it again (else it will generate a memory leak with duplicated fragments)
                isSpeakerEnabled = savedInstanceState.getBoolean("Speaker");
                isMicMuted = savedInstanceState.getBoolean("Mic");
                isVideoCallPaused = savedInstanceState.getBoolean("VideoCallPaused");
                refreshInCallActions();
                return;
            } else {
                isSpeakerEnabled = LinphoneManager.getLc().isSpeakerEnabled();
//				isMicMuted = LinphoneManager.getLc().isMicMuted();
                isMicMuted = true;    //李峰:强制静音
            }

            Fragment callFragment;
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
                callFragment = new CallVideoFragment();
                videoCallFragment = (CallVideoFragment) callFragment;
                displayVideoCall(false);
                isSpeakerEnabled = true;
            } else {
                callFragment = new CallAudioFragment();
                audioCallFragment = (CallAudioFragment) callFragment;
            }

            if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
                BluetoothManager.getInstance().routeAudioToBluetooth();
            }

            callFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commitAllowingStateLoss();

        }
        buddyList = (ListView) findViewById(R.id.buddylist);
        buddyList.setOnItemClickListener(this);
        buddyList.setOnItemLongClickListener(this);
        buddyList.setVisibility(View.INVISIBLE);

        refresh = (Button) findViewById(R.id.btn_refresh1);
        refresh.setOnClickListener(this);

//        IntentFilter quitFilter = new IntentFilter();
//        quitFilter.addAction("android.yview.action.VY_QUIT");
//        MyQuitReceiver myQuitReceiver = new MyQuitReceiver();
//        registerReceiver(myQuitReceiver, quitFilter);
    }

    private boolean isVideoEnabled(LinphoneCall call) {
        if (call != null) {
            return call.getCurrentParamsCopy().getVideoEnabled();
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("Speaker", LinphoneManager.getLc().isSpeakerEnabled());
        outState.putBoolean("Mic", LinphoneManager.getLc().isMicMuted());
        outState.putBoolean("VideoCallPaused", isVideoCallPaused);

        super.onSaveInstanceState(outState);
    }

    private boolean isTablet() {
        return getResources().getBoolean(R.bool.isTablet);
    }

    private static final int CALL_ACTIVITY = 19;
    private AlphabetIndexer indexer;


    class ContactsListAdapter extends ArrayAdapter{
        private int margin;
        private Bitmap bitmapUnknown;
        private List<Contact> contacts;
        private Cursor cursor;
        private LayoutInflater inflater;

        public ContactsListAdapter(Context context, int resource) {
            super(context, resource);
            int m = -1;
            for (int i = 0; i<names.length; i++) {
                String name = names[i];
                try {
//                    if (LinphoneManager.getLcIfManagerNotDestroyedOrNull().getDefaultProxyConfig().getIdentity().contains(name)) {
//                        m=i;
//                    }
                    for (LinphoneProxyConfig proxyConfig : LinphoneManager.getLcIfManagerNotDestroyedOrNull().getProxyConfigList()) {
                        if (proxyConfig.getIdentity().contains(name)) {
                            {m = i;break;}
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (m != -1 && i < names.length-1) {
                    names[i] = names[i+1];
                    caller_id_numbers[i] = caller_id_numbers[i+1];
                }
            }
        }

/*
        ContactsListAdapter(List<Contact> contactsList, Cursor c) {
            contacts = contactsList;
            cursor = c;

            margin = LinphoneUtils.pixelsToDpi(LinphoneActivity.instance().getResources(), 10);
            bitmapUnknown = BitmapFactory.decodeResource(LinphoneActivity.instance().getResources(), R.drawable.avatar);
            inflater = LayoutInflater.from(CallActivity.this);

        }
*/

        public int getCount() {
/*
            if (LinphoneActivity.instance().getResources().getBoolean(R.bool.use_linphone_friend)) {
                return LinphoneManager.getLc().getFriendList().length;
            } else {
                return cursor.getCount();
            }
*/
            return names.length-1;
        }

        public Object getItem(int position) {
/*
            if (contacts == null || position >= contacts.size()) {
                return Compatibility.getContact(getContentResolver(), cursor, position);
            } else {
                return contacts.get(position);
            }
*/
            return caller_id_numbers[position];
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(final int position, View convertView, ViewGroup parent) {

            View view;

            if (convertView != null) {
                view = convertView;
            } else {
                view = LayoutInflater.from(CallActivity.this).inflate(android.R.layout.simple_list_item_1, parent, false);
            }

            String sipAddress = "sip:" + caller_id_numbers[position] + "@" + domain;
//            Log.i("sipAddress:-------------------------------------------->"+sipAddress);

            PresenceActivityType presenceType = null;
            LinphoneFriend friend = null;
            try {
                friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(sipAddress);
                presenceType = friend.getPresenceModel().getActivity().getType();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
            TextView tv = (TextView) view.findViewById(android.R.id.text1);
            if (presenceType == PresenceActivityType.Online) {
                tv.setText(names[position] + "(在线)");
                view.setEnabled(true);
                Log.i("presenceType:------------------------------------------->"+presenceType);
                view.setTag(PresenceActivityType.Online);
            } else if (presenceType == PresenceActivityType.OnThePhone) {
                LinphoneCore lc = LinphoneManager.getLc();
				LinphoneCall currentCall = lc.getCurrentCall();
//                LinphoneCall[] calls = lc.getCalls();
                String curCallAddress = null;
                try {
                    Log.i("curCallAddress2:------------------->" + curCallAddress);
                    tv.setText(names[position] + "(正在通话中)");
                    view.setEnabled(true);
                    view.setTag(PresenceActivityType.OnThePhone);
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            } else if (presenceType == PresenceActivityType.Busy) {
                tv.setText(names[position] + "(忙,无法接听)");
                view.setEnabled(true);
                view.setTag(PresenceActivityType.Busy);
            } else if (presenceType == PresenceActivityType.Away) {
                tv.setText(names[position] + "(离开)");
                view.setEnabled(true);
                view.setTag(PresenceActivityType.Away);
            } else if (presenceType == PresenceActivityType.Offline) {
                tv.setEnabled(false);
                tv.setClickable(false);
                tv.setText(names[position] + "(下线)");
                view.setTag(PresenceActivityType.Offline);
            } else {
                tv.setEnabled(false);
                tv.setClickable(false);
                tv.setText(names[position]+"(下线)");
                view.setTag(PresenceActivityType.Offline);
            }

            return view;

        }

        @Override
        public boolean isEnabled(int position) {
            PresenceActivityType presenceType = null;
            LinphoneFriend friend = null;
            String sipAddress = "sip:" + caller_id_numbers[position] + "@" + domain;
            try {
                friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(sipAddress);
                presenceType = friend.getPresenceModel().getActivity().getType();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
            if (presenceType == PresenceActivityType.Offline) {
                return false;
            }
            else {
                return true;
            }
//            return super.isEnabled(position);
        }
    }

    private void prepareBuddyList() {
        String[] names = new String[]{"test1", "test2", "test3", "test4", "test5", "test6", "test7"};
        ArrayList<ContentProviderOperation> ops;
        ops = new ArrayList<ContentProviderOperation>();
        for (String name : names) {
            ContactsManager.getInstance().createNewContact(ops, name, null);
        }
//        for (NewOrUpdatedNumberOrAddress numberOrAddress : numbersAndAddresses) {
//            numberOrAddress.save();
//        }
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            //lifeng:这里对新增对CONTACT考虑是否添加为好友
//            addLinphoneFriendIfNeeded();
//            removeLinphoneTagIfNeeded();
            ContactsManager.getInstance().prepareContactsInBackground();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void onClickAddBuddy(View view) {
        Intent intent = new Intent(this, ContactEditorActivity.class);
        Bundle extras = new Bundle();
        extras.putSerializable("NewSipAdress", null);
        startActivity(new Intent().setClass(this, ContactEditorActivity.class).putExtras(extras));
    }
    private String[] names = new String[]{"test1", "test2", "test3", "test4", "test5", "test6", "test7"};
    private String[] caller_id_numbers = new String[]{"1001", "1002", "1003", "1004", "1005", "1006", "1007"};

    public void updateStatus(LinphoneFriend friend, PresenceActivityType presenceActivityType) {
        Log.i("friend.getAddress().userName():---------------->" + friend.getAddress().getUserName());
        for (int i=0; i<names.length-1; i++) {
            if (caller_id_numbers[i].equals(friend.getAddress().getUserName())) {
                if (presenceActivityType == PresenceActivityType.Online) {
//                    names[i] += "(在线)";
                }
                else if (presenceActivityType == PresenceActivityType.OnThePhone) {

                } else if (presenceActivityType == PresenceActivityType.Busy) {

                }
            }
        }
//        buddyList.invalidate();
    }
    public void onClickBuddyList(View view) {
        ContactsListAdapter contactsListAdapter = null;
        if (buddyList.getAdapter() == null) {
            contactsListAdapter = new ContactsListAdapter(this, 0);
            for (int i = 0; i < names.length - 1; i++) {
                String name = names[i];
                String caller_id_number = caller_id_numbers[i];
                Contact contact = new Contact(name, caller_id_number);
                String sipAddress = "sip:" + name + "@" + domain;
                LinphoneFriend friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(sipAddress);
                if (friend == null) {
                    if (!ContactsManager.getInstance().createNewFriend(contact, sipAddress)) return;
                    Log.i("friend sip address@@@@@@@@@@@@@@@@@@@@@@"+sipAddress);
                    friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(sipAddress);
                }
                friend.enableSubscribes(true);
                friend.setIncSubscribePolicy(LinphoneFriend.SubscribePolicy.SPAccept);
                try {
                    LinphoneManager.getLcIfManagerNotDestroyedOrNull().addFriend(friend);
                } catch (LinphoneCoreException e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            LinphoneFriend[] friends = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getFriendList();
            for (LinphoneFriend friend : friends) {
                friend.enableSubscribes(true);
            }
        }

        buddyList.setAdapter(contactsListAdapter);

        if (buddyList.isShown()) {
            buddyList.setVisibility(View.INVISIBLE);
        } else {
            buddyList.setVisibility(View.VISIBLE);
        }

    }
    private static final int MOTION_EVENT_FROM_MEDIABUTTON = 77;
    private void initUI() {
        inflater = LayoutInflater.from(this);
        container = (ViewGroup) findViewById(R.id.topLayout);
        callsList = (LinearLayout) findViewById(R.id.calls_list);
        conferenceList = (LinearLayout) findViewById(R.id.conference_list);

        //TopBar
        video = (ImageView) findViewById(R.id.video);
        video.setOnClickListener(this);
        enabledVideoButton(false);

        videoProgress = (ProgressBar) findViewById(R.id.video_in_progress);
        videoProgress.setVisibility(View.GONE);

        micro = (ImageView) findViewById(R.id.micro);
        micro.setOnClickListener(this);
        if (isMicMuted) {
            micro.setImageResource(R.drawable.micro_selected);
            LinphoneCore lc = LinphoneManager.getLc();
            lc.muteMic(isMicMuted);
        }

        speaker = (ImageView) findViewById(R.id.speaker);
        speaker.setOnClickListener(this);

        options = (ImageView) findViewById(R.id.options);
        options.setOnClickListener(this);
        options.setEnabled(false);

        //BottonBar
        hangUp = (Button) findViewById(R.id.hang_up);
        hangUp.setOnClickListener(this);

        dialer = (ImageView) findViewById(R.id.dialer);
        dialer.setOnClickListener(this);

        numpad = (Numpad) findViewById(R.id.numpad);
        numpad.getBackground().setAlpha(240);

        chat = (ImageView) findViewById(R.id.chat);
        chat.setOnClickListener(this);

        //Others

        //Active Call
        callInfo = (LinearLayout) findViewById(R.id.active_call_info);

        pause = (ImageView) findViewById(R.id.pause);
        pause.setOnClickListener(this);
        enabledPauseButton(false);

        mActiveCallHeader = (RelativeLayout) findViewById(R.id.active_call);
        mNoCurrentCall = (LinearLayout) findViewById(R.id.no_current_call);
        mCallPaused = (LinearLayout) findViewById(R.id.remote_pause);

//		contactPicture = (ImageView) findViewById(R.id.contact_picture);;
        avatar_layout = (RelativeLayout) findViewById(R.id.avatar_layout);
        //lifeng add
        speakRequest = (Button) findViewById(R.id.btn_speak);
        speakRequest.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (v.isPressed() == false) {
                        Log.i("v.isPressed()@@@@@@@@@@@@@@@@@@@@"+v.isPressed());
                        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                        lc.muteMic(false);
                        LinphoneManager.getLc().sendDtmf('C');    //'C' 表示申请对讲
                        if (event.getMetaState() != MOTION_EVENT_FROM_MEDIABUTTON) {
                            LinphoneService.instance().toggleSpeakState();
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    LinphoneCore lc = LinphoneManager.getLc();
                    lc.muteMic(true);
                    LinphoneManager.getLc().sendDtmf('D'); //'D' 表示'对讲完毕'
                    if (event.getMetaState() != MOTION_EVENT_FROM_MEDIABUTTON) {
                        LinphoneService.instance().toggleSpeakState();
                    }
                }
                return false;
            }
        });
        speakToAll = (Button) findViewById(R.id.btn_speakall);
//        speakToAll.setOnClickListener(this);
        /*if(isTablet()){
            speaker.setEnabled(false);
		}
		speaker.setEnabled(false);*/

        //Options
        addCall = (ImageView) findViewById(R.id.add_call);
        addCall.setOnClickListener(this);
        addCall.setEnabled(false);

        transfer = (ImageView) findViewById(R.id.transfer);
        transfer.setOnClickListener(this);
        transfer.setEnabled(false);

        conference = (ImageView) findViewById(R.id.conference);
        conference.setEnabled(false);
        conference.setOnClickListener(this);

        try {
            audioRoute = (ImageView) findViewById(R.id.audio_route);
            audioRoute.setOnClickListener(this);
            routeSpeaker = (ImageView) findViewById(R.id.route_speaker);
            routeSpeaker.setOnClickListener(this);
            routeEarpiece = (ImageView) findViewById(R.id.route_earpiece);
            routeEarpiece.setOnClickListener(this);
            routeBluetooth = (ImageView) findViewById(R.id.route_bluetooth);
            routeBluetooth.setOnClickListener(this);
        } catch (NullPointerException npe) {
            Log.e("Bluetooth: Audio routes menu disabled on tablets for now (1)");
        }

        switchCamera = (ImageView) findViewById(R.id.switchCamera);
        switchCamera.setOnClickListener(this);

        mControlsLayout = (LinearLayout) findViewById(R.id.menu);

        if (!isTransferAllowed) {
            addCall.setBackgroundResource(R.drawable.options_add_call);
        }

        if (!isAnimationDisabled) {
            slideInRightToLeft = AnimationUtils.loadAnimation(this, R.anim.slide_in_right_to_left);
            slideOutLeftToRight = AnimationUtils.loadAnimation(this, R.anim.slide_out_left_to_right);
            slideInBottomToTop = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom_to_top);
            slideInTopToBottom = AnimationUtils.loadAnimation(this, R.anim.slide_in_top_to_bottom);
            slideOutBottomToTop = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom_to_top);
            slideOutTopToBottom = AnimationUtils.loadAnimation(this, R.anim.slide_out_top_to_bottom);
        }

        if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            try {
                audioRoute.setVisibility(View.VISIBLE);
                speaker.setVisibility(View.GONE);
            } catch (NullPointerException npe) {
                Log.e("Bluetooth: Audio routes menu disabled on tablets for now (2)");
            }
        } else {
            try {
                audioRoute.setVisibility(View.GONE);
                speaker.setVisibility(View.VISIBLE);
            } catch (NullPointerException npe) {
                Log.e("Bluetooth: Audio routes menu disabled on tablets for now (3)");
            }
        }

        createInCallStats();

    }

    public Button getSpeakRequest() {
        return speakRequest;
    }
    public void createInCallStats() {
        sideMenu = (DrawerLayout) findViewById(R.id.side_menu);
        menu = (ImageView) findViewById(R.id.call_quality);

        sideMenuContent = (RelativeLayout) findViewById(R.id.side_menu_content);

        menu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sideMenu.isDrawerVisible(Gravity.LEFT)) {
                    sideMenu.closeDrawer(sideMenuContent);
                } else {
                    sideMenu.openDrawer(sideMenuContent);
                }
            }
        });

        status.initCallStatsRefresher(LinphoneManager.getLc().getCurrentCall(), findViewById(R.id.incall_stats));

    }

    private void refreshIncallUi() {
        refreshInCallActions();
        refreshCallList(getResources());
        enableAndRefreshInCallActions();
    }

    private void refreshInCallActions() {
        if (!LinphonePreferences.instance().isVideoEnabled() || isConferenceRunning) {
            enabledVideoButton(false);
        } else {
            if (video.isEnabled()) {
                if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
                    video.setImageResource(R.drawable.camera_selected);
                    videoProgress.setVisibility(View.INVISIBLE);
                } else {
                    video.setImageResource(R.drawable.camera_default);
                }
            } else {
                video.setImageResource(R.drawable.camera_default);
            }
        }

        if (isSpeakerEnabled) {
            speaker.setImageResource(R.drawable.speaker_selected);
        } else {
            speaker.setImageResource(R.drawable.speaker_default);
        }

        if (isMicMuted) {
            micro.setImageResource(R.drawable.micro_selected);
        } else {
            micro.setImageResource(R.drawable.micro_default);
        }

        try {
            if (isSpeakerEnabled) {
                routeSpeaker.setImageResource(R.drawable.route_speaker_selected);
                routeEarpiece.setImageResource(R.drawable.route_earpiece);
                routeBluetooth.setImageResource(R.drawable.route_bluetooth);
            }

            routeSpeaker.setImageResource(R.drawable.route_speaker);
            if (BluetoothManager.getInstance().isUsingBluetoothAudioRoute()) {
                routeEarpiece.setImageResource(R.drawable.route_earpiece);
                routeBluetooth.setImageResource(R.drawable.route_bluetooth_selected);
            } else {
                routeEarpiece.setImageResource(R.drawable.route_earpiece_selected);
                routeBluetooth.setImageResource(R.drawable.route_bluetooth);
            }
        } catch (NullPointerException npe) {
            Log.e("Bluetooth: Audio routes menu disabled on tablets for now (4)");
        }
    }

    private void enableAndRefreshInCallActions() {
        int confsize = 0;

        if (LinphoneManager.getLc().isInConference()) {
            confsize = LinphoneManager.getLc().getConferenceSize() - (LinphoneManager.getLc().isInConference() ? 1 : 0);
        }

        //Enabled transfer button
        if (isTransferAllowed && !LinphoneManager.getLc().soundResourcesLocked())
            enabledTransferButton(true);

        //Enable conference button
        if (LinphoneManager.getLc().getCallsNb() > 1 && LinphoneManager.getLc().getCallsNb() > confsize && !LinphoneManager.getLc().soundResourcesLocked()) {
            enabledConferenceButton(true);
        } else {
            enabledConferenceButton(false);
        }

        addCall.setEnabled(LinphoneManager.getLc().getCallsNb() < LinphoneManager.getLc().getMaxCalls() && !LinphoneManager.getLc().soundResourcesLocked());
        options.setEnabled(!getResources().getBoolean(R.bool.disable_options_in_call) && (addCall.isEnabled() || transfer.isEnabled()));

        if (LinphoneManager.getLc().getCurrentCall() != null && LinphonePreferences.instance().isVideoEnabled() && !LinphoneManager.getLc().soundResourcesLocked()) {
            enabledVideoButton(true);
        }
        if (LinphoneManager.getLc().getCurrentCall() != null && !LinphoneManager.getLc().soundResourcesLocked()) {
            enabledPauseButton(true);
        }
        micro.setEnabled(true);
        if (!isTablet()) {
            speaker.setEnabled(true);
        }
        transfer.setEnabled(true);
        pause.setEnabled(true);
        dialer.setEnabled(true);
    }

    public void updateStatusFragment(StatusFragment statusFragment) {
        status = statusFragment;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
            //displayVideoCallControlsIfHidden();
        }

        if (id == R.id.video) {
            enabledOrDisabledVideo(isVideoEnabled(LinphoneManager.getLc().getCurrentCall()));
        } else if (id == R.id.micro) {
//			toggleMicro();
        } else if (id == R.id.speaker) {
            toggleSpeaker();
        } else if (id == R.id.add_call) {
            goBackToDialer();
        } else if (id == R.id.pause) {
            pauseOrResumeCall(LinphoneManager.getLc().getCurrentCall());
        } else if (id == R.id.hang_up) {
            LinphonePreferences.instance().setBackgroundModeEnabled(false);
            hangUp(true);
        } else if (id == R.id.dialer) {
            hideOrDisplayNumpad();
        } else if (id == R.id.chat) {
            goToChatList();
        } else if (id == R.id.conference) {
            enterConference();
            hideOrDisplayCallOptions();
        } else if (id == R.id.switchCamera) {
            if (videoCallFragment != null) {
                videoCallFragment.switchCamera();
            }
        } else if (id == R.id.transfer) {
            goBackToDialerAndDisplayTransferButton();
        } else if (id == R.id.options) {
            hideOrDisplayCallOptions();
        } else if (id == R.id.audio_route) {
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.route_bluetooth) {
            if (BluetoothManager.getInstance().routeAudioToBluetooth()) {
                isSpeakerEnabled = false;
                routeBluetooth.setImageResource(R.drawable.route_bluetooth_selected);
                routeSpeaker.setImageResource(R.drawable.route_speaker);
                routeEarpiece.setImageResource(R.drawable.route_earpiece);
            }
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.route_earpiece) {
            LinphoneManager.getInstance().routeAudioToReceiver();
            isSpeakerEnabled = false;
            routeBluetooth.setImageResource(R.drawable.route_bluetooth);
            routeSpeaker.setImageResource(R.drawable.route_speaker);
            routeEarpiece.setImageResource(R.drawable.route_earpiece_selected);
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.route_speaker) {
            LinphoneManager.getInstance().routeAudioToSpeaker();
            isSpeakerEnabled = true;
            routeBluetooth.setImageResource(R.drawable.route_bluetooth);
            routeSpeaker.setImageResource(R.drawable.route_speaker_selected);
            routeEarpiece.setImageResource(R.drawable.route_earpiece);
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.call_pause) {
            LinphoneCall call = (LinphoneCall) v.getTag();
            pauseOrResumeCall(call);
        } else if (id == R.id.conference_pause) {
            pauseOrResumeConference();
        } else if (id == R.id.btn_refresh1) {
//            Cursor sipContactsCursor = ContactsManager.getInstance().getSIPContactsCursor();
//            indexer = new AlphabetIndexer(sipContactsCursor, Compatibility.getCursorDisplayNameColumnIndex(sipContactsCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
//            buddyList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
//            buddyList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getSIPContacts(), sipContactsCursor));
            //lifeng:因为好友列表没有存储在数据库中,每次启动LINPHONE好友列表都会置零,所以在显示CONTACTS前,要将所有的CONTACTS添加为FRIEND

            for (int i = 0; i < names.length-1; i++) {
                String name = names[i];
                String caller_id_number = caller_id_numbers[i];
                Contact contact = new Contact(caller_id_number, name);
                String sipAddress = "sip:" + caller_id_number + "@" + domain;
                LinphoneFriend friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(sipAddress);
                if (friend == null) {
                    ContactsManager.getInstance().createNewFriend(contact, sipAddress);
                    friend = LinphoneManager.getLcIfManagerNotDestroyedOrNull().findFriendByAddress(sipAddress);
                }
                friend.enableSubscribes(true);
                friend.setIncSubscribePolicy(LinphoneFriend.SubscribePolicy.SPAccept);
                try {
                    LinphoneManager.getLcIfManagerNotDestroyedOrNull().addFriend(friend);
                } catch (LinphoneCoreException e) {
                    e.printStackTrace();
                }
            }

            if (buddyList.getAdapter() == null) {
                contactsListAdapter = new ContactsListAdapter(this, 0);
                buddyList.setAdapter(contactsListAdapter);
            }

            buddyList.invalidate();
        } else if (id == R.id.btn_speakall) {
            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            LinphoneCall currentCall = lc.getCurrentCall();
            lc.terminateCall(currentCall);
//            for (LinphoneCall call : lc.getCalls()) {
//                pauseOrResumeCall(call);
//            }
//            LinphoneManager.getInstance().changeStatusToOnline();
            LinphoneManager.getInstance().newOutgoingCall("sip:3100@116.228.237.226", null);
        }
    }

    private void enabledVideoButton(boolean enabled) {
        if (enabled) {
            video.setEnabled(true);
        } else {
            video.setEnabled(false);
        }
    }

    private void enabledPauseButton(boolean enabled) {
        if (enabled) {
            pause.setEnabled(true);
            pause.setImageResource(R.drawable.pause_big_default);
        } else {
            pause.setEnabled(false);
            pause.setImageResource(R.drawable.pause_big_disabled);
        }
    }

    private void enabledTransferButton(boolean enabled) {
        if (enabled) {
            transfer.setEnabled(true);
        } else {
            transfer.setEnabled(false);
        }
    }

    private void enabledConferenceButton(boolean enabled) {
        if (enabled) {
            conference.setEnabled(true);
        } else {
            conference.setEnabled(false);
        }
    }

    private void enabledOrDisabledVideo(final boolean isVideoEnabled) {
        final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }

        if (isVideoEnabled) {
            LinphoneCallParams params = call.getCurrentParamsCopy();
            params.setVideoEnabled(false);
            LinphoneManager.getLc().updateCall(call, params);
        } else {
            videoProgress.setVisibility(View.VISIBLE);
            if (!call.getRemoteParams().isLowBandwidthEnabled()) {
                LinphoneManager.getInstance().addVideo();
            } else {
                displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
            }
        }
    }

    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    private void switchVideo(final boolean displayVideo) {
        final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }

        //Check if the call is not terminated
        if (call.getState() == State.CallEnd || call.getState() == State.CallReleased) return;

        if (!displayVideo) {
            showAudioView();
        } else {
            if (!call.getRemoteParams().isLowBandwidthEnabled()) {
                LinphoneManager.getInstance().addVideo();
                if (videoCallFragment == null || !videoCallFragment.isVisible())
                    showVideoView();
            } else {
                displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
            }
        }
    }

    private void showAudioView() {
        mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        replaceFragmentVideoByAudio();
        displayAudioCall();
        showStatusBar();
        removeCallbacks();
    }

    private void showVideoView() {
        if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            Log.w("Bluetooth not available, using speaker");
            LinphoneManager.getInstance().routeAudioToSpeaker();
            isSpeakerEnabled = true;
        }
        refreshInCallActions();

        mSensorManager.unregisterListener(this);
        replaceFragmentAudioByVideo();
        hideStatusBar();
    }

    private void displayNoCurrentCall(boolean display) {
        if (!display) {
            mActiveCallHeader.setVisibility(View.VISIBLE);
            mNoCurrentCall.setVisibility(View.GONE);
        } else {
            mActiveCallHeader.setVisibility(View.GONE);
            mNoCurrentCall.setVisibility(View.VISIBLE);
        }
    }

    private void displayCallPaused(boolean display) {
        if (display) {
            mCallPaused.setVisibility(View.VISIBLE);
        } else {
            mCallPaused.setVisibility(View.GONE);
        }
    }

    private void displayAudioCall() {
        mControlsLayout.setVisibility(View.VISIBLE);
        mActiveCallHeader.setVisibility(View.VISIBLE);
        callInfo.setVisibility(View.VISIBLE);
        avatar_layout.setVisibility(View.VISIBLE);
        switchCamera.setVisibility(View.GONE);
    }

    private void replaceFragmentVideoByAudio() {
        audioCallFragment = new CallAudioFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, audioCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
        }
    }

    private void replaceFragmentAudioByVideo() {
//		Hiding controls to let displayVideoCallControlsIfHidden add them plus the callback
        videoCallFragment = new CallVideoFragment();

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, videoCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
        }
    }

    private void toggleMicro() {
        LinphoneCore lc = LinphoneManager.getLc();
        isMicMuted = !isMicMuted;
        lc.muteMic(isMicMuted);
        if (isMicMuted) {
            micro.setImageResource(R.drawable.micro_selected);
        } else {
            micro.setImageResource(R.drawable.micro_default);
        }
//		LinphoneManager.getLc().sendDtmf('A');
    }

    private void toggleSpeaker() {
        isSpeakerEnabled = !isSpeakerEnabled;
        if (isSpeakerEnabled) {
            LinphoneManager.getInstance().routeAudioToSpeaker();
            speaker.setImageResource(R.drawable.speaker_selected);
            LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);
        } else {
            Log.d("Toggle speaker off, routing back to earpiece");
            LinphoneManager.getInstance().routeAudioToReceiver();
            speaker.setImageResource(R.drawable.speaker_default);
        }
    }

    public void pauseOrResumeCall(LinphoneCall call) {
        LinphoneCore lc = LinphoneManager.getLc();
        if (call != null && LinphoneManager.getLc().getCurrentCall() == call) {
            lc.pauseCall(call);
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
                isVideoCallPaused = true;
            }
            pause.setImageResource(R.drawable.pause_big_over_selected);
        } else if (call != null) {
            if (call.getState() == State.Paused) {
                lc.resumeCall(call);
                if (isVideoCallPaused) {
                    isVideoCallPaused = false;
                }
                pause.setImageResource(R.drawable.pause_big_default);
            }
        }
    }


    //当前APP暂停回到主页面 add by louzf 2016-11-15
    private void AppBackground()
    {
        ComponentName cn = new ComponentName("com.yview.yv00adbgui", "com.yview.yv00adbgui.yv00mainmenuActivity");
//        Intent intent = new Intent();
//        intent.setComponent(cn);
//        startActivity(intent);

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        //intent.setComponent(cn);
        startActivity(intent);
    }

    private void SendExitBra()
    {
        Intent broadIntent = new Intent("android.yview.LinPhoneExit");
        sendBroadcast(broadIntent);
    }


    private AudioManager audioManager;
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void hangUp(boolean isSalence) {
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        boolean isBackgroundModeActive = LinphonePreferences.instance().isBackgroundModeEnabled();
        if (!isBackgroundModeActive) {
            if (currentCall != null) {
                lc.terminateCall(currentCall);
            } else if (lc.isInConference()) {
                lc.terminateConference();
            } else {
                lc.terminateAllCalls();
            }
            lc.terminateAllCalls();

            LinphoneManager.getInstance().changeStatusToOffline();
            buddyList.setVisibility(View.INVISIBLE);
            buddyList.setEnabled(false);
            SendExitBra();
            finishAffinity();
            stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        }
        else {
            AppBackground();
        }
/*
        if (isSalence) {
            audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
            audioManager.abandonAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {

                }
            });
        }
*/

    }


    private class MyQuitReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            LinphonePreferences.instance().setBackgroundModeEnabled(false);
            hangUp(true);
        }
    }

    public void displayVideoCall(boolean display) {
        if (display) {
            showStatusBar();
            mControlsLayout.setVisibility(View.VISIBLE);
            mActiveCallHeader.setVisibility(View.VISIBLE);
            callInfo.setVisibility(View.VISIBLE);
            avatar_layout.setVisibility(View.GONE);
            callsList.setVisibility(View.VISIBLE);
            if (cameraNumber > 1) {
                switchCamera.setVisibility(View.VISIBLE);
            }
        } else {
            hideStatusBar();
            mControlsLayout.setVisibility(View.GONE);
            mActiveCallHeader.setVisibility(View.GONE);
            switchCamera.setVisibility(View.GONE);
            callsList.setVisibility(View.GONE);
        }
    }


    public void displayVideoCallControlsIfHidden() {
        if (mControlsLayout != null) {
            if (mControlsLayout.getVisibility() != View.VISIBLE) {
                if (isAnimationDisabled) {
                    displayVideoCall(true);
                } else {
                    Animation animation = slideInBottomToTop;
                    animation.setAnimationListener(new AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            displayVideoCall(true);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            animation.setAnimationListener(null);
                        }
                    });
                    mControlsLayout.startAnimation(animation);
                    if (cameraNumber > 1) {
                        switchCamera.startAnimation(slideInTopToBottom);
                    }
                }
            }
            resetControlsHidingCallBack();
        }
    }

    public void resetControlsHidingCallBack() {
        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;

        if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && mControlsHandler != null) {
            mControlsHandler.postDelayed(mControls = new Runnable() {
                public void run() {
                    hideNumpad();

                    if (isAnimationDisabled) {
                        video.setEnabled(true);
                        transfer.setVisibility(View.INVISIBLE);
                        addCall.setVisibility(View.INVISIBLE);
                        displayVideoCall(false);
                        numpad.setVisibility(View.GONE);
                        options.setImageResource(R.drawable.options_default);
                    } else {
                        Animation animation = slideOutTopToBottom;
                        animation.setAnimationListener(new AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {
                                video.setEnabled(false); // HACK: Used to avoid controls from being hided if video is switched while controls are hiding
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {
                            }

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                video.setEnabled(true); // HACK: Used to avoid controls from being hided if video is switched while controls are hiding
                                transfer.setVisibility(View.INVISIBLE);
                                addCall.setVisibility(View.INVISIBLE);
                                displayVideoCall(false);
                                numpad.setVisibility(View.GONE);
                                options.setImageResource(R.drawable.options_default);
                                animation.setAnimationListener(null);
                            }
                        });
                        mControlsLayout.startAnimation(animation);
                        if (cameraNumber > 1) {
                            switchCamera.startAnimation(slideOutBottomToTop);
                        }
                    }
                }
            }, SECONDS_BEFORE_HIDING_CONTROLS);
        }
    }

    public void removeCallbacks() {
        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;
    }

    private void hideNumpad() {
        if (numpad == null || numpad.getVisibility() != View.VISIBLE) {
            return;
        }

        dialer.setImageResource(R.drawable.footer_dialer);
        if (isAnimationDisabled) {
            numpad.setVisibility(View.GONE);
        } else {
            Animation animation = slideOutTopToBottom;
            animation.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    numpad.setVisibility(View.GONE);
                    animation.setAnimationListener(null);
                }
            });
            numpad.startAnimation(animation);
        }
    }

    private void hideOrDisplayNumpad() {
        if (numpad == null) {
            return;
        }

        if (numpad.getVisibility() == View.VISIBLE) {
            hideNumpad();
        } else {
            dialer.setImageResource(R.drawable.dialer_alt_back);
            if (isAnimationDisabled) {
                numpad.setVisibility(View.VISIBLE);
            } else {
                Animation animation = slideInBottomToTop;
                animation.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        numpad.setVisibility(View.VISIBLE);
                        animation.setAnimationListener(null);
                    }
                });
                numpad.startAnimation(animation);
            }
        }
    }

    private void hideAnimatedPortraitCallOptions() {
        Animation animation = slideOutLeftToRight;
        animation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (isTransferAllowed) {
                    transfer.setVisibility(View.INVISIBLE);
                }
                addCall.setVisibility(View.INVISIBLE);
                animation.setAnimationListener(null);
            }
        });
        if (isTransferAllowed) {
            transfer.startAnimation(animation);
        }
        addCall.startAnimation(animation);
    }

    private void hideAnimatedLandscapeCallOptions() {
        Animation animation = slideOutTopToBottom;
        if (isTransferAllowed) {
            animation.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    transfer.setAnimation(null);
                    transfer.setVisibility(View.INVISIBLE);
                    animation = AnimationUtils.loadAnimation(CallActivity.this, R.anim.slide_out_top_to_bottom); // Reload animation to prevent transfer button to blink
                    animation.setAnimationListener(new AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            addCall.setVisibility(View.INVISIBLE);
                        }
                    });
                    addCall.startAnimation(animation);
                }
            });
            transfer.startAnimation(animation);
        } else {
            animation.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    addCall.setVisibility(View.INVISIBLE);
                }
            });
            addCall.startAnimation(animation);
        }
    }

    private void showAnimatedPortraitCallOptions() {
        Animation animation = slideInRightToLeft;
        animation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                options.setBackgroundResource(R.drawable.options_default);
                if (isTransferAllowed) {
                    transfer.setVisibility(View.VISIBLE);
                }
                addCall.setVisibility(View.VISIBLE);
                animation.setAnimationListener(null);
            }
        });
        if (isTransferAllowed) {
            transfer.startAnimation(animation);
        }
        addCall.startAnimation(animation);
    }

    private void showAnimatedLandscapeCallOptions() {
        Animation animation = slideInBottomToTop;
        animation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                addCall.setAnimation(null);
                options.setBackgroundResource(R.drawable.options_default);
                addCall.setVisibility(View.VISIBLE);
                if (isTransferAllowed) {
                    animation.setAnimationListener(new AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            transfer.setVisibility(View.VISIBLE);
                        }
                    });
                    transfer.startAnimation(animation);
                }
            }
        });
        addCall.startAnimation(animation);
    }

    private void hideOrDisplayAudioRoutes() {
        if (routeSpeaker.getVisibility() == View.VISIBLE) {
            routeSpeaker.setVisibility(View.GONE);
            routeBluetooth.setVisibility(View.GONE);
            routeEarpiece.setVisibility(View.GONE);
        } else {
            routeSpeaker.setVisibility(View.VISIBLE);
            routeBluetooth.setVisibility(View.VISIBLE);
            routeEarpiece.setVisibility(View.VISIBLE);
        }
    }

    private void hideOrDisplayCallOptions() {
        boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        //Hide options
        if (addCall.getVisibility() == View.VISIBLE) {
            options.setImageResource(R.drawable.options_default);
            if (isAnimationDisabled) {
                if (isTransferAllowed) {
                    transfer.setVisibility(View.INVISIBLE);
                }
                addCall.setVisibility(View.INVISIBLE);
                conference.setVisibility(View.INVISIBLE);
            } else {
                if (isOrientationLandscape) {
                    hideAnimatedLandscapeCallOptions();
                } else {
                    hideAnimatedPortraitCallOptions();
                }
            }
            //Display options
        } else {
            if (isAnimationDisabled) {
                if (isTransferAllowed) {
                    transfer.setVisibility(View.VISIBLE);
                }
                addCall.setVisibility(View.VISIBLE);
                conference.setVisibility(View.VISIBLE);
                options.setImageResource(R.drawable.options_selected);
            } else {
                if (isOrientationLandscape) {
                    showAnimatedLandscapeCallOptions();
                } else {
                    showAnimatedPortraitCallOptions();
                }
            }
            transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
        }
    }

    public void goBackToDialer() {
        Intent intent = new Intent();
        intent.putExtra("Transfer", false);
        setResult(Activity.RESULT_FIRST_USER, intent);
        Log.i("goBackToDialer:----------------------->");
        finish();
    }

    private void goBackToDialerAndDisplayTransferButton() {
        Intent intent = new Intent();
        intent.putExtra("Transfer", true);
        setResult(Activity.RESULT_FIRST_USER, intent);
        Log.i("goBackToDialerAndDisplayTransferButton:-------------");
        finish();
    }

    private void goToChatList() {
        Intent intent = new Intent();
        intent.putExtra("chat", true);
        setResult(Activity.RESULT_FIRST_USER, intent);
        Log.i("goToChatList:-----------------------------");
        finish();
    }

    public void acceptCallUpdate(boolean accept) {
        if (timer != null) {
            timer.cancel();
        }
        Log.i("acceptCallUpdate:----------------------->");
        LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }

        LinphoneCallParams params = call.getCurrentParamsCopy();
        if (accept) {
            params.setVideoEnabled(true);
            LinphoneManager.getLc().enableVideo(true, true);
        }

        try {
            LinphoneManager.getLc().acceptCallUpdate(call, params);
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    public void startIncomingCallActivity() {

        startActivity(new Intent(this, CallIncomingActivity.class));
    }

    public void hideStatusBar() {
        if (isTablet()) {
            return;
        }

        findViewById(R.id.status).setVisibility(View.GONE);
        findViewById(R.id.fragmentContainer).setPadding(0, 0, 0, 0);
    }

    public void showStatusBar() {
        if (isTablet()) {
            return;
        }

        if (status != null && !status.isVisible()) {
            // Hack to ensure statusFragment is visible after coming back to
            // dialer from chat
            status.getView().setVisibility(View.VISIBLE);
        }
        findViewById(R.id.status).setVisibility(View.VISIBLE);
        //findViewById(R.id.fragmentContainer).setPadding(0, LinphoneUtils.pixelsToDpi(getResources(), 40), 0, 0);
    }


    private void showAcceptCallUpdateDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Drawable d = new ColorDrawable(getResources().getColor(R.color.colorC));
        d.setAlpha(200);
        dialog.setContentView(R.layout.dialog);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(d);

        TextView customText = (TextView) dialog.findViewById(R.id.customText);
        customText.setText(getResources().getString(R.string.add_video_dialog));
        Button delete = (Button) dialog.findViewById(R.id.delete_button);
        delete.setText(R.string.accept);
        Button cancel = (Button) dialog.findViewById(R.id.cancel);
        cancel.setText(R.string.decline);

        delete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (CallActivity.isInstanciated()) {
                    Log.d("Call Update Accepted");
                    CallActivity.instance().acceptCallUpdate(true);
                }
                dialog.dismiss();
            }
        });

        cancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (CallActivity.isInstanciated()) {
                    Log.d("Call Update Denied");
                    CallActivity.instance().acceptCallUpdate(false);
                }
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    @Override
    protected void onResume() {
        instance = this;
        super.onResume();


        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }

        refreshIncallUi();
        handleViewIntent();

        if (!isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
            mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
            removeCallbacks();
        }
        for (LinphoneCall call : LinphoneManager.getLcIfManagerNotDestroyedOrNull().getCalls()) {
            if (!call.getRemoteAddress().asStringUriOnly().contains("sip:3100")) {
                speakToAll.setEnabled(true);
            } else {
                speakToAll.setEnabled(false);
            }
        }
//        startService(new Intent(this, PlayerService.class));
        if (LinphoneService.instance().isSpeaking()) {
            speakRequest.setPressed(true);
            LinphoneManager.getLcIfManagerNotDestroyedOrNull().muteMic(false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

    }

    private void handleViewIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.getAction() == "android.intent.action.VIEW") {
            LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
            if (call != null && isVideoEnabled(call)) {
                LinphonePlayer player = call.getPlayer();
                String path = intent.getData().getPath();
                Log.i("Openning " + path);
                int openRes = player.open(path, new LinphonePlayer.Listener() {

                    @Override
                    public void endOfFile(LinphonePlayer player) {
                        player.close();
                    }
                });
                if (openRes == -1) {
                    String message = "Could not open " + path;
                    Log.e(message);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.i("Start playing");
                if (player.start() == -1) {
                    player.close();
                    String message = "Could not start playing " + path;
                    Log.e(message);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        super.onPause();

        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;

        if (!isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
//		LinphoneManager.getInstance().changeStatusToOnline();

        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;
        mControlsHandler = null;

        mSensorManager.unregisterListener(this);

        unbindDrawables(findViewById(R.id.topLayout));
        instance = null;
        super.onDestroy();
        System.gc();
    }

    private void unbindDrawables(View view) {
        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ImageView) {
            view.setOnClickListener(null);
        }
        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
//        if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        Log.i("keycode@@@@@@@@@@@@@@@@@@@@@@@@@@"+keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                Log.i("back@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
                LinphonePreferences.instance().setBackgroundModeEnabled(true);
                hangUp(false);
                break;
            case KeyEvent.KEYCODE_HEADSETHOOK:
/*
                count++;
                //申请AUDIOFOCUS
                AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                        if (count % 2 == 1) {
                            MediaPlayer mp = MediaPlayer.create(CallActivity.this, R.raw.start);
                            Log.i("mp:@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@"+mp);
                            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    mp.stop();
                                    mp.release();
                                }
                            });
                            mp.start();

                            //发送一个'对讲'按钮被按下的事件
                            MotionEvent motionEvent = MotionEvent.obtain(SystemClock.uptimeMillis(),
                                    SystemClock.uptimeMillis()+100,
                                    MotionEvent.ACTION_DOWN,
                                    speakRequest.getX()+10,
                                    speakRequest.getY()+10,
                                    0);
                            speakRequest.dispatchTouchEvent(motionEvent);

                        }
                        else {
                            MediaPlayer mp = MediaPlayer.create(CallActivity.this, R.raw.stop);
                            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    mp.stop();
                                    mp.release();
                                }
                            });
                            mp.start();
                            Log.i("stop@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
                            //发送一个'对讲'按钮被放开的事件
                            MotionEvent motionEvent = MotionEvent.obtain(SystemClock.uptimeMillis(),
                                    SystemClock.uptimeMillis()+100,
                                    MotionEvent.ACTION_UP,
                                    speakRequest.getX()+10,
                                    speakRequest.getY()+10,
                                    4);
                            speakRequest.dispatchTouchEvent(motionEvent);
                        }
                    }
                }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
*/

                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void bindAudioFragment(CallAudioFragment fragment) {
        audioCallFragment = fragment;
    }

    public void bindVideoFragment(CallVideoFragment fragment) {
        videoCallFragment = fragment;
    }


    //CALL INFORMATION
    private void displayCurrentCall(LinphoneCall call) {
        LinphoneAddress lAddress = call.getRemoteAddress();
        for (int i=0; i<names.length-1; i++) {
            if (lAddress.asStringUriOnly().contains(caller_id_numbers[i])) {
                lAddress.setDisplayName(names[i]);
            }
        }
        TextView contactName = (TextView) findViewById(R.id.current_contact_name);
        setContactInformation(contactName, contactPicture, lAddress);
        registerCallDurationTimer(null, call);
    }

    private void displayPausedCalls(Resources resources, final LinphoneCall call, int index) {
        // Control Row
        LinearLayout callView;

        if (call == null) {
            callView = (LinearLayout) inflater.inflate(R.layout.conference_paused_row, container, false);
            callView.setId(index + 1);
            callView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    pauseOrResumeConference();
                }
            });
        } else {
            callView = (LinearLayout) inflater.inflate(R.layout.call_inactive_row, container, false);
            callView.setId(index + 1);

            TextView contactName = (TextView) callView.findViewById(R.id.contact_name);
            ImageView contactImage = (ImageView) callView.findViewById(R.id.contact_picture);

            LinphoneAddress lAddress = call.getRemoteAddress();
            setContactInformation(contactName, contactImage, lAddress);
            displayCallStatusIconAndReturnCallPaused(callView, call);
            registerCallDurationTimer(callView, call);
        }
        callsList.addView(callView);
    }

    private void setContactInformation(TextView contactName, ImageView contactPicture, LinphoneAddress lAddress) {
        Contact lContact = ContactsManager.getInstance().findContactWithAddress(contactName.getContext().getContentResolver(), lAddress);
        lContact = null;    //lifeng:不知道如何删除先前存储的联系人,这里只好强制为NULL
        if (lContact == null) {
            if (lAddress.asStringUriOnly().contains("sip:3100")) {
                contactName.setText("群组对讲");
            } else {
                contactName.setText(LinphoneUtils.getAddressDisplayName(lAddress));
            }
            if (contactPicture != null) {
                contactPicture.setImageResource(R.drawable.avatar);
            }

        } else {
            contactName.setText(lContact.getName());
            if (contactPicture != null) {
                LinphoneUtils.setImagePictureFromUri(contactPicture.getContext(), contactPicture, lContact.getPhotoUri(), lContact.getThumbnailUri());
            }
        }
    }

    private boolean displayCallStatusIconAndReturnCallPaused(LinearLayout callView, LinphoneCall call) {
        boolean isCallPaused, isInConference;
        ImageView callState = (ImageView) callView.findViewById(R.id.call_pause);
        callState.setTag(call);
        callState.setOnClickListener(this);

        if (call.getState() == State.Paused || call.getState() == State.PausedByRemote || call.getState() == State.Pausing) {
            callState.setImageResource(R.drawable.pause);
            isCallPaused = true;
            isInConference = false;
        } else if (call.getState() == State.OutgoingInit || call.getState() == State.OutgoingProgress || call.getState() == State.OutgoingRinging) {
            isCallPaused = false;
            isInConference = false;
        } else {
            isInConference = isConferenceRunning && call.isInConference();
            isCallPaused = false;
        }

        return isCallPaused || isInConference;
    }

    private void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != State.StreamsRunning) {
            return;
        }

        Chronometer timer;
        if (v == null) {
            timer = (Chronometer) findViewById(R.id.current_call_timer);
        } else {
            timer = (Chronometer) v.findViewById(R.id.call_timer);
        }

        if (timer == null) {
            throw new IllegalArgumentException("no callee_duration view found");
        }

        timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
        timer.start();
    }

    public void refreshCallList(Resources resources) {
        isConferenceRunning = LinphoneManager.getLc().isInConference();

        //MultiCalls
        if (LinphoneManager.getLc().getCallsNb() > 1) {
            callsList.setVisibility(View.VISIBLE);
        }

        //Active call
        if (LinphoneManager.getLc().getCurrentCall() != null) {
            displayNoCurrentCall(false);
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && !isConferenceRunning) {
                displayVideoCall(false);
            } else {
                displayAudioCall();
            }
        } else if (LinphoneManager.getLcIfManagerNotDestroyedOrNull().getCalls() == null){
            showAudioView();
            displayNoCurrentCall(true);
            if (LinphoneManager.getLc().getCallsNb() == 1) {
                callsList.setVisibility(View.VISIBLE);
            }
        }

        //Conference
        if (isConferenceRunning) {
            displayConference(true);
        } else {
            displayConference(false);
        }

        if (callsList != null) {
            callsList.removeAllViews();
            int index = 0;

            if (LinphoneManager.getLc().getCallsNb() == 0) {
                goBackToDialer();
                return;
            }

            boolean isConfPaused = false;
            for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
                if (call.isInConference() && !isConferenceRunning) {
                    isConfPaused = true;
                    index++;
                } else {
                    if (call != LinphoneManager.getLc().getCurrentCall() && !call.isInConference()) {
//						displayPausedCalls(resources, call, index);
                        index++;
                    } else {
                        displayCurrentCall(call);
                    }
                }
            }

            if (!isConferenceRunning) {
                if (isConfPaused) {
                    callsList.setVisibility(View.VISIBLE);
//					displayPausedCalls(resources, null, index);
                }

            }

        }

        //Paused by remote
        List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(LinphoneManager.getLc(), Arrays.asList(State.PausedByRemote));
        if (pausedCalls.size() == 1) {
            displayCallPaused(true);
        } else {
            displayCallPaused(false);
        }


    }

    //Conference
    private void exitConference(final LinphoneCall call) {
        LinphoneCore lc = LinphoneManager.getLc();

        if (call.isInConference()) {
            lc.removeFromConference(call);
            if (lc.getConferenceSize() <= 1) {
                lc.leaveConference();
            }
        }
        refreshIncallUi();
    }

    private void enterConference() {
        LinphoneManager.getLc().addAllToConference();
    }

    public void pauseOrResumeConference() {
        LinphoneCore lc = LinphoneManager.getLc();
        if (lc.isInConference()) {
            conferenceStatus.setImageResource(R.drawable.pause_big_over_selected);
            lc.leaveConference();
        } else {
            conferenceStatus.setImageResource(R.drawable.pause_big_default);
            lc.enterConference();
        }
        refreshCallList(getResources());
    }

    private void displayConferenceParticipant(int index, final LinphoneCall call) {
        LinearLayout confView = (LinearLayout) inflater.inflate(R.layout.conf_call_control_row, container, false);
        conferenceList.setId(index + 1);
        TextView contact = (TextView) confView.findViewById(R.id.contactNameOrNumber);

        Contact lContact = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), call.getRemoteAddress());
        if (lContact == null) {
            contact.setText(call.getRemoteAddress().getUserName());
        } else {
            contact.setText(lContact.getName());
        }

        registerCallDurationTimer(confView, call);

        ImageView quitConference = (ImageView) confView.findViewById(R.id.quitConference);
        quitConference.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                exitConference(call);
            }
        });
        conferenceList.addView(confView);

    }

    private void displayConferenceHeader() {
        conferenceList.setVisibility(View.VISIBLE);
        RelativeLayout headerConference = (RelativeLayout) inflater.inflate(R.layout.conference_header, container, false);
        conferenceStatus = (ImageView) headerConference.findViewById(R.id.conference_pause);
        conferenceStatus.setOnClickListener(this);
        conferenceList.addView(headerConference);

    }

    private void displayConference(boolean isInConf) {
        if (isInConf) {
            mControlsLayout.setVisibility(View.VISIBLE);
            mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
            mActiveCallHeader.setVisibility(View.GONE);
            mNoCurrentCall.setVisibility(View.GONE);
            conferenceList.removeAllViews();

            //Conference Header
            displayConferenceHeader();

            //Conference participant
            int index = 1;
            for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
                if (call.isInConference()) {
                    displayConferenceParticipant(index, call);
                    index++;
                }
            }
            conferenceList.setVisibility(View.VISIBLE);
        } else {
            conferenceList.setVisibility(View.GONE);
        }
    }

    public static Boolean isProximitySensorNearby(final SensorEvent event) {
        float threshold = 4.001f; // <= 4 cm is near

        final float distanceInCm = event.values[0];
        final float maxDistance = event.sensor.getMaximumRange();
        Log.d("Proximity sensor report [", distanceInCm, "] , for max range [", maxDistance, "]");

        if (maxDistance <= threshold) {
            // Case binary 0/1 and short sensors
            threshold = maxDistance;
        }
        return distanceInCm < threshold;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
/*
        if (event.timestamp == 0) return;
        if (isProximitySensorNearby(event)) {
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } else {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
*/
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String curCallAddress, newCallAddress = null;
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        curCallAddress = currentCall.getRemoteAddress().asStringUriOnly();
//        ImageView friendStatus = (ImageView) view.findViewById(R.id.friendStatus);
//        for (String numberOrAddress : contact.getNumbersOrAddresses()) {
//            if (numberOrAddress.contains("@")) {
                Log.i("lc.getRemoteAddress().asStringUriOnly():============>" + lc.getRemoteAddress().asStringUriOnly());
                if (view.getTag() == PresenceActivityType.Online) {
                    newCallAddress = "sip:" + caller_id_numbers[position] + "@" + domain;
//                    break;
                } else if (view.getTag() == PresenceActivityType.Offline) {
                    displayCustomToast("此人不在线", Toast.LENGTH_SHORT);
                    return;
                } else if (view.getTag() == PresenceActivityType.OnThePhone) {
                    newCallAddress = "sip:" + caller_id_numbers[position] + "@" + domain;
                    Log.i("curCallAddress, newCallAddress:------------------------>" + curCallAddress, newCallAddress);
                    if (curCallAddress.equals(newCallAddress)) //{
                        displayCustomToast("正在与此人通话...", Toast.LENGTH_SHORT);
//                    } else
//                        displayCustomToast("此人可能正在成员对讲", Toast.LENGTH_SHORT);
//                    return;
                } else {
                    displayCustomToast("此人无法接通", Toast.LENGTH_SHORT);
                    return;
                }
//            }
//        }
//        if (!curCallAddress.equals(newCallAddress) && !curCallAddress.contains("sip:3100")) {
            Log.i("curCallAddress1:-------------------------------------->" + newCallAddress);
            lc.terminateCall(currentCall);
//        }
        if (!speakToAll.isEnabled())
            speakToAll.setEnabled(true);
        view.setTag(PresenceActivityType.OnThePhone);
//        LinphoneManager.getInstance().changeStatusToOnThePhone();
        LinphoneManager.getInstance().newOutgoingCall(newCallAddress, null);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        buddyList.getAdapter();
        Contact contact = (Contact) parent.getItemAtPosition(position);
        for (String numberOrAddress : contact.getNumbersOrAddresses()) {
            if (numberOrAddress.contains("@")) {
                LinphoneActivity.instance().editContact(contact, numberOrAddress);
            }
        }
        return false;
    }
}
