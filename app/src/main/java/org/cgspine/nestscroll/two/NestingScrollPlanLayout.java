package org.cgspine.nestscroll.two;

import android.content.Context;
import android.content.res.TypedArray;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Scroller;

import org.cgspine.nestscroll.R;
import org.cgspine.nestscroll.Util;

/**
 * @author cginechen
 * @date 2016-12-28
 */

public class NestingScrollPlanLayout extends ViewGroup implements NestedScrollingParent {
    private static final String TAG = "NestingScrollPlanLayout";

    private int mHeaderViewId = 0;
    private int mTargetViewId = 0;
    private View mHeaderView;
    private View mTargetView;
    // 头部 View 偏移量
    private int mHeaderInitOffset;
    private int mHeaderCurrentOffset;
    private int mHeaderEndOffset = 0;
    // target View 偏移量
    private int mTargetInitOffset;
    private int mTargetCurrentOffset;
    private int mTargetEndOffset = 0;

    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private Scroller mScroller;
    private boolean mNeedScrollToInitPos = false;
    private boolean mNeedScrollToEndPos = false;
    private boolean mHasFling = false;

    public NestingScrollPlanLayout(Context context) {
        this(context, null);
    }

    public NestingScrollPlanLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.NestingScrollPlanLayout, 0, 0);
        mHeaderViewId = array.getResourceId(R.styleable.NestingScrollPlanLayout_header_view, 0);
        mTargetViewId = array.getResourceId(R.styleable.NestingScrollPlanLayout_target_view, 0);

        mHeaderInitOffset = array.getDimensionPixelSize(R.styleable.
                NestingScrollPlanLayout_header_init_offset, Util.dp2px(getContext(), 20));
        mTargetInitOffset = array.getDimensionPixelSize(R.styleable.
                NestingScrollPlanLayout_target_init_offset, Util.dp2px(getContext(), 40));
        mHeaderCurrentOffset = mHeaderInitOffset;
        mTargetCurrentOffset = mTargetInitOffset;
        array.recycle();

        setChildrenDrawingOrderEnabled(true);
        // 初始化 NestedScrollingParentHelper 这个辅助类
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);

        mScroller = new Scroller(getContext());
        mScroller.setFriction(0.98f);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mHeaderViewId != 0) {
            mHeaderView = findViewById(mHeaderViewId);
        }
        if (mTargetViewId != 0) {
            mTargetView = findViewById(mTargetViewId);
        }
    }

    private void ensureHeaderViewAndScrollView() {
        if (mHeaderView != null && mTargetView != null) {
            return;
        }
        if (mHeaderView == null && mTargetView == null && getChildCount() >= 2) {
            mHeaderView = getChildAt(0);
            mTargetView = getChildAt(1);
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
    // 测量
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ensureHeaderViewAndScrollView();
        // target view 的高度是全屏的高度
        int scrollMeasureWidthSpec = MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY);
        int scrollMeasureHeightSpec = MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY);
        mTargetView.measure(scrollMeasureWidthSpec, scrollMeasureHeightSpec);
        measureChild(mHeaderView, widthMeasureSpec, heightMeasureSpec);
    }
    // 布局
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
        mTargetView.layout(childLeft, childTop + mTargetCurrentOffset,
                childLeft + childWidth, childTop + childHeight + mTargetCurrentOffset);
        int headerViewWidth = mHeaderView.getMeasuredWidth();
        int headerViewHeight = mHeaderView.getMeasuredHeight();
        mHeaderView.layout((width / 2 - headerViewWidth / 2), mHeaderCurrentOffset,
                (width / 2 + headerViewWidth / 2), mHeaderCurrentOffset + headerViewHeight);
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        Log.i(TAG, "onStartNestedScroll: nestedScrollAxes = " + nestedScrollAxes);
        // 接受纵向滚动
        return isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes) {
        Log.i(TAG, "onNestedScrollAccepted: axes = " + axes);
        // 这一步需要交给 NestedScrollingParentHelper 去记录相关变量
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        // NestingScroll 滚动前，我们要先看看自己能不能消耗，消耗量记录在 consumed 数组里面
        // 往上滑动时我们先看看自己可以消耗多少（因为上滑时自己的消耗量可以出现上限），往下滑动时我们看看子元素可以消耗多少（因为下滑时子View的消耗量可以出现上限）
        // 基于上一点，我们这里只处理上滑的情况
        Log.i(TAG, "onNestedPreScroll: dx = " + dx + " ; dy = " + dy);
        if (canViewScrollUp(target)) {
            return;
        }
        if (dy > 0) {
            // 往上滑
            int parentCanConsume = mTargetCurrentOffset - mTargetEndOffset;
            if (parentCanConsume > 0) {
                if (dy > parentCanConsume) {
                    // 自己消耗不完，余下部分会给子 View
                    consumed[1] = parentCanConsume;
                    moveTargetViewTo(mTargetEndOffset);
                } else {
                    // 自己全部消耗
                    consumed[1] = dy;
                    moveTargetView(-dy);
                }
            }
        }
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        // NestingScroll 时，我们只处理往下滑的情况，如果有未消耗的量，则滚动父 View
        Log.i(TAG, "onNestedScroll: dxConsumed = " + dxConsumed + " ; dyConsumed = " + dyConsumed +
                " ; dxUnconsumed = " + dxUnconsumed + " ; dyUnconsumed = " + dyUnconsumed);
        if (dyUnconsumed < 0 && !(canViewScrollUp(target))) {
            int dy = -dyUnconsumed;
            moveTargetView(dy);
        }
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onStopNestedScroll(View child) {
        Log.i(TAG, "onStopNestedScroll");
        // 结束滚动：因为不管有没有出现 fling，都会走进这里，所以我这里有一个标志位，如果有fling,则在fling中处理最终定位，否则在结束时处理最终定位
        mNestedScrollingParentHelper.onStopNestedScroll(child);
        if (mHasFling) {
            mHasFling = false;
        } else {
            if (mTargetCurrentOffset <= (mTargetEndOffset + mTargetInitOffset) / 2) {
                mNeedScrollToEndPos = true;
            } else {
                mNeedScrollToInitPos = true;
            }
            invalidate();
        }
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        super.onNestedPreFling(target, velocityX, velocityY);
        // fling 前回调，我们会主动将其滚动到特定位置，如果向上 fling 时，会 return false 表示并不阻断子 view 的 fling
        Log.i(TAG, "onNestedPreFling: mTargetCurrentOffset = " + mTargetCurrentOffset +
                " ; velocityX = " + velocityX + " ; velocityY = " + velocityY);
        mHasFling = true;
        int vy = (int) -velocityY;
        if (velocityY < 0) {
            // 向下
            if (canViewScrollUp(target)) {
                return false;
            }
            mNeedScrollToInitPos = true;
            mScroller.fling(0, mTargetCurrentOffset, 0, vy,
                    0, 0, mTargetEndOffset, Integer.MAX_VALUE);
            invalidate();
            return true;
        } else {
            // 向上
            if (mTargetCurrentOffset <= mTargetEndOffset) {
                return false;
            }
            mNeedScrollToEndPos = true;
            mScroller.fling(0, mTargetCurrentOffset, 0, vy,
                    0, 0, mTargetEndOffset, Integer.MAX_VALUE);
            invalidate();
        }
        return false;
    }


    private boolean canViewScrollUp(View view) {
        return ViewCompat.canScrollVertically(view, -1);
    }


    private void moveTargetView(float dy) {
        int target = mTargetCurrentOffset + (int) (dy);
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
            float percent = (mTargetCurrentOffset - mTargetEndOffset) * 1.0f / (mTargetInitOffset - mTargetEndOffset);
            headerTarget = (int) (mHeaderEndOffset + percent * (mHeaderInitOffset - mHeaderEndOffset));
        }
        ViewCompat.offsetTopAndBottom(mHeaderView, headerTarget - mHeaderCurrentOffset);
        mHeaderCurrentOffset = headerTarget;
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
                return;
            }
            mScroller.startScroll(0, mTargetCurrentOffset, 0, mTargetEndOffset - mTargetCurrentOffset);
            invalidate();
        }
    }
}
