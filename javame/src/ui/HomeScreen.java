package ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;

/**
 * The Home screen — the only screen in Milestone 2.
 *
 * Built from plain LCDUI ({@link Form} + {@link StringItem}) to feel native on
 * Series 40 and to keep memory use minimal. It owns no Bluetooth or protocol
 * logic; it shows a status line and forwards menu commands to a {@link Controller}.
 *
 * Later milestones add the Notifications / Calls / Music / Battery / Settings
 * screens alongside this one.
 */
public final class HomeScreen implements CommandListener {

    /** Menu actions the screen delegates to its owner. */
    public interface Controller {
        void onConnectRequested();
        void onExitRequested();
    }

    private final Form form;
    private final StringItem status;
    private final Command connectCommand;
    private final Command exitCommand;
    private final Controller controller;

    public HomeScreen(Controller controller) {
        this.controller = controller;
        this.form = new Form("RelayME");
        this.status = new StringItem(null, "");
        this.form.append(status);

        this.connectCommand = new Command("Connect", Command.OK, 1);
        this.exitCommand = new Command("Exit", Command.EXIT, 1);
        this.form.addCommand(connectCommand);
        this.form.addCommand(exitCommand);
        this.form.setCommandListener(this);
    }

    /** The LCDUI displayable to hand to {@code Display.setCurrent}. */
    public Displayable getDisplayable() {
        return form;
    }

    /** Update the single status line shown to the user. */
    public void setStatus(String text) {
        status.setText(text);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == connectCommand) {
            controller.onConnectRequested();
        } else if (command == exitCommand) {
            controller.onExitRequested();
        }
    }
}
