package org.openhab.binding.somfytahoma.internal;

import java.util.ArrayList;

/**
 * Created by Ondřej Pečta on 12. 9. 2016.
 */
public class SomfyTahomaAction {

    private String deviceURL;
    private ArrayList<SomfyTahomaCommand> commands = new ArrayList<SomfyTahomaCommand>();

    public String getDeviceURL() {
        return deviceURL;
    }

    public void setDeviceURL(String deviceURL) {
        this.deviceURL = deviceURL;
    }

    public ArrayList<SomfyTahomaCommand> getCommands() {
        return commands;
    }

    public void addCommand(SomfyTahomaCommand command) {
        this.commands.add(command);
    }
}
