// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

/**
 * Parcelable wrapper for MotionEvent data for IPC transmission.
 *
 * MotionEvent cannot be directly parceled across process boundaries,
 * so we extract the essential data and serialize it.
 *
 * This supports single-pointer events. Multi-touch can be added later if needed.
 */
parcelable MotionEventParcel {
    /**
     * Event action (ACTION_DOWN, ACTION_UP, ACTION_MOVE, etc.)
     * Maps to MotionEvent.ACTION_* constants
     */
    int action;

    /**
     * X coordinate relative to the view
     */
    float x;

    /**
     * Y coordinate relative to the view
     */
    float y;

    /**
     * Event timestamp in milliseconds (uptimeMillis)
     */
    long eventTime;

    /**
     * Timestamp when the event was created (downTime for gesture start)
     */
    long downTime;

    /**
     * Pressure of the touch (0.0 to 1.0, typically 1.0 for touch)
     */
    float pressure;

    /**
     * Size of the touch area (0.0 to 1.0)
     */
    float size;

    /**
     * Meta state (modifier keys like SHIFT, ALT, etc.)
     */
    int metaState;

    /**
     * Button state for mouse/stylus (0 for touch)
     */
    int buttonState;

    /**
     * Device ID that generated the event
     */
    int deviceId;

    /**
     * Edge flags (whether touch is on edge of screen)
     */
    int edgeFlags;

    /**
     * Source type (SOURCE_TOUCHSCREEN, SOURCE_MOUSE, etc.)
     */
    int source;

    /**
     * Flags for the event
     */
    int flags;
}

