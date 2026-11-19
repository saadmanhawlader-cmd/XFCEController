package com.xfcecontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private WebSocketClient socket;
    private TextView status;
    private String host = "192.168.0.119";
    private String wsPort = "8765";
    private String videoUrl = "udp://@:1234";
    private String token = "";
    private float sensitivity = 1.7f;
    private float lastX, lastY;
    private boolean pointerActive;
    private final Handler handler = new Handler();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        startVideo();
        connectSocket();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        videoLayout = new VLCVideoLayout(this);
        root.addView(videoLayout, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(14, 10, 14, 10);
        panel.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        root.addView(panel, pp);

        status = new TextView(this);
        status.setTextColor(0xffffffff);
        status.setText("Video starting • Remote disconnected");
        panel.addView(status);

        LinearLayout row = new LinearLayout(this);
        panel.addView(row);
        addButton(row, "⚙", v -> showSettings());
        addButton(row, "L click", v -> send("{\"action\":\"click\",\"button\":1}"));
        addButton(row, "R click", v -> send("{\"action\":\"click\",\"button\":3}"));
        addButton(row, "↑", v -> send("{\"action\":\"scroll\",\"direction\":\"up\"}"));
        addButton(row, "↓", v -> send("{\"action\":\"scroll\",\"direction\":\"down\"}"));

        TextView pad = new TextView(this);
        pad.setText("Touch here to move pointer");
        pad.setTextColor(0xffffffff);
        pad.setGravity(Gravity.CENTER);
        pad.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(360, 210, Gravity.BOTTOM | Gravity.RIGHT);
        tp.setMargins(0, 0, 20, 20);
        root.addView(pad, tp);
        pad.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX(); lastY = e.getY(); pointerActive = true; return true;
                case MotionEvent.ACTION_MOVE:
                    if (pointerActive) {
                        float dx = (e.getX() - lastX) * sensitivity;
                        float dy = (e.getY() - lastY) * sensitivity;
                        lastX = e.getX(); lastY = e.getY();
                        send(String.format(java.util.Locale.US,
                            "{\"action\":\"move\",\"dx\":%.1f,\"dy\":%.1f}", dx, dy));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    pointerActive = false; return true;
            }
            return true;
        });
        setContentView(root);
    }

    private interface Click { void run(View v); }
    private void addButton(LinearLayout row, String label, Click click) {
        Button b = new Button(this); b.setText(label); b.setFocusable(true);
        b.setOnClickListener(click::run); row.addView(b);
    }

    private void startVideo() {
        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--network-caching=120");
            options.add("--live-caching=120");
            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.attachViews(videoLayout, null, false, false);
            Media media = new Media(libVLC, UriHelper.parse(videoUrl));
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
        } catch (Exception e) {
            Toast.makeText(this, "Video error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void connectSocket() {
        try {
            URI uri = new URI("ws://" + host + ":" + wsPort);
            socket = new WebSocketClient(uri) {
                @Override public void onOpen(ServerHandshake h) {
                    send("{\"token\":\"" + escape(token) + "\"}");
                    runOnUiThread(() -> status.setText("Video active • Remote connected"));
                }
                @Override public void onMessage(String msg) {
                    if (msg.contains("\"connected\":true"))
                        runOnUiThread(() -> status.setText("Video active • Remote ready"));
                }
                @Override public void onClose(int code, String reason, boolean remote) {
                    runOnUiThread(() -> status.setText("Video active • Remote disconnected"));
                }
                @Override public void onError(Exception ex) {
                    runOnUiThread(() -> status.setText("Remote error: " + ex.getMessage()));
                }
            };
            socket.connect();
        } catch (Exception e) {
            status.setText("Remote setup error");
        }
    }

    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private void send(String message) {
        if (socket != null && socket.isOpen()) socket.send(message);
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this); box.setPadding(20, 8, 20, 8);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText ip = field("Phone IP", host); box.addView(ip);
        EditText port = field("WebSocket port", wsPort); box.addView(port);
        EditText key = field("Bridge token", token); box.addView(key);
        EditText stream = field("Video URL", videoUrl); box.addView(stream);
        new AlertDialog.Builder(this).setTitle("Connection settings").setView(box)
            .setNegativeButton("Cancel", null).setPositiveButton("Save & reconnect", (d, w) -> {
                host = ip.getText().toString().trim();
                wsPort = port.getText().toString().trim();
                token = key.getText().toString();
                videoUrl = stream.getText().toString().trim();
                if (socket != null) socket.close();
                connectSocket();
                if (mediaPlayer != null) mediaPlayer.stop();
                if (mediaPlayer != null) mediaPlayer.detachViews();
                if (libVLC != null) libVLC.release();
                startVideo();
            }).show();
    }
    private EditText field(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setText(value); return e;
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        String key = keyName(code);
        if (key != null) { send("{\"action\":\"key\",\"key\":\"" + key + "\"}"); return true; }
        return super.onKeyDown(code, event);
    }
    private String keyName(int c) {
        switch (c) {
            case KeyEvent.KEYCODE_DPAD_UP: return "Up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "Down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "Right";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: return "Return";
            case KeyEvent.KEYCODE_DEL: return "BackSpace";
            case KeyEvent.KEYCODE_SPACE: return "space";
            case KeyEvent.KEYCODE_ESCAPE: return "Escape";
            default:
                if (c >= KeyEvent.KEYCODE_A && c <= KeyEvent.KEYCODE_Z)
                    return String.valueOf((char)('a' + c - KeyEvent.KEYCODE_A));
                if (c >= KeyEvent.KEYCODE_0 && c <= KeyEvent.KEYCODE_9)
                    return String.valueOf((char)('0' + c - KeyEvent.KEYCODE_0));
                return null;
        }
    }

    @Override protected void onDestroy() {
        if (socket != null) socket.close();
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.detachViews(); mediaPlayer.release(); }
        if (libVLC != null) libVLC.release();
        super.onDestroy();
    }
}
