package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public class DialogPreference extends Preference {
    private CharSequence dialogTitle;
    private CharSequence dialogMessage;
    private int dialogIcon;
    private int dialogLayoutResource;
    private CharSequence positiveButtonText;
    private CharSequence negativeButtonText;
    public DialogPreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public DialogPreference(Context context) { super(context); }
    public CharSequence getDialogTitle() { return dialogTitle; }
    public void setDialogTitle(CharSequence value) { dialogTitle = value; }
    public CharSequence getDialogMessage() { return dialogMessage; }
    public void setDialogMessage(CharSequence value) { dialogMessage = value; }
    public int getDialogIcon() { return dialogIcon; }
    public void setDialogIcon(int value) { dialogIcon = value; }
    public int getDialogLayoutResource() { return dialogLayoutResource; }
    public void setDialogLayoutResource(int value) { dialogLayoutResource = value; }
    public CharSequence getPositiveButtonText() { return positiveButtonText; }
    public void setPositiveButtonText(CharSequence value) { positiveButtonText = value; }
    public CharSequence getNegativeButtonText() { return negativeButtonText; }
    public void setNegativeButtonText(CharSequence value) { negativeButtonText = value; }
    public void setDialogTitle(int resourceId) { }
    public void setDialogMessage(int resourceId) { }
    public void setPositiveButtonText(int resourceId) { }
    public void setNegativeButtonText(int resourceId) { }
}
