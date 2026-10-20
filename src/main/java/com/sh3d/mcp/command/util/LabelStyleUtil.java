package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.UserPreferences;

/** Shared default text style for labels, including those displayed in 3D. */
public final class LabelStyleUtil {

    /** Default label font size in Sweet Home 3D (UserPreferences.DEFAULT_TEXT_STYLE). */
    private static final float DEFAULT_FONT_SIZE = 18f;

    private LabelStyleUtil() {
    }

    /** The style Sweet Home 3D draws a label with when the label has none of its own. */
    public static TextStyle defaultStyle(UserPreferences preferences) {
        TextStyle style = preferences != null ? preferences.getDefaultTextStyle(Label.class) : null;
        return style != null ? style : new TextStyle(DEFAULT_FONT_SIZE);
    }
}
