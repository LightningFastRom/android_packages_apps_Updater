/*
 * Copyright (C) 2007 The Android Open Source Project
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

package org.lineageos.updater.ui;

import android.annotation.SuppressLint;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;

import org.lineageos.updater.R;

/**
 * A basic ImageButton that vibrates on finger down.
 */
public class HapticCheckBox extends CheckBox {
    public HapticCheckBox(Context context) {
        super(context);
        initVibration(context);
    }

    public HapticCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVibration(context);
    }

    public HapticCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVibration(context);
    }

    public HapticCheckBox(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initVibration(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initVibration(Context context) {
        if (isHapticFeedbackEnabled()) {
            setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    // Passthrough
                    return false;
                }
            });
        }
    }
    
    public void wapTextColor(Context context) {
        if (Color.luminance(context.getColor(R.color.theme_accent)) < 0.5) {
            setTextColor(Color.WHITE);
            setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        } else {
            setTextColor(Color.BLACK);
            setCompoundDrawableTintList(ColorStateList.valueOf(Color.BLACK));
        }
    }
}
