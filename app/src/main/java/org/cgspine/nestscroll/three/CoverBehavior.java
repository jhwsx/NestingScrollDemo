package org.cgspine.nestscroll.three;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import android.util.Log;
import android.view.View;

/**
 * @author cginechen
 * @date 2016-12-29
 */

public class CoverBehavior extends CoordinatorLayout.Behavior<View> {
    private static final String TAG = "CoverBehavior";
    private int mHeaderInitOffset;
    private int mHeaderCurrentOffset;
    private int mHeaderEndOffset;

    public CoverBehavior(int headerInitOffset, int headerEndOffset) {
        mHeaderInitOffset = headerInitOffset;
        mHeaderEndOffset = headerEndOffset;
        mHeaderCurrentOffset = mHeaderInitOffset;
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, View child, int layoutDirection) {
        int width = parent.getWidth();
        int childWidth = child.getMeasuredWidth();
        int childHeight = child.getMeasuredHeight();
        child.layout((width / 2 - childWidth / 2), mHeaderCurrentOffset,
                (width / 2 + childWidth / 2), mHeaderCurrentOffset + childHeight);
        return true;
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
        Log.i(TAG, "layoutDependsOn");
        // 这里绝对依赖于谁？CoordinatorLayout会一个个询问child的兄弟元素，看是否依赖于它
        // demo中我就让它依赖于拥有TargetBehavior的view
        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) dependency.getLayoutParams();
        if (lp.getBehavior() instanceof TargetBehavior) {
            return true;
        }
        return super.layoutDependsOn(parent, child, dependency);
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
        // 当依赖View发生变化时，child就可以相应做出一些改变
        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) dependency.getLayoutParams();
        if (lp.getBehavior() instanceof TargetBehavior) {
            TargetBehavior behavior = (TargetBehavior) lp.getBehavior();
            moveHeaderView(behavior, child);
            return true;
        }
        return super.onDependentViewChanged(parent, child, dependency);
    }


    private void moveHeaderView(TargetBehavior behavior, View view) {
        int targetInitOffset = behavior.getTargetInitOffset();
        int targetEndOffset = behavior.getTargetEndOffset();
        int targetCurrentOffset = behavior.getTargetCurrentOffset();
        Log.i(TAG, "moveHeaderView: targetCurrentOffset = " + targetCurrentOffset);
        int target;
        if (targetCurrentOffset >= targetInitOffset) {
            target = mHeaderInitOffset;
        } else if (targetCurrentOffset <= targetEndOffset) {
            target = mHeaderEndOffset;
        } else {
            float percent = (targetCurrentOffset - targetEndOffset) * 1.0f / targetInitOffset - targetEndOffset;
            target = (int) (mHeaderEndOffset + percent * (mHeaderInitOffset - mHeaderEndOffset));
        }
        ViewCompat.offsetTopAndBottom(view, target - mHeaderCurrentOffset);
        mHeaderCurrentOffset = target;
    }
}
