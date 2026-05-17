package li.lingfeng.ltweaks.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.Result;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.utils.ZXingUtils;
import me.dreamvoid.mtweaks.R;

import java.util.regex.Matcher;

/**
 * Created by smallville on 2017/2/1.
 */

public class QrCodeActivity extends AppCompatActivity {

    private ProgressBar mProgressBar;
    private TextView mQrcodeText;
    private Button mWeChatButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().getAction().equals(Intent.ACTION_SEND) || !getIntent().getType().startsWith("image/")) {
            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_qrcode);
        Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mQrcodeText = (TextView) findViewById(R.id.qrcode_text);
        mQrcodeText.setTextIsSelectable(true);
        mQrcodeText.setMovementMethod(LinkMovementMethod.getInstance());

        new DecodeTask().execute(uri);
    }

    private class DecodeTask extends AsyncTask<Uri, Void, Result> {

        @Override
        protected Result doInBackground(Uri... params) {
            return ZXingUtils.decodeQrCode(params[0]);
        }

        @Override
        protected void onPostExecute(Result result) {
            mProgressBar.setVisibility(View.GONE);
            mQrcodeText.setVisibility(View.VISIBLE);
            mWeChatButton.setVisibility(View.VISIBLE);
            if (result == null) {
                mQrcodeText.setText(R.string.share_qrcode_cant_decode);
                mQrcodeText.setTextColor(Color.RED);
            } else {
                String text = result.getText();
                Spannable content = new SpannableString(text);

                Matcher matcher = Patterns.WEB_URL.matcher(text);
                while (matcher.find()) {
                    String url = matcher.group();
                    Logger.d("Got url " + url);
                    content.setSpan(new URLSpan(url), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                mQrcodeText.setText(content);
            }
        }
    }
}
