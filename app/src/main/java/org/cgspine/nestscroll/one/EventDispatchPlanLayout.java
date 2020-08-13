package org.cgspine.nestscroll.one;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

import org.cgspine.nestscroll.R;
import org.cgspine.nestscroll.Util;

/**
 * @author cginechen
 * @date 2016-12-27
 */

public class EventDispatchPlanLayout extends ViewGroup {
    private static final String TAG = "EventDispatchPlanLayout";
    private static final int INVALID_POINTER = -1;
    /**
     * 头部 View 的 id
     */
    private int mHeaderViewId = 0;
    /**
     * 目标 View 的 id
     */
    private int mTargetViewId = 0;
    /**
     * 头部 View
     */
    private View mHeaderView;
    /**
     * 目标 View
     */
    private View mTargetView;
    private ITargetView mTarget;

    private int mTouchSlop;
    /**
     * 头部 View 初始的偏移量
     */
    private int mHeaderInitOffset;
    /**
     * 头部 View 当前的偏移量
     */
    private int mHeaderCurrentOffset;
    private int mHeaderEndOffset = 0;
    /**
     * 目标 View 初始的偏移量
     */
    private int mTargetInitOffset;
    /**
     * 目标 View 当前的偏移量
     */
    private int mTargetCurrentOffset;
    private int mTargetEndOffset = 0;

    private int mActivePointerId = INVALID_POINTER;
    private boolean mIsDragging;
    private float mInitialDownY;
    private float mInitialMotionY;
    private float mLastMotionY;

    private VelocityTracker mVelocityTracker;
    private float mMaxVelocity;

    private Scroller mScroller;
    private boolean mNeedScrollToInitPos = false;
    private boolean mNeedScrollToEndPos = false;

    public EventDispatchPlanLayout(Context context) {
        this(context, null);
    }

