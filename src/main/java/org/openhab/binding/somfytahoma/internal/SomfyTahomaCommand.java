package org.openhab.binding.somfytahoma.internal;

import java.util.ArrayList;

/**
 * Created by Ondřej Pečta on 12. 9. 2016.
 */
public class SomfyTahomaCommand {

    String command;
    ArrayList<String> params = new ArrayList<String>();

    public SomfyTahomaCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public ArrayList<String> getParams() {
        return params;
    }

    public void addParam(String param) {
        this.params.add(param);
    }
}
