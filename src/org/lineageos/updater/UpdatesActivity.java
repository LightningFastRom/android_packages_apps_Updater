/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Handler;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONException;
import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.PermissionsUtils;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.ui.PreferenceSheet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpdatesActivity extends UpdatesListActivity {

    private static final String TAG = "UpdatesActivity";

    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;

    private Handler mHandler = null;
    private int mHandlerDelay = 1000;

    private Switch mAutoUpdate;
    private SwipeRefreshLayout pullToRefresh;

    private SharedPreferences mPrefs;

    private boolean mIsTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        mIsTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        pullToRefresh = findViewById(R.id.updates_swipe_container);

        if (Color.luminance(getApplication().getApplicationContext().getColor(R.color.theme_accent)) < 0.5) {
            pullToRefresh.setColorSchemeColors(Color.WHITE);
        } else {
            pullToRefresh.setColorSchemeColors(Color.BLACK);
        }

        pullToRefresh.setProgressBackgroundColorSchemeResource(R.color.theme_accent);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter(this);
        recyclerView.setAdapter(mAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));

        boolean hasPermission = PermissionsUtils.checkAndRequestStoragePermission(
                this, 0);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (hasPermission) {
                    if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                        String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                        handleDownloadStatusChange(downloadId);
                        mAdapter.notifyDataSetChanged();
                    } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                            UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                        String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                        mAdapter.notifyItemChanged(downloadId);
                    } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                        String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                        mAdapter.removeItem(downloadId);
                    }
                }
            }
        };

        if (!mIsTV) {
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView headerTitle = (TextView) findViewById(R.id.header_title);
        headerTitle.setText(getString(R.string.header_title_text,
                BuildInfoUtils.getBuildVersion()));


        if (mIsTV) {
            Button prefButton = (Button) findViewById(R.id.preferences);
            prefButton.setVisibility(View.VISIBLE);
            prefButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPreferencesDialog();
                }
            });

            findViewById(R.id.refresh).setOnClickListener(v -> {
                downloadUpdatesList(true);
            });
        }

        pullToRefresh.setOnRefreshListener(() -> downloadUpdatesList(true));

        mAutoUpdate = (Switch) findViewById(R.id.auto_update);
        mAutoUpdate.setChecked(mPrefs.getBoolean(Constants.PREF_AUTO_UPDATES, true));
        mAutoUpdate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.edit()
                    .putBoolean(Constants.PREF_AUTO_UPDATES,
                            isChecked)
                    .apply();
            }
        });
        
        mHandler = new Handler();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
        
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground( final Void ... params ) {
                return null;
            }

            @Override
            protected void onPostExecute( final Void result ) {
                downloadUpdatesList(true);
                pullToRefresh.setRefreshing(true);
            }
          }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_preferences: {
                showPreferencesDialog();
                return true;
            }
            case R.id.menu_show_changelog: {
                Intent openUrl = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(Utils.getChangelogURL(this)));
                startActivity(openUrl);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mAdapter.setUpdaterController(mUpdaterService.getUpdaterController());
            getUpdatesList();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setUpdaterController(null);
            mUpdaterService = null;
            mAdapter.notifyDataSetChanged();
        }
    };

    private void loadUpdatesList(File jsonFile, boolean manualRefresh)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        UpdaterController controller = mUpdaterService.getUpdaterController();
        boolean newUpdates = false;

        List<UpdateInfo> updates = Utils.parseJson(jsonFile, true);
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            newUpdates |= controller.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }
        controller.setUpdatesAvailableOnline(updatesOnline, true);

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();

        if (manualRefresh) {
            TextView updateMessage = (TextView) findViewById(R.id.update_message);
            updateMessage.setText(newUpdates ? R.string.snack_updates_found : R.string.is_up_to_date);
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshAnimationStop();

                if (sortedUpdates.isEmpty()) {
                    findViewById(R.id.all_up_to_date_view).setVisibility(View.VISIBLE);
                    findViewById(R.id.recycler_view).setVisibility(View.GONE);

                } else {
                    findViewById(R.id.all_up_to_date_view).setVisibility(View.GONE);
                    findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
                    sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
                    for (UpdateInfo update : sortedUpdates) {
                        updateIds.add(update.getDownloadId());
                    }
                    mAdapter.setData(updateIds);
                    mAdapter.notifyDataSetChanged();
                }
            }
        }, mHandlerDelay);
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdatesList(jsonFile, false);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdatesList(false);
        }
    }

    private void processNewJson(File json, File jsonNew, boolean manualRefresh) {
        try {
            loadUpdatesList(jsonNew, manualRefresh);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            long millis = System.currentTimeMillis();
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply();
            if (json.exists() && Utils.isUpdateCheckEnabled(this) &&
                    Utils.checkForNewUpdates(json, jsonNew)) {
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(this);
            }
            // In case we set a one-shot check because of a previous failure
            UpdatesCheckReceiver.cancelUpdatesCheck(this);
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
        }
    }

    private void downloadUpdatesList(final boolean manualRefresh) {
        final File jsonFile = Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
                runOnUiThread(() -> {
                    if (!cancelled) {
                        showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
                    }

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshAnimationStop();
                            pullToRefresh.setRefreshing(false);
                        }
                    }, mHandlerDelay);
                });
            }

            @Override
            public void onResponse(int statusCode, String url,
                    DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                runOnUiThread(() -> {
                    Log.d(TAG, "List downloaded");
                    processNewJson(jsonFile, jsonFileTmp, manualRefresh);

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshAnimationStop();
                            pullToRefresh.setRefreshing(false);
                        }
                    }, mHandlerDelay);
                });
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonFileTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
            return;
        }

        refreshAnimationStart();
        downloadClient.start();
    }

    private void handleDownloadStatusChange(String downloadId) {
        UpdateInfo update = mUpdaterService.getUpdaterController().getUpdate(downloadId);
        switch (update.getStatus()) {
            case PAUSED_ERROR:
                showSnackbar(R.string.snack_download_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFICATION_FAILED:
                showSnackbar(R.string.snack_download_verification_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFIED:
                showSnackbar(R.string.snack_download_verified, Snackbar.LENGTH_LONG);
                break;
        }
    }

    @Override
    public void showSnackbar(int stringId, int duration) {
        Snackbar.make(findViewById(R.id.main_container), stringId, duration).show();
    }

    private void refreshAnimationStart() {
        findViewById(R.id.all_up_to_date_view).setVisibility(View.GONE);
        findViewById(R.id.recycler_view).setVisibility(View.GONE);
        if (mIsTV) {
            findViewById(R.id.refresh_progress).setVisibility(View.VISIBLE);
        }
    }

    private void refreshAnimationStop() {
        if (mIsTV) {
            findViewById(R.id.refresh_progress).setVisibility(View.GONE);
        }

        if (mAdapter.getItemCount() > 0) {
            findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.all_up_to_date_view).setVisibility(View.VISIBLE);
        }
    }

    private void showPreferencesDialog() {
        new PreferenceSheet().newInstance(mUpdaterService).show(getSupportFragmentManager(), "prefdialog");
    }
}
