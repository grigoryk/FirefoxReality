/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.mozilla.telemetry.schedule.jobscheduler.TelemetryJobService;
import org.mozilla.vrbrowser.R;
import org.mozilla.geckoview.MediaElement;
import org.mozilla.vrbrowser.browser.Media;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.ui.views.MediaSeekBar;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.VolumeControl;
import org.mozilla.vrbrowser.ui.widgets.menus.VideoProjectionMenuWidget;

public class MediaControlsWidget extends UIWidget implements MediaElement.Delegate {

    private Media mMedia;
    private MediaSeekBar mSeekBar;
    private VolumeControl mVolumeControl;
    private UIButton mMediaPlayButton;
    private UIButton mMediaSeekBackButton;
    private UIButton mMediaSeekForwardButton;
    private UIButton mMediaProjectionButton;
    private UIButton mMediaVolumeButton;
    private UIButton mMediaBackButton;
    private TextView mMediaSeekLabel;
    private Drawable mPlayIcon;
    private Drawable mPauseIcon;
    private Drawable mVolumeIcon;
    private Drawable mMutedIcon;
    private Runnable mBackHandler;
    private boolean mPlayOnSeekEnd;
    private Rect mOffsetViewBounds;
    private VideoProjectionMenuWidget mProjectionMenu;
    static long VOLUME_SLIDER_CHECK_DELAY = 1000;
    private Handler mVolumeCtrlHandler = new Handler();
    private boolean mHideVolumeSlider = false;
    private Runnable mVolumeCtrlRunnable;

