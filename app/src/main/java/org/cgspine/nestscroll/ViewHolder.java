package org.cgspine.nestscroll;

import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;

/**
 * @author cginechen
 * @date 2016-12-29
 */

public class ViewHolder extends RecyclerView.ViewHolder {

    private TextView mItemView;

    public ViewHolder(TextView itemView) {
        super(itemView);
        mItemView = itemView;
    }

    public void setText(String text) {
        mItemView.setText(text);
    }
}
