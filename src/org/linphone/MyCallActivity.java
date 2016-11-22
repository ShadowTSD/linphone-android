package org.linphone;

//import android.support.v7.app.AppCompatActivity;
import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import org.linphone.core.LinphoneCore;

public class MyCallActivity extends Activity {
    private Button speakRequest;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_call);
        speakRequest = (Button)findViewById(R.id.btn_speak);
        speakRequest.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    LinphoneCore lc = LinphoneManager.getLc();
                    lc.muteMic(false);
                    lc.sendDtmf('C');	//'C' 表示申请对讲
                }
                else if (event.getAction() == MotionEvent.ACTION_UP) {
                    LinphoneCore lc = LinphoneManager.getLc();
                    lc.muteMic(true);
                    lc.sendDtmf('D'); //'D' 表示'对讲完毕'
                }
                return true;
            }
        });
    }
}
