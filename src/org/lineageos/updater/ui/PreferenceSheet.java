package org.lineageos.updater.ui;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import android.app.Dialog;
import androidx.preference.PreferenceManager;

import org.lineageos.updater.R;
import org.lineageos.updater.UpdatesCheckReceiver;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class PreferenceSheet extends BottomSheetDialogFragment {
    private SharedPreferences mPrefs = null;

    private static UpdaterService mUpdaterService = null;

    private Spinner preferencesAutoUpdatesCheckInterval;
    private Switch preferencesAutoDeleteUpdates;
    private Switch preferencesMobileDataWarning;
    private Switch preferencesAbPerfMode;

    public static PreferenceSheet newInstance(UpdaterService updaterService) {
        mUpdaterService = updaterService;
        return new PreferenceSheet();
    }

    @Override public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }

    @Override public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.preferences_dialog, container, false);
      }

    @Override public void onViewCreated( View view, Bundle savedInstanceState) {
    	super.onViewCreated(view, savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        preferencesAutoUpdatesCheckInterval = view.findViewById(R.id.preferences_auto_updates_check_interval);
        preferencesAutoDeleteUpdates = view.findViewById(R.id.preferences_auto_delete_updates);
        preferencesMobileDataWarning = view.findViewById(R.id.preferences_mobile_data_warning);
        preferencesAbPerfMode = view.findViewById(R.id.preferences_ab_perf_mode);

        preferencesAutoUpdatesCheckInterval.setEnabled(mPrefs.getBoolean(Constants.PREF_AUTO_UPDATES, true));
        preferencesAutoUpdatesCheckInterval.setSelection(mPrefs.getInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY));
        preferencesAutoDeleteUpdates.setChecked(mPrefs.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false));
        preferencesMobileDataWarning.setChecked(mPrefs.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true));
        preferencesAbPerfMode.setChecked(mPrefs.getBoolean(Constants.PREF_AB_PERF_MODE, false));
        
        if (Utils.isUpdateCheckEnabled(requireContext())) {
            UpdatesCheckReceiver.scheduleRepeatingUpdatesCheck(requireContext());
        } else {
            UpdatesCheckReceiver.cancelRepeatingUpdatesCheck(requireContext());
            UpdatesCheckReceiver.cancelUpdatesCheck(requireContext());
        }

        if (Utils.isABDevice()) {
            preferencesAbPerfMode.setVisibility(View.VISIBLE);
            boolean enableABPerfMode = preferencesAbPerfMode.isChecked();
            mUpdaterService.getUpdaterController().setPerformanceMode(enableABPerfMode);
        }
    }

    @Override
    public void onCancel(DialogInterface dialog)
    {
        super.onCancel(dialog);
        mPrefs.edit()
            .putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                    preferencesAutoUpdatesCheckInterval.getSelectedItemPosition())
            .putBoolean(Constants.PREF_AUTO_DELETE_UPDATES,
                    preferencesAutoDeleteUpdates.isChecked())
            .putBoolean(Constants.PREF_MOBILE_DATA_WARNING,
                    preferencesMobileDataWarning.isChecked())
            .putBoolean(Constants.PREF_AB_PERF_MODE,
                    preferencesAbPerfMode.isChecked())
            .apply();
    }
}
