package fr.vriege.anilib.platform.desktopextensionhost.compat.android.view;

public class View {
    private boolean enabled = true;

    public View() {
    }

    public View getRootView() {
        return this;
    }

    public View findViewById(int id) {
        return null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
