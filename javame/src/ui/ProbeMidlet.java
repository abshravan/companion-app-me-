package ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;

/**
 * A minimal diagnostic MIDlet that reports the device's Java ME capabilities.
 *
 * It deliberately references <b>only</b> {@code java.lang} and LCDUI — never any
 * optional API ({@code javax.bluetooth}, etc.) — so it loads and runs on every
 * MIDP 2.0 device. Use it to answer two questions on a phone where the main app
 * fails to start:
 *   1. Does this phone run our MIDlets at all, and on which CLDC/MIDP version?
 *   2. Does it expose JSR-82 (the Bluetooth API the companion needs)?
 *
 * If "bluetooth.api" shows "(none)", the device cannot be a RelayME companion.
 */
public final class ProbeMidlet extends MIDlet implements CommandListener {

    private final Command exit = new Command("Exit", Command.EXIT, 1);

    protected void startApp() {
        Form form = new Form("RelayME Probe");
        form.append("configuration: " + prop("microedition.configuration"));
        form.append("profiles: " + prop("microedition.profiles"));
        form.append("platform: " + prop("microedition.platform"));
        form.append("locale: " + prop("microedition.locale"));
        form.append("bluetooth.api: " + prop("bluetooth.api.version"));
        form.addCommand(exit);
        form.setCommandListener(this);
        Display.getDisplay(this).setCurrent(form);
    }

    private String prop(String key) {
        String value = System.getProperty(key);
        return value != null ? value : "(none)";
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    public void commandAction(Command command, Displayable displayable) {
        notifyDestroyed();
    }
}
