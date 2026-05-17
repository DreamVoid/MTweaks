package li.lingfeng.ltweaks.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import li.lingfeng.ltweaks.R;
import li.lingfeng.ltweaks.utils.ShareUtils;

/**
 * Created by smallville on 2017/11/9.
 */

public class SelectableTextActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView mTextView;
    private EditText mEditView;
    private ImageView mCopyButton;
    private ImageView mShareButton;
    private ImageView mEditButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_text_selectable);
        mTextView = (TextView) findViewById(R.id.text);
        mEditView = (EditText) findViewById(R.id.text_edit);
        mCopyButton = (ImageView) findViewById(R.id.copy);
        mShareButton = (ImageView) findViewById(R.id.share);
        mEditButton = (ImageView) findViewById(R.id.edit);

        String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            text = "";
        }
        mTextView.setText(text);
        mTextView.setTextIsSelectable(true);
        mEditView.setText(text);

        mCopyButton.setOnClickListener(this);
        mShareButton.setOnClickListener(this);
        mEditButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.copy) {
            ClipboardManager clipManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipManager.setPrimaryClip(ClipData.newPlainText(null, mEditView.getText()));
        } else if (id == R.id.share) {
            ShareUtils.shareText(this, mEditView.getText().toString());
        } else if (id == R.id.edit) {
            mEditView.setVisibility(View.VISIBLE);
            mTextView.setVisibility(View.GONE);
        }
    }
}
