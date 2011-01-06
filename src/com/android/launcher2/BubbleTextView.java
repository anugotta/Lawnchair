/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends CacheableTextView {
    static final float CORNER_RADIUS = 4.0f;
    static final float SHADOW_LARGE_RADIUS = 4.0f;
    static final float SHADOW_SMALL_RADIUS = 1.75f;
    static final float SHADOW_Y_OFFSET = 2.0f;
    static final int SHADOW_LARGE_COLOUR = 0xCC000000;
    static final int SHADOW_SMALL_COLOUR = 0xBB000000;
    static final float PADDING_H = 8.0f;
    static final float PADDING_V = 3.0f;

    private Paint mPaint;
    private float mBubbleColorAlpha;
    private int mPrevAlpha = -1;

    private final HolographicOutlineHelper mOutlineHelper = new HolographicOutlineHelper();
    private final Canvas mTempCanvas = new Canvas();
    private final Rect mTempRect = new Rect();
    private final Paint mTempPaint = new Paint();
    private boolean mDidInvalidateForPressedState;
    private Bitmap mPressedOrFocusedBackground;
    private int mFocusedOutlineColor;
    private int mFocusedGlowColor;
    private int mPressedOutlineColor;
    private int mPressedGlowColor;

    private boolean mBackgroundSizeChanged;
    private Drawable mBackground;

    public BubbleTextView(Context context) {
        super(context);
        init();
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mBackground = getBackground();
        setFocusable(true);
        setBackgroundDrawable(null);

        final Resources res = getContext().getResources();
        int bubbleColor = res.getColor(R.color.bubble_dark_background);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(bubbleColor);
        mBubbleColorAlpha = Color.alpha(bubbleColor) / 255.0f;
        mFocusedOutlineColor = res.getColor(R.color.workspace_item_focused_outline_color);
        mFocusedGlowColor = res.getColor(R.color.workspace_item_focused_glow_color);
        mPressedOutlineColor = res.getColor(R.color.workspace_item_pressed_outline_color);
        mPressedGlowColor = res.getColor(R.color.workspace_item_pressed_glow_color);
    }

    protected int getCacheTopPadding() {
        return (int) PADDING_V;
    }
    protected int getCacheBottomPadding() {
        return (int) (PADDING_V + SHADOW_LARGE_RADIUS + SHADOW_Y_OFFSET);
    }
    protected int getCacheLeftPadding() {
        return (int) (PADDING_H + SHADOW_LARGE_RADIUS);
    }
    protected int getCacheRightPadding() {
        return (int) (PADDING_H + SHADOW_LARGE_RADIUS);
    }

    public void applyFromShortcutInfo(ShortcutInfo info, IconCache iconCache) {
        Bitmap b = info.getIcon(iconCache);

        setCompoundDrawablesWithIntrinsicBounds(null,
                new FastBitmapDrawable(b),
                null, null);
        setText(info.title);
        buildAndEnableCache();
        setTag(info);
    }

    @Override
    protected boolean setFrame(int left, int top, int right, int bottom) {
        if (mLeft != left || mRight != right || mTop != top || mBottom != bottom) {
            mBackgroundSizeChanged = true;
        }
        return super.setFrame(left, top, right, bottom);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mBackground || super.verifyDrawable(who);
    }

    @Override
    protected void drawableStateChanged() {
        if (isPressed()) {
            // In this case, we have already created the pressed outline on ACTION_DOWN,
            // so we just need to do an invalidate to trigger draw
            if (!mDidInvalidateForPressedState) {
                invalidate();
            }
        } else {
            // Otherwise, either clear the pressed/focused background, or create a background
            // for the focused state
            final boolean backgroundEmptyBefore = mPressedOrFocusedBackground == null;
            mPressedOrFocusedBackground = null;
            if (isFocused()) {
                mPressedOrFocusedBackground = createGlowingOutline(
                        mTempCanvas, mFocusedGlowColor, mFocusedOutlineColor);
                invalidate();
            }
            final boolean backgroundEmptyNow = mPressedOrFocusedBackground == null;
            if (!backgroundEmptyBefore && backgroundEmptyNow) {
                invalidate();
            }
        }

        Drawable d = mBackground;
        if (d != null && d.isStateful()) {
            d.setState(getDrawableState());
        }
        super.drawableStateChanged();
    }

    /**
     * Draw the View v into the given Canvas.
     *
     * @param v the view to draw
     * @param destCanvas the canvas to draw on
     * @param padding the horizontal and vertical padding to use when drawing
     */
    private void drawWithPadding(Canvas destCanvas, int padding) {
        final Rect clipRect = mTempRect;
        getDrawingRect(clipRect);

        // adjust the clip rect so that we don't include the text label
        clipRect.bottom =
            getExtendedPaddingTop() - (int) BubbleTextView.PADDING_V + getLayout().getLineTop(0);

        // Draw the View into the bitmap.
        // The translate of scrollX and scrollY is necessary when drawing TextViews, because
        // they set scrollX and scrollY to large values to achieve centered text
        destCanvas.save();
        destCanvas.translate(-getScrollX() + padding / 2, -getScrollY() + padding / 2);
        destCanvas.clipRect(clipRect, Op.REPLACE);
        draw(destCanvas);
        destCanvas.restore();
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createGlowingOutline(Canvas canvas, int outlineColor, int glowColor) {
        final int padding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS;
        final Bitmap b = Bitmap.createBitmap(
                getWidth() + padding, getHeight() + padding, Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        drawWithPadding(canvas, padding);
        mOutlineHelper.applyExtraThickExpensiveOutlineWithBlur(b, canvas, glowColor, outlineColor);

        return b;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        boolean result = super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // So that the pressed outline is visible immediately when isPressed() is true,
                // we pre-create it on ACTION_DOWN (it takes a small but perceptible amount of time
                // to create it)
                if (mPressedOrFocusedBackground == null) {
                    mPressedOrFocusedBackground = createGlowingOutline(
                            mTempCanvas, mPressedGlowColor, mPressedOutlineColor);
                }
                // Invalidate so the pressed state is visible, or set a flag so we know that we
                // have to call invalidate as soon as the state is "pressed"
                if (isPressed()) {
                    mDidInvalidateForPressedState = true;
                    invalidate();
                } else {
                    mDidInvalidateForPressedState = false;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // If we've touched down and up on an item, and it's still not "pressed", then
                // destroy the pressed outline
                if (!isPressed()) {
                    mPressedOrFocusedBackground = null;
                }
                break;
        }
        return result;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mPressedOrFocusedBackground != null && (isPressed() || isFocused())) {
            // The blue glow can extend outside of our clip region, so we first temporarily expand
            // the canvas's clip region
            canvas.save(Canvas.CLIP_SAVE_FLAG);
            int padding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS / 2;
            canvas.clipRect(-padding + mScrollX, -padding + mScrollY,
                    getWidth() + padding + mScrollX, getHeight() + padding + mScrollY,
                    Region.Op.REPLACE);
            // draw blue glow
            canvas.drawBitmap(mPressedOrFocusedBackground,
                    mScrollX - padding, mScrollY - padding, mTempPaint);
            canvas.restore();
        }
        if (isBuildingCache()) {
            // We enhance the shadow by drawing the shadow twice
            this.setShadowLayer(SHADOW_LARGE_RADIUS, 0.0f, SHADOW_Y_OFFSET, SHADOW_LARGE_COLOUR);
            super.draw(canvas);
            this.setShadowLayer(SHADOW_SMALL_RADIUS, 0.0f, 0.0f, SHADOW_SMALL_COLOUR);
            super.draw(canvas);
        } else {
            final Drawable background = mBackground;
            if (background != null) {
                final int scrollX = mScrollX;
                final int scrollY = mScrollY;

                if (mBackgroundSizeChanged) {
                    background.setBounds(0, 0,  mRight - mLeft, mBottom - mTop);
                    mBackgroundSizeChanged = false;
                }

                if ((scrollX | scrollY) == 0) {
                    background.draw(canvas);
                } else {
                    canvas.translate(scrollX, scrollY);
                    background.draw(canvas);
                    canvas.translate(-scrollX, -scrollY);
                }
            }
            super.draw(canvas);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mBackground != null) mBackground.setCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBackground != null) mBackground.setCallback(null);
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (mPrevAlpha != alpha) {
            mPrevAlpha = alpha;
            mPaint.setAlpha((int) (alpha * mBubbleColorAlpha));
            super.onSetAlpha(alpha);
        }
        return true;
    }
}