    public MediaControlsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public MediaControlsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public MediaControlsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.media_controls, this);

        mSeekBar = findViewById(R.id.mediaControlSeekBar);
        mVolumeControl = findViewById(R.id.volumeControl);
        mMediaPlayButton = findViewById(R.id.mediaPlayButton);
        mMediaSeekBackButton = findViewById(R.id.mediaSeekBackwardButton);
        mMediaSeekForwardButton = findViewById(R.id.mediaSeekForwardButton);
        mMediaProjectionButton = findViewById(R.id.mediaProjectionButton);
        mMediaVolumeButton = findViewById(R.id.mediaVolumeButton);
        mMediaBackButton = findViewById(R.id.mediaBackButton);
        mMediaSeekLabel = findViewById(R.id.mediaControlSeekLabel);
        mPlayIcon = aContext.getDrawable(R.drawable.ic_icon_media_play);
        mPauseIcon = aContext.getDrawable(R.drawable.ic_icon_media_pause);
        mMutedIcon = aContext.getDrawable(R.drawable.ic_icon_media_volume_muted);
        mVolumeIcon = aContext.getDrawable(R.drawable.ic_icon_media_volume);
        mOffsetViewBounds = new Rect();

        mVolumeCtrlRunnable = () -> {
            if ((mHideVolumeSlider) && (mVolumeControl.getVisibility() == View.VISIBLE)) {
                mVolumeControl.setVisibility(View.INVISIBLE);
                stopVolumeCtrlHandler();
            }
        };

        mMediaPlayButton.setOnClickListener(v -> {
            if (mMedia.isEnded()) {
                mMedia.seek(0);
                mMedia.play();
            } else if (mMedia.isPlaying()) {
                mMedia.pause();
            } else {
                mMedia.play();
            }

            mMediaPlayButton.requestFocusFromTouch();
        });

        mMediaSeekBackButton.setOnClickListener(v -> {
            mMedia.seek(Math.max(0, mMedia.getCurrentTime() - 10.0f));
            mMediaSeekBackButton.requestFocusFromTouch();
        });

        mMediaSeekForwardButton.setOnClickListener(v -> {
            double t = mMedia.getCurrentTime() + 30;
            if (mMedia.getDuration() > 0) {
                t = Math.min(mMedia.getDuration(), t);
            }
            mMedia.seek(t);
            mMediaSeekForwardButton.requestFocusFromTouch();
        });

        mMediaProjectionButton.setOnClickListener(v -> {
            WidgetPlacement placement = mProjectionMenu.getPlacement();
            placement.parentHandle = this.getHandle();
            placement.worldWidth = 0.5f;
            placement.parentAnchorX = 0.65f;
            placement.parentAnchorY = 0.4f;
            placement.cylinderMapRadius = 0.0f;
            placement.cylinder = SettingsStore.getInstance(getContext()).isCurvedModeEnabled();
            if (mWidgetManager.getCylinderDensity() > 0) {
                placement.rotationAxisY = 1.0f;
                placement.rotation = (float) Math.toRadians(-7);
            }
            if (mProjectionMenu.isVisible()) {
                mProjectionMenu.hide(KEEP_WIDGET);

            } else {
                mProjectionMenu.show(REQUEST_FOCUS);
            }
            mWidgetManager.updateWidget(mProjectionMenu);
        });

        mMediaVolumeButton.setOnClickListener(v -> {
            if (mMedia.isMuted()) {
                mMedia.setMuted(false);
            } else {
                mMedia.setMuted(true);
                mVolumeControl.setVolume(0);
            }
            mMediaVolumeButton.requestFocusFromTouch();
        });

        mMediaBackButton.setOnClickListener(v -> {
            if (mBackHandler != null) {
                mBackHandler.run();
            }
            mMediaBackButton.requestFocusFromTouch();
        });

        mSeekBar.setDelegate(new MediaSeekBar.Delegate() {
            @Override
            public void onSeekDragStart() {
                mPlayOnSeekEnd = mMedia.isPlaying();
                mMediaSeekLabel.setVisibility(View.VISIBLE);
                mMedia.pause();
                mSeekBar.requestFocusFromTouch();
            }

            @Override
            public void onSeek(double aTargetTime) {
                mMedia.seek(aTargetTime);
            }

            @Override
            public void onSeekDragEnd() {
                if (mPlayOnSeekEnd) {
                    mMedia.play();
                }
                mMediaSeekLabel.setVisibility(View.GONE);
            }

            @Override
            public void onSeekHoverStart() {
                mMediaSeekLabel.setVisibility(View.VISIBLE);
            }

            @Override
            public void onSeekHoverEnd() {
                mMediaSeekLabel.setVisibility(View.GONE);
            }

            @Override
            public void onSeekPreview(String aText, double aRatio) {
                mMediaSeekLabel.setText(aText);
                View childView = mSeekBar.getSeekBarView();
                childView.getDrawingRect(mOffsetViewBounds);
                MediaControlsWidget.this.offsetDescendantRectToMyCoords(childView, mOffsetViewBounds);

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mMediaSeekLabel.getLayoutParams();
                params.setMarginStart(mOffsetViewBounds.left + (int) (aRatio * mOffsetViewBounds.width()) - mMediaSeekLabel.getMeasuredWidth() / 2);
                mMediaSeekLabel.setLayoutParams(params);
            }
        });


        mVolumeControl.setDelegate(new VolumeControl.Delegate() {

            @Override
            public void onVolumeChange(double aVolume) {
                mMedia.setVolume(aVolume);
                if (mMedia.isMuted()) {
                    mMedia.setMuted(false);
                }
                mVolumeControl.requestFocusFromTouch();
            }

            @Override
            public void onSeekBarActionCancelled() {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            }
        });


        this.setOnHoverListener((v, event) -> {
            if (mMedia == null) {
                return false;
            }
           /*this handles the case where the user
              holds the volume slider up/down past the volume control
              control then hovers, which wont be picked up by the
              volume control hover listener.  in this case the widget itself
              needs to handle this case
            */
            if ((event.getX() < 0) || (event.getY() < 0)) {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            }

            return false;
        });
        mMediaVolumeButton.setOnHoverListener((v, event) -> {
            float startY = v.getY();
            float maxY = startY + v.getHeight();
            //for this we only hide on the left side of volume button or outside y area of button
            if ((event.getX() <= 0) || (event.getX() >= v.getWidth()) || (!(event.getY() > startY && event.getY() < maxY))) {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            } else {
                mVolumeControl.setVisibility(View.VISIBLE);
                mHideVolumeSlider = false;
                stopVolumeCtrlHandler();
            }
            return false;
        });

        mVolumeControl.setOnHoverListener((v, event) -> {
            float startY = 0;
            float maxY = startY + v.getHeight();
            if ((event.getX() > 0 && event.getX() < v.getWidth()) && (event.getY() > startY && event.getY() < maxY)) {
                mHideVolumeSlider = false;
                stopVolumeCtrlHandler();
            }
            //for this we only hide on the right side of volume button or outside y area of button
            else if ((event.getX() <= 0) || (event.getX() >= v.getWidth()) || (!(event.getY() > startY && event.getY() < maxY))) {
                mHideVolumeSlider = true;
                startVolumeCtrlHandler();
            }
            return false;
        });


    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.media_controls_container_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.media_controls_container_height);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(context, R.dimen.media_controls_world_z);
        aPlacement.anchorX = 0.45f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.cylinderMapRadius = 0.0f; // Do not map X when this widget uses cylindrical layout.
    }

    public void setParentWidget(int aHandle) {
        mWidgetPlacement.parentHandle = aHandle;
    }

    public void setProjectionMenuWidget(VideoProjectionMenuWidget aWidget) {
        mProjectionMenu = aWidget;
    }

    public void setBackHandler(Runnable aRunnable) {
        mBackHandler = aRunnable;
    }

    @Override
    public void releaseWidget() {
        super.releaseWidget();
    }

    public void setMedia(Media aMedia) {
        if (mMedia != null && mMedia == aMedia) {
            return;
        }
        if (mMedia != null) {
            mMedia.removeMediaListener(this);
        }
        mMedia = aMedia;
        boolean enabled = mMedia != null;
        mMediaPlayButton.setEnabled(enabled);
        mMediaVolumeButton.setEnabled(enabled);
        mMediaSeekForwardButton.setEnabled(enabled);
        mMediaSeekBackButton.setEnabled(enabled);
        mSeekBar.setEnabled(enabled);

        if (mMedia == null) {
            return;
        }
        onMetadataChange(mMedia.getMediaElement(), mMedia.getMetaData());
        onVolumeChange(mMedia.getMediaElement(), mMedia.getVolume(), mMedia.isMuted());
        onTimeChange(mMedia.getMediaElement(), mMedia.getCurrentTime());
        onVolumeChange(mMedia.getMediaElement(), mMedia.getVolume(), mMedia.isMuted());
        onReadyStateChange(mMedia.getMediaElement(), mMedia.getReadyState());
        onPlaybackStateChange(mMedia.getMediaElement(), mMedia.isPlaying() ? MediaElement.MEDIA_STATE_PLAY : MediaElement.MEDIA_STATE_PAUSE);
        mMedia.addMediaListener(this);
    }

    public void setProjectionSelectorEnabled(boolean aEnabled) {
        mMediaProjectionButton.setEnabled(aEnabled);
    }

    // Media Element delegate
    @Override
    public void onPlaybackStateChange(MediaElement mediaElement, int playbackState) {
        if (playbackState == MediaElement.MEDIA_STATE_PLAY) {
            mMediaPlayButton.setImageDrawable(mPauseIcon);
        } else if (playbackState == MediaElement.MEDIA_STATE_PAUSE) {
            mMediaPlayButton.setImageDrawable(mPlayIcon);
        }
    }


    @Override
    public void onReadyStateChange(MediaElement mediaElement, int readyState) {

    }

    @Override
    public void onMetadataChange(MediaElement mediaElement, MediaElement.Metadata metaData) {
        if (metaData == null) {
            return;
        }
        mSeekBar.setDuration(metaData.duration);
        if (metaData.audioTrackCount == 0) {
            mMediaVolumeButton.setImageDrawable(mMutedIcon);
            mMediaVolumeButton.setEnabled(false);
        } else {
            mMediaVolumeButton.setEnabled(true);
        }
        mSeekBar.setSeekable(metaData.isSeekable);
    }

    @Override
    public void onLoadProgress(MediaElement mediaElement, MediaElement.LoadProgressInfo progressInfo) {
        if (progressInfo.buffered != null) {
            mSeekBar.setBuffered(progressInfo.buffered[progressInfo.buffered.length - 1].end);
        }
    }

    @Override
    public void onVolumeChange(MediaElement mediaElement, double volume, boolean muted) {
        if (!mMediaVolumeButton.isEnabled()) {
            return;
        }
        mMediaVolumeButton.setImageDrawable(muted ? mMutedIcon : mVolumeIcon);
        mVolumeControl.setVolume(volume);
        mVolumeControl.setMuted(muted);
    }

    @Override
    public void onTimeChange(MediaElement mediaElement, double time) {
        mSeekBar.setCurrentTime(time);
    }

    @Override
    public void onPlaybackRateChange(MediaElement mediaElement, double rate) {
    }

    @Override
    public void onFullscreenChange(MediaElement mediaElement, boolean fullscreen) {
    }

    @Override
    public void onError(MediaElement mediaElement, int code) {
    }

    private void startVolumeCtrlHandler() {
        mVolumeCtrlHandler.postDelayed(mVolumeCtrlRunnable, VOLUME_SLIDER_CHECK_DELAY);
    }

    public void stopVolumeCtrlHandler() {
        mVolumeCtrlHandler.removeCallbacks(mVolumeCtrlRunnable);
    }
}
