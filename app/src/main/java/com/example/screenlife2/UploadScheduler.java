package com.example.screenlife2;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;

// UploadScheduler now acts as a bridge to WorkManager for manual triggers and UI status
public class UploadScheduler {
    private static final String TAG = "UploadScheduler";

    public interface UploadListener {
        void onInvoke(UploadStatus uploadStatus, UploadResult uploadResult, UploadData uploadData);
    }

    public enum UploadStatus {
        IDLE,
        UPLOADING
    }

    public enum UploadResult {
        NO_UPLOADS,
        SUCCESS,
        WIFI_FAILURE,
        NETWORK_FAILURE
    }

    public static class UploadData {
        public int BatchesCurrent;
        public int BatchesMax;
        public int FilesCurrent;
        public int FilesMax;

        public UploadData(int bCurrent, int bMax, int fCurrent, int fMax) {
            BatchesCurrent = bCurrent;
            BatchesMax = bMax;
            FilesCurrent = fCurrent;
            FilesMax = fMax;
        }
    }

    private UploadStatus uploadStatus = UploadStatus.IDLE;
    private UploadResult uploadResult = UploadResult.NO_UPLOADS;
    private UploadData uploadData = new UploadData(0, 0, 0, 0);

    private final Context m_context;
    private ArrayList<UploadListener> m_onStatusChangedCallbacks = new ArrayList<>();

    public UploadScheduler(Context c) {
        m_context = c;
    }

    public void addListener(UploadListener listener) {
        m_onStatusChangedCallbacks.add(listener);
    }

    public void clearListeners() {
        m_onStatusChangedCallbacks.clear();
    }

    private void invokeListeners() {
        for (UploadListener listener : m_onStatusChangedCallbacks) {
            listener.onInvoke(uploadStatus, uploadResult, uploadData);
        }
    }

    public void startUpload() {
        Log.d(TAG, "Manually triggering upload via WorkManager");

        Constraints.Builder constraintsBuilder = new Constraints.Builder();

        if (!Boolean.parseBoolean(Settings.getString("useCellular", ""))) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED);
        } else {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED);
        }

        OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(constraintsBuilder.build())
                .build();

        WorkManager.getInstance(m_context).enqueueUniqueWork(
                "UploadWork",
                ExistingWorkPolicy.KEEP,
                uploadWorkRequest
        );

        // We set status to UPLOADING and invoke listeners. 
        // Note: Real-time progress updates would require observing the WorkInfo.
        uploadStatus = UploadStatus.UPLOADING;
        invokeListeners();
    }

    public void stopUpload() {
        WorkManager.getInstance(m_context).cancelUniqueWork("UploadWork");
        uploadStatus = UploadStatus.IDLE;
        invokeListeners();
    }

    public void updateUpload() {
        invokeListeners();
    }

    public void destroy() {
        // No longer needs to unregister network callbacks
    }

    public UploadStatus getUploadStatus() { return uploadStatus; }
    public UploadResult getUploadResult() { return uploadResult; }
}
