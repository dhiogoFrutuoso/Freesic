package com.freesic.app;

import android.animation.ValueAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Curves recovered from Lark's packaged MotionScene; gesture thresholds are Freesic choices. */
final class MotionCurves {
    static final Interpolator OPEN = new PathInterpolator(.56f, 1.25f, .6f, 1f);
    static final Interpolator CLOSE = new PathInterpolator(.4f, 0f, .2f, 1f);
    static final Interpolator TRACK = new PathInterpolator(.2f, 0f, .4f, 1f);
    static long duration(long milliseconds) { return ValueAnimator.areAnimatorsEnabled() ? milliseconds : 0; }
    private MotionCurves() {}
}
