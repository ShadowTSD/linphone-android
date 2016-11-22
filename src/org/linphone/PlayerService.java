package org.linphone;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;

import org.linphone.mediastream.Log;

/**
 * Created by lifeng on 16/10/28.
 */
public class PlayerService extends Service {
    private MediaSessionCompat session;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        super.onCreate();

        ComponentName mbr = new ComponentName(getPackageName(),
                PlayerService.class.getName());
        session = new MediaSessionCompat(this, "lifeng", mbr, null);
/*
        */
/* set flags to handle media buttons *//*

        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

		*/
/* this is need after Lolipop *//*

        session.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                    return super.onMediaButtonEvent(intent);
                }

                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null || event.getAction() != KeyEvent.ACTION_UP) {
                    return super.onMediaButtonEvent(intent);
                }
                Log.i("!!!!!!!!!!!!!!!!!!!!!!!@@@@@@@@@@@@@:event"+event.getAction());

                // do something

                return true;
            }
        });

		*/
/* to make sure the media session is active *//*

        if (!session.isActive()) {
            session.setActive(true);
        }
*/
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("service success@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//        android.support.v4.media.session.MediaButtonReceiver.handleIntent(session, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDestroy() {
        super.onDestroy();
        session.release();
    }
}