    public EventDispatchPlanLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.EventDispatchPlanLayout, 0, 0);
        // 获取头部 View 的 id
        mHeaderViewId = array.getResourceId(R.styleable.EventDispatchPlanLayout_header_view, 0);
        // 获取目标 View 的 id
        mTargetViewId = array.getResourceId(R.styleable.EventDispatchPlanLayout_target_view, 0);

        mHeaderInitOffset = array.getDimensionPixelSize(R.styleable.
                EventDispatchPlanLayout_header_init_offset, Util.dp2px(getContext(), 20));
        mTargetInitOffset = array.getDimensionPixelSize(R.styleable.
                EventDispatchPlanLayout_target_init_offset, Util.dp2px(getContext(), 40));
        mHeaderCurrentOffset = mHeaderInitOffset;
        mTargetCurrentOffset = mTargetInitOffset;
        array.recycle();
        // 允许改变子类绘制顺序
        ViewCompat.setChildrenDrawingOrderEnabled(this, true);

        final ViewConfiguration vc = ViewConfiguration.get(getContext());
        mMaxVelocity = vc.getScaledMaximumFlingVelocity();
        mTouchSlop = Util.px2dp(context, vc.getScaledTouchSlop()); //系统的值是8dp,太大了。。。

        mScroller = new Scroller(getContext());
        mScroller.setFriction(0.98f);
    }
    // 在这里可以获取子 View
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mHeaderViewId != 0) {
            mHeaderView = findViewById(mHeaderViewId);
        }
        if (mTargetViewId != 0) {
            mTargetView = findViewById(mTargetViewId);
            ensureTarget();
        }
    }

    /**
     * 确保 Target View 实现了 ITargetView 接口
     */
    private void ensureTarget() {
        if (mTargetView instanceof ITargetView) {
            mTarget = (ITargetView) mTargetView;
        } else {
            throw new RuntimeException("TargetView should implement interface ITargetView");
        }
    }

    /**
     * 确保有头部 View 和目标 View
     */
    private void ensureHeaderViewAndScrollView() {
        if (mHeaderView != null && mTargetView != null) {
            return;
        }
        if (mHeaderView == null && mTargetView == null && getChildCount() >= 2) {
            mHeaderView = getChildAt(0);
            mTargetView = getChildAt(1);
            ensureTarget();
            return;
        }
        throw new RuntimeException("please ensure headerView and scrollView");
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        ensureHeaderViewAndScrollView();
        int headerIndex = indexOfChild(mHeaderView);
        int scrollIndex = indexOfChild(mTargetView);
        if (headerIndex < scrollIndex) {
            return i;
        }
        if (headerIndex == i) {
            return scrollIndex;
        } else if (scrollIndex == i) {
            return headerIndex;
        }
        return i;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // 去掉默认行为，使得每个事件都会经过这个Layout
    }

    /**
     * 测量过程
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ensureHeaderViewAndScrollView();
        // 不走 measureChild 方法，直接确定好测量规格，这种方式不会受targetView布局参数的影响了。
        int scrollMeasureWidthSpec = MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY);
        int scrollMeasureHeightSpec = MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY);
        // 这行代码之后会调用 LinearLayout 的 onMeasure 方法，完成 TargetView 的测量。
        mTargetView.measure(scrollMeasureWidthSpec, scrollMeasureHeightSpec);
        measureChild(mHeaderView, widthMeasureSpec, heightMeasureSpec);
//        measureChild(mTargetView, widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 布局过程
     * @param changed
     * @param l
     * @param t
     * @param r
     * @param b
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        ensureHeaderViewAndScrollView();

        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        // 布局 targetView
        mTargetView.layout(childLeft, childTop + mTargetCurrentOffset,
                childLeft + childWidth, childTop + childHeight + mTargetCurrentOffset);
        int refreshViewWidth = mHeaderView.getMeasuredWidth();
        int refreshViewHeight = mHeaderView.getMeasuredHeight();
        // 布局头部 View
        mHeaderView.layout((width / 2 - refreshViewWidth / 2), mHeaderCurrentOffset,
                (width / 2 + refreshViewWidth / 2), mHeaderCurrentOffset + refreshViewHeight);
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureHeaderViewAndScrollView();
        final int action = MotionEventCompat.getActionMasked(ev);
        int pointerIndex;
        // 不阻断事件的快路径：如果目标view可以往上滚动或者`EventDispatchPlanLayout`不是enabled
        if (!isEnabled() || mTarget.canChildScrollUp()) {
            Log.d(TAG, "fast end onIntercept: isEnabled = " + isEnabled() + "; canChildScrollUp = "
                    + mTarget.canChildScrollUp());
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsDragging = false;
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                // 在 down 的时候记录初始的 y 值
                mInitialDownY = ev.getY(pointerIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = ev.getY(pointerIndex);
                // 判断是否 dragging
                startDragging(y);
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                // 双指逻辑处理
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // 多点触控使用这个方法获取事件类型
        final int action = MotionEventCompat.getActionMasked(ev);
        int pointerIndex;

        if (!isEnabled() || mTarget.canChildScrollUp()) {
            Log.d(TAG, "fast end onTouchEvent: isEnabled = " + isEnabled() + "; canChildScrollUp = "
                    + mTarget.canChildScrollUp());
            return false;
        }

        acquireVelocityTracker(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // 获取一个指针(手指)的唯一标识符ID，在手指按下和抬起之间ID始终不变。
                mActivePointerId = ev.getPointerId(0);
                mIsDragging = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                // 通过PointerId获取到当前状态下 pointIndex，之后通过 pointIndex 获取其他内容。
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }
                final float y = ev.getY(pointerIndex);
                startDragging(y);

                if (mIsDragging) {
                    float dy = y - mLastMotionY;
                    if (dy >= 0) {
                        moveTargetView(dy);
                    } else {
                        if (mTargetCurrentOffset + dy <= mTargetEndOffset) {
                            moveTargetView(dy);
                            // 重新dispatch一次down事件，使得列表可以继续滚动
                            int oldAction = ev.getAction();
                            ev.setAction(MotionEvent.ACTION_DOWN);
                            dispatchTouchEvent(ev);
                            ev.setAction(oldAction);
                        } else {
                            moveTargetView(dy);
                        }
                    }
                    mLastMotionY = y;
                }
                break;
            }
            case MotionEventCompat.ACTION_POINTER_DOWN: { // 有非主要的手指按下(即按下之前已经有手指在屏幕上)。
                pointerIndex = MotionEventCompat.getActionIndex(ev);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP: // 有非主要的手指抬起(即抬起之后仍然有手指在屏幕上)。
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Got ACTION_UP event but don't have an active pointer id.");
                    return false;
                }

                if (mIsDragging) {
                    mIsDragging = false;
                    mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
                    final float vy = mVelocityTracker.getYVelocity(mActivePointerId);
                    finishDrag((int) vy);
                }
                mActivePointerId = INVALID_POINTER;
                releaseVelocityTracker();
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseVelocityTracker();
                return false;
        }

        return mIsDragging;
    }

    private void acquireVelocityTracker(final MotionEvent event) {
        if (null == mVelocityTracker) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    private void releaseVelocityTracker() {
        if (null != mVelocityTracker) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void finishDrag(int vy) {
        Log.i(TAG, "TouchUp: vy = " + vy);
        if (vy > 0) {
            mNeedScrollToInitPos = true;
            mScroller.fling(0, mTargetCurrentOffset, 0, vy,
                    0, 0, mTargetEndOffset, Integer.MAX_VALUE);
            invalidate();
        } else if (vy < 0) {
            mNeedScrollToEndPos = true;
            mScroller.fling(0, mTargetCurrentOffset, 0, vy,
                    0, 0, mTargetEndOffset, Integer.MAX_VALUE);
            invalidate();
        } else {
            if (mTargetCurrentOffset <= (mTargetEndOffset + mTargetInitOffset) / 2) {
                mNeedScrollToEndPos = true;
            } else {
                mNeedScrollToInitPos = true;
            }
            invalidate();
        }
    }

    private void startDragging(float y) {
        if (y > mInitialDownY || mTargetCurrentOffset > mTargetEndOffset) {
            final float yDiff = Math.abs(y - mInitialDownY);
            if (yDiff > mTouchSlop && !mIsDragging) {
                mInitialMotionY = mInitialDownY + mTouchSlop;
                mLastMotionY = mInitialMotionY;
                mIsDragging = true;
            }
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private void moveTargetView(float dy) {
        int target = (int) (mTargetCurrentOffset + dy);
        moveTargetViewTo(target);
    }

    private void moveTargetViewTo(int target) {
        target = Math.max(target, mTargetEndOffset);
        ViewCompat.offsetTopAndBottom(mTargetView, target - mTargetCurrentOffset);
        mTargetCurrentOffset = target;

        int headerTarget;
        if (mTargetCurrentOffset >= mTargetInitOffset) {
            headerTarget = mHeaderInitOffset;
        } else if (mTargetCurrentOffset <= mTargetEndOffset) {
            headerTarget = mHeaderEndOffset;
        } else {
            float percent = (mTargetCurrentOffset - mTargetEndOffset) * 1.0f / mTargetInitOffset - mTargetEndOffset;
            headerTarget = (int) (mHeaderEndOffset + percent * (mHeaderInitOffset - mHeaderEndOffset));
        }
        ViewCompat.offsetTopAndBottom(mHeaderView, headerTarget - mHeaderCurrentOffset);
        mHeaderCurrentOffset = headerTarget;
    }

    public interface ITargetView {
        boolean canChildScrollUp();

        void fling(float vy);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int offsetY = mScroller.getCurrY();
            moveTargetViewTo(offsetY);
            invalidate();
        } else if (mNeedScrollToInitPos) {
            mNeedScrollToInitPos = false;
            if (mTargetCurrentOffset == mTargetInitOffset) {
                return;
            }
            mScroller.startScroll(0, mTargetCurrentOffset, 0, mTargetInitOffset - mTargetCurrentOffset);
            invalidate();
        } else if (mNeedScrollToEndPos) {
            mNeedScrollToEndPos = false;
            if (mTargetCurrentOffset == mTargetEndOffset) {
                if (mScroller.getCurrVelocity() > 0) {
                    // 如果还有速度，则传递给子view
                    mTarget.fling(-mScroller.getCurrVelocity());
                }
            }
            mScroller.startScroll(0, mTargetCurrentOffset, 0, mTargetEndOffset - mTargetCurrentOffset);
            invalidate();
        }
    }
}
