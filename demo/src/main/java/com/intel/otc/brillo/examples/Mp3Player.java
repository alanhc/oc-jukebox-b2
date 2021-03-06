package com.intel.otc.brillo.examples;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Mp3Player implements Runnable,
        Visualizer.OnDataCaptureListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {
    private static final String TAG = Mp3Player.class.getSimpleName();

    private Context mContext;
    private SongsManager sm;
    private int currentSongIndex = 0;
    private AudioManager am;
    private MediaPlayer mp;
    private Visualizer visualizer;
    public enum MediaState {
        Idle, Playing, Paused
    }
    private MediaState mState;
    private List<OnMediaStateChangeListener> mStateChangeListeners = new LinkedList<>();
    private List<OnVisualizerDataListener> mVisualizerDataListeners = new LinkedList<>();
    private int volumeBeforeMute;

    public interface OnMediaStateChangeListener {
        void onMediaStateChanged(MediaState state);
    }

    public interface OnVisualizerDataListener {
        void onVisualizerDataCaptured(byte[] data);
    }

    public Mp3Player(Context context) {
        mContext = context;
    }

    @Override
    public void run() {
        Log.d(TAG, "Initialize MP3 player...");
        // Set the current Thread priority as standard audio threads
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        // Get an instance of AudioManager for volume control
        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        sm = new SongsManager();
        mp = new MediaPlayer();
        mp.setOnCompletionListener(this);
        mp.setOnPreparedListener(this);
        visualizer = new Visualizer(mp.getAudioSessionId());
        visualizer.setCaptureSize(visualizer.getCaptureSizeRange()[0]);
        visualizer.setDataCaptureListener(this, Visualizer.getMaxCaptureRate() / 2, false, true);
        visualizer.setEnabled(false);
        setMediaState(MediaState.Idle);
    }

    @Override
    public void onCompletion(MediaPlayer player) {
        setMediaState(MediaState.Idle);
        if (++currentSongIndex < sm.size())
            Play();
        else currentSongIndex = 0;
    }

    @Override
    public void onPrepared(MediaPlayer player) {
        mp.start();
        visualizer.setEnabled(true);
        setMediaState(MediaState.Playing);
    }

    public void Play() {
        switch (mState) {
            case Idle:
                playSong(currentSongIndex);
                break;
            case Playing:
                mp.pause();
                setMediaState(MediaState.Paused);
                break;
            case Paused:
                mp.start();
                setMediaState(MediaState.Playing);
                break;
        }
    }

    public void Play(int index) {
        if (0 <= index && index < sm.size()) {
            if (mState != MediaState.Idle && index != currentSongIndex)
                Stop();
            currentSongIndex = index;
            Play();
        }
    }

    public void Stop() {
        if (mState != MediaState.Idle) {
            visualizer.setEnabled(false);
            mp.stop();
            setMediaState(MediaState.Idle);
        }
    }

    public boolean isMuted() {
        return am.isStreamMute(AudioManager.STREAM_MUSIC);
    }

    public void mute() {
        if (!isMuted()) {
            volumeBeforeMute = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        }
    }

    public void unmute() {
        if (isMuted()) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeMute, 0);
        }
    }

    public int getMaxVolume() {
        return am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public int getCurrentVolume() {
        return am.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public void setVolume(int volume) {
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    }

    private synchronized void setMediaState(MediaState newState) {
        mState = newState;
        for (OnMediaStateChangeListener listener : mStateChangeListeners)
            listener.onMediaStateChanged(newState);
    }

    public void subscribeStateChangeNotification(OnMediaStateChangeListener listener) {
        mStateChangeListeners.add(listener);
    }

    public void unsubscribeStateChangeNotification(OnMediaStateChangeListener listener) {
        mStateChangeListeners.remove(listener);
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
        for (OnVisualizerDataListener listener : mVisualizerDataListeners)
            listener.onVisualizerDataCaptured(fft);
    }

    public void subscribeVisualizerData(OnVisualizerDataListener listener) {
        mVisualizerDataListeners.add(listener);
    }

    public void unsubscribeVisualizerData(OnVisualizerDataListener listener) {
        mVisualizerDataListeners.remove(listener);
    }

    public MediaState getCurrentState() {
        return mState;
    }

    public String getCurrentTitle() {
        return (mState != MediaState.Idle)? sm.getSongTitle(currentSongIndex) : null;
    }

    public String[] getPlayList() {
        String[] titles = new String[sm.size()];
        for (int i = 0; i < sm.size(); i++)
            titles[i] = sm.getSongTitle(i);
        return titles;
    }

    private void playSong(int index) {
        try {
            Log.d(TAG, "Playing " + sm.getSongTitle(index));
            mp.reset();
            mp.setDataSource(sm.getSongPath(index));
            mp.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }
}
