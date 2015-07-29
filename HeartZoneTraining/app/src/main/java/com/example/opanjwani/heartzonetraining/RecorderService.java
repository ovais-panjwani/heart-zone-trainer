package com.example.opanjwani.heartzonetraining;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.ua.sdk.Ua;
import com.ua.sdk.UaLog;
import com.ua.sdk.datapoint.BaseDataTypes;
import com.ua.sdk.datapoint.DataFrame;
import com.ua.sdk.datapoint.DataTypeRef;
import com.ua.sdk.datasourceidentifier.DataSourceIdentifier;
import com.ua.sdk.recorder.Recorder;
import com.ua.sdk.recorder.RecorderManager;
import com.ua.sdk.recorder.RecorderManagerObserver;
import com.ua.sdk.recorder.RecorderObserver;
import com.ua.sdk.recorder.data.DataFrameObserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.ua.sdk.datapoint.BaseDataTypes.TYPE_HEART_RATE;

public class RecorderService extends Service implements IntervalManager.Listener{

    private RecorderManager recorderManager;
    private Ua ua;
    private MyRecorderManagerObserver recorderManagerObserver;
    private MyDataFrameObserver dataFrameObserver;
    private MyRecorderObserver recorderObserver;
    private boolean started;
    private Recorder recorder;
    private IntervalManager intervalManager;
    private FileOutputStream fileOutputStream;
    private File file;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        UaWrapper uaWrapper = UaWrapper.getInstance();
        ua = uaWrapper.getUa();
        recorderManager = ua.getRecorderManager();
        intervalManager = IntervalManager.getInstance();
        File filedir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        filedir.mkdirs();
        file = new File(filedir, "interval_trainer.txt");
        try {
            fileOutputStream = new FileOutputStream(file, true);
            fileOutputStream.write(("Hello World").getBytes());
        } catch (IOException e) {
            UaLog.error("FileWriter not built.");
        }

        intervalManager.addListener(this);

        recorderManagerObserver = new MyRecorderManagerObserver();
        recorderManager.addRecorderManagerObserver(recorderManagerObserver);
        observeRecorder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recorder != null) {
            recorder.removeDataFrameObserver(dataFrameObserver);
            recorder.removeRecorderObserver(recorderObserver);
        }

        if (recorderManagerObserver != null) {
            recorderManager.removeRecorderManagerObserver(recorderManagerObserver);
        }

        stopForeground(true);
    }

    @Override
    public void onIntervalFinished() {
        if (recorderManager.getRecorder(RecordFragment.SESSION_NAME) != null) {
            recorder.destroy();
        }
        try {
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStateChanged(String state) {
        try {
            fileOutputStream.write((state + "\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void observeRecorder() {
        recorder = recorderManager.getRecorder(RecordFragment.SESSION_NAME);
        if (recorder != null) {
            dataFrameObserver = new MyDataFrameObserver();

            recorder.addDataFrameObserver(dataFrameObserver, TYPE_HEART_RATE.getRef());

            recorderObserver = new MyRecorderObserver();
            recorder.addRecorderObserver(recorderObserver);
        }
    }

    private String formatHeartRate(double heartRate) {
        if (Double.isNaN(heartRate)) {
            return "--- Bpm";
        } else {
            return String.format("%.0f Bpm", heartRate);
        }
    }

    public class MyDataFrameObserver implements DataFrameObserver {
        @Override
        public void onDataFrameUpdated(DataSourceIdentifier dataSourceIdentifier, DataTypeRef dataTypeRef, DataFrame dataFrame) {
            Log.d("###### service", formatHeartRate(dataFrame.getHeartRateDataPoint().getHeartRate()));
            if (dataFrame.isSegmentStarted() && dataTypeRef.equals(BaseDataTypes.TYPE_HEART_RATE.getRef())) {
                try {
                    String data = "" + dataFrame.getActiveTime() + ", " + formatHeartRate(dataFrame.getHeartRateDataPoint().getHeartRate())
                            + "\n";
                    fileOutputStream.write(data.getBytes());
                    fileOutputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }


    private class MyRecorderManagerObserver implements RecorderManagerObserver {

        @Override
        public void onRecorderCreated(String name) {
            if (name.equals(RecordFragment.SESSION_NAME)) {
                observeRecorder();
            }
        }

        @Override
        public void onRecorderDestroyed(String name) {
            if (name.equals(RecordFragment.SESSION_NAME)) {
                stopForeground(true);
            }
        }

        @Override
        public void onRecorderRecovered(String name) {
            if (name.equals(RecordFragment.SESSION_NAME)) {
                observeRecorder();
            }
        }
    }

    private class MyRecorderObserver implements RecorderObserver {

        @Override
        public void onSegmentStateUpdated(DataFrame dataFrame) {
            started = dataFrame.isSegmentStarted();
            if(!started) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    fileOutputStream = new FileOutputStream(file, true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onTimeUpdated(double activeTime, double elapsedTime) {
            // wait for workout to start to show notification
            if (started && !Double.isNaN(activeTime)) {
                intervalManager.onUpdate(activeTime);
            }
        }
    }
}
