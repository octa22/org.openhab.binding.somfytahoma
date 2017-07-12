/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.somfytahoma.internal;

import com.google.gson.*;
import org.apache.commons.lang.StringUtils;
import org.openhab.binding.somfytahoma.SomfyTahomaBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

import static org.openhab.binding.somfytahoma.internal.SomfyTahomaConstants.*;


/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 *
 * @author opecta@gmail.com
 * @since 1.0.0-SNAPSHOT
 */

public class SomfyTahomaBinding extends AbstractActiveBinding<SomfyTahomaBindingProvider> {

    private static final Logger logger =
            LoggerFactory.getLogger(SomfyTahomaBinding.class);

    /**
     * The BundleContext. This is only valid when the bundle is ACTIVE. It is set in the activate()
     * method and must not be accessed anymore once the deactivate() method was called or before activate()
     * was called.
     */
    private BundleContext bundleContext;
    private ItemRegistry itemRegistry;

    /**
     * the refresh interval which is used to poll values from the SomfyTahoma
     * server (optional, defaults to 60000ms)
     */
    private long refreshInterval = 60000;
    private String email;
    private String password;
    private String cookie = "";
    private boolean loggedIn = false;

    //Gson parser
    private JsonParser parser = new JsonParser();

    public SomfyTahomaBinding() {
    }


    /**
     * Called by the SCR to activate the component with its configuration read from CAS
     *
     * @param bundleContext BundleContext of the Bundle that defines this component
     * @param configuration Configuration properties for this component obtained from the ConfigAdmin service
     */
    public void activate(final BundleContext bundleContext, final Map<String, Object> configuration) {
        this.bundleContext = bundleContext;

        // the configuration is guaranteed not to be null, because the component definition has the
        // configuration-policy set to require. If set to 'optional' then the configuration may be null


        readConfiguration(configuration);
        setProperlyConfigured(true);
    }

    private void listActionGroups() {
        String groups = getGroups();
        StringBuilder sb = new StringBuilder();

        JsonObject jobject = parser.parse(groups).getAsJsonObject();
        JsonArray jactionGroups = jobject.get("actionGroups").getAsJsonArray();
        for (JsonElement jactionGroup : jactionGroups) {
            jobject = jactionGroup.getAsJsonObject();
            String oid = ACTION_GROUP + jobject.get("oid").getAsString();
            String label = jobject.get("label").getAsString();
            if (!isBound(oid))
                sb.append("\tName: ").append(label).append(" URL: ").append(oid).append("\n");
        }
        if (sb.length() > 0) {
            logger.info("Found unbound action group(s): \n" + sb.toString());
        }
    }

    private String getGroups() {
        String url = null;

        try {
            url = TAHOMA_URL + "getActionGroups";
            String urlParameters = "";

            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            InputStream response = sendDataToTahomaWithCookie(url, postData);

            return readResponse(response);

        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (IOException e) {
            if (e.toString().contains(UNAUTHORIZED)) {
                loggedIn = false;
            }
            logger.error("Cannot send getActionGroups command: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send getActionGroups command: " + e.toString());
        }
        return "";
    }

    private void listDevices() {
        String url = null;

        try {
            url = TAHOMA_URL + "getSetup";
            String urlParameters = "";
            StringBuilder sb = new StringBuilder();

            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            InputStream response = sendDataToTahomaWithCookie(url, postData);

            String line = readResponse(response);

            JsonObject jobject = parser.parse(line).getAsJsonObject();
            jobject = jobject.get("setup").getAsJsonObject();
            for (JsonElement el : jobject.get("devices").getAsJsonArray()) {
                JsonObject obj = el.getAsJsonObject();
                if ("RollerShutter".equals(obj.get("uiClass").getAsString())) {
                    String label = obj.get("label").getAsString();
                    String deviceURL = obj.get("deviceURL").getAsString();
                    if (!isBound(deviceURL))
                        sb.append("\tName: ").append(label).append(" URL: ").append(deviceURL).append("\n");
                }
            }
            if (sb.length() > 0) {
                logger.info("Found unbound rollerShutter(s): \n" + sb.toString());
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (IOException e) {
            if (e.toString().contains(UNAUTHORIZED)) {
                loggedIn = false;
            }
            logger.error("Cannot send listDevices command: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send listDevices command: " + e.toString());
        }

    }

    private String readResponse(InputStream response) throws Exception {
        String line;
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(response));

        while ((line = reader.readLine()) != null) {
            body.append(line).append("\n");
        }
        line = body.toString();
        logger.debug(line);
        return line;
    }

    private void readConfiguration(final Map<String, Object> configuration) {
        // to override the default refresh interval one has to add a
        // parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
        String refreshIntervalString = (String) configuration.get("refresh");
        if (StringUtils.isNotBlank(refreshIntervalString)) {
            refreshInterval = Long.parseLong(refreshIntervalString);
        }

        String emailString = (String) configuration.get("email");
        if (StringUtils.isNotBlank(emailString)) {
            email = emailString;
        }

        String passwordString = (String) configuration.get("password");
        if (StringUtils.isNotBlank(passwordString)) {
            password = passwordString;
        }
    }

    /**
     * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin service.
     *
     * @param configuration Updated configuration properties
     */
    public void modified(final Map<String, Object> configuration) {
        // update the internal configuration accordingly
        if (configuration != null) {
            readConfiguration(configuration);
        }
    }

    /**
     * Called by the SCR to deactivate the component when either the configuration is removed or
     * mandatory references are no longer satisfied or the component has simply been stopped.
     *
     * @param reason Reason code for the deactivation:<br>
     *               <ul>
     *               <li> 0 – Unspecified
     *               <li> 1 – The component was disabled
     *               <li> 2 – A reference became unsatisfied
     *               <li> 3 – A configuration was changed
     *               <li> 4 – A configuration was deleted
     *               <li> 5 – The component was disposed
     *               <li> 6 – The bundle was stopped
     *               </ul>
     */
    public void deactivate(final int reason) {
        this.bundleContext = null;
        logout();
        // deallocate resources here that are no longer needed and
        // should be reset when activating this binding again
    }

    private void logout() {
        try {
            sendToTahomaWithCookie(TAHOMA_URL + "logout");
            cookie = "";
            loggedIn = false;
        } catch (Exception e) {
            logger.error("Cannot send logout command!");
        }
    }


    public void setItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public void unsetItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = null;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected String getName() {
        return "SomfyTahoma Refresh Service";
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void execute() {
        // the frequently executed code (polling) goes here ...
        //logger.debug("execute() method is called!");

        if (!bindingsExist()) {
            logger.debug("There is no existing Somfy Tahoma binding configuration => refresh cycle aborted!");
            return;
        }

        try {
            if (!loggedIn) {
                login();
                if (loggedIn) {
                    listDevices();
                    listActionGroups();
                }
            }
            updateTahomaStates();
        } catch (Exception ex) {
            logger.error(ex.toString());
            loggedIn = false;
        }
    }

    private void updateTahomaStates() {
        for (final SomfyTahomaBindingProvider provider : providers) {
            for (final String itemName : provider.getItemNames()) {

                SomfyTahomaBindingConfig config = (SomfyTahomaBindingConfig) provider.getItemConfig(itemName);
                if (config == null)
                    return;

                String deviceUrl = config.getType();
                if (deviceUrl.startsWith(ACTION_GROUP) || deviceUrl.equals(VERSION))
                    continue;

                int state = getState(deviceUrl);
                if (state == -1) {
                    //relogin
                    login();
                    state = getState(deviceUrl);
                }
                State newState = new PercentType(state);
                State oldState = null;
                try {
                    oldState = itemRegistry.getItem(itemName).getState();
                    if (!oldState.equals(newState))
                        eventPublisher.postUpdate(itemName, newState);
                } catch (ItemNotFoundException e) {
                    logger.error("Cannot find item " + itemName + " in item registry!");
                }
            }
        }
    }


    private void login() {
        String url = null;

        try {
            url = TAHOMA_URL + "login";
            String urlParameters = "userId=" + email + "&userPassword=" + password;
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            URL cookieUrl = new URL(url);
            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            setConnectionDefaults(connection);
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }

            //get cookie
            String headerName;
            for (int i = 1; (headerName = connection.getHeaderFieldKey(i)) != null; i++) {
                if (headerName.equals("Set-Cookie")) {
                    cookie = connection.getHeaderField(i);
                    break;
                }
            }

            InputStream response = connection.getInputStream();
            String line = readResponse(response);

            JsonObject jobject = parser.parse(line).getAsJsonObject();
            boolean success = jobject.get("success").getAsBoolean();

            if (success) {
                String version = jobject.get(VERSION).getAsString();
                logger.debug("SomfyTahoma cookie: " + cookie);
                logger.info("SomfyTahoma version: " + version);
                String versionItem = getVersionItem();
                if (versionItem != null && version != null) {
                    State oldState = itemRegistry.getItem(versionItem).getState();
                    State newState = new StringType(version);
                    if (!newState.equals(oldState)) {
                        eventPublisher.postUpdate(versionItem, newState);
                    }
                }
                loggedIn = true;
            } else {
                logger.debug("Login response: " + line);
                loggedIn = false;
                throw new SomfyTahomaException(line);
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot get login cookie: " + e.toString());
        }
    }

    private String getVersionItem() {

        for (final SomfyTahomaBindingProvider provider : providers) {
            for (final String name : provider.getItemNames()) {
                SomfyTahomaBindingConfig config = (SomfyTahomaBindingConfig) provider.getItemConfig(name);
                if (config == null)
                    continue;
                if (config.getType().equals(VERSION)) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveCommand(String itemName, Command command) {
        // the code being executed when a command was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        logger.debug("internalReceiveCommand({},{}) is called!", itemName, command);
        String type = getTahomaDeviceUrl(itemName);
        if (type.startsWith(ACTION_GROUP)) {
            if ("ON".equals(command.toString())) {
                ArrayList<SomfyTahomaAction> actions = getTahomaActions(type.substring(ACTION_GROUP.length()));
                for (SomfyTahomaAction action : actions) {
                    sendCommand(action);
                }
            }
        } else {
            String cmd = getTahomaCommand(command.toString());
            //Check if the rollershutter is not moving
            String executionId = getCurrentExecutions(type);
            if (executionId != null) {
                //STOP command should be interpreted if rollershutter moving
                //otherwise do nothing
                if (cmd.equals("my")) {
                    cancelExecution(executionId);
                }
            } else {
                String param = cmd.equals("setClosure") ? "[" + command.toString() + "]" : "[]";
                sendCommand(type, cmd, param);
            }
        }
    }

    private void cancelExecution(String executionId) {
        String url = null;

        try {
            url = DELETE_URL + executionId;
            sendDeleteToTahomaWithCookie(url);

        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (IOException e) {
            if (e.toString().contains(UNAUTHORIZED)) {
                loggedIn = false;
            }
            logger.error("Cannot cancel execution: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot cancel execution: " + e.toString());
        }
    }

    private String getCurrentExecutions(String type) {
        String url = null;

        try {
            url = TAHOMA_URL + "getCurrentExecutions";

            String urlParameters = "";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            InputStream response = sendDataToTahomaWithCookie(url, postData);
            String line = readResponse(response);

            JsonObject jobject = parser.parse(line).getAsJsonObject();
            JsonArray jarray = jobject.get("executions").getAsJsonArray();

            return parseExecutions(type, jarray);

        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (IOException e) {
            if (e.toString().contains(UNAUTHORIZED)) {
                loggedIn = false;
            }
            logger.error("Cannot send getCurrentExecutions command: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send getCurrentExecutions command: " + e.toString());
        }

        return null;
    }

    private String parseExecutions(String type, JsonArray executions) {
        for (JsonElement execution : executions) {
            JsonObject jobject = execution.getAsJsonObject().get("actionGroup").getAsJsonObject();
            String execId = execution.getAsJsonObject().get("id").getAsString();
            JsonArray actions = jobject.get("actions").getAsJsonArray();
            for (JsonElement action : actions) {
                jobject = action.getAsJsonObject();
                if (jobject.get("deviceURL").getAsString().equals(type))
                    return execId;
            }
        }
        return null;
    }

    private ArrayList<SomfyTahomaAction> getTahomaActions(String actionGroup) {
        String groups = getGroups();
        ArrayList<SomfyTahomaAction> actions = new ArrayList<>();

        JsonObject jobject = parser.parse(groups).getAsJsonObject();
        JsonArray jactionGroups = jobject.get("actionGroups").getAsJsonArray();
        for (JsonElement jactionGroup : jactionGroups) {
            jobject = jactionGroup.getAsJsonObject();
            String oid = jobject.get("oid").getAsString();
            if (actionGroup.equals(oid)) {
                JsonArray jactions = jobject.get("actions").getAsJsonArray();
                for (JsonElement jactionElement : jactions) {
                    jobject = jactionElement.getAsJsonObject();
                    SomfyTahomaAction action = new SomfyTahomaAction();
                    action.setDeviceURL(jobject.get("deviceURL").getAsString());
                    JsonArray jcommands = jobject.get("commands").getAsJsonArray();
                    for (JsonElement jcommandElement : jcommands) {
                        JsonObject jcommand = jcommandElement.getAsJsonObject();
                        String name = jcommand.get("name").getAsString();
                        SomfyTahomaCommand cmd = new SomfyTahomaCommand(name);
                        for (JsonElement jparamElement : jcommand.get("parameters").getAsJsonArray()) {
                            cmd.addParam(jparamElement.getAsJsonObject().getAsString());
                        }
                        action.addCommand(cmd);
                    }

                    actions.add(action);
                }
                break;
            }
        }
        return actions;

    }

    private String getTahomaCommand(String command) {

        switch (command) {
            case "DOWN":
                return "down";
            case "UP":
                return "up";
            case "STOP":
                return "my";
            default:
                return "setClosure";
        }
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveUpdate(String itemName, State newState) {
        // the code being executed when a state was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        logger.info("internalReceiveUpdate({},{}) is called!", itemName, newState);
    }


    private String getTahomaDeviceUrl(String itemName) {

        for (final SomfyTahomaBindingProvider provider : providers) {
            if (provider.getItemNames().contains(itemName)) {
                SomfyTahomaBindingConfig config = (SomfyTahomaBindingConfig) provider.getItemConfig(itemName);
                return config.getType();
            }

        }
        return "";
    }

    private boolean isBound(String io) {

        for (final SomfyTahomaBindingProvider provider : providers) {
            for (final String name : provider.getItemNames()) {
                SomfyTahomaBindingConfig config = (SomfyTahomaBindingConfig) provider.getItemConfig(name);
                if (config == null)
                    continue;

                String type = config.getType();
                if (type.equals(io))
                    return true;
            }
        }
        return false;
    }

    private void sendCommand(SomfyTahomaAction action) {

        Gson gson = new Gson();
        for (SomfyTahomaCommand command : action.getCommands()) {
            logger.debug("Sending to device " + action.getDeviceURL() + " command " + command.getCommand() + " params " + gson.toJson(command.getParams()));
            sendCommand(action.getDeviceURL(), command.getCommand(), gson.toJson(command.getParams()));
        }

    }

    private void sendCommand(String io, String command, String params) {
        String url = null;

        try {
            url = TAHOMA_URL + "apply";

            String urlParameters = "{\"actions\": [{\"deviceURL\": \"" + io + "\", \"commands\": [{ \"name\": \"" + command + "\", \"parameters\": " + params + "}]}]}";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            InputStream response = sendDataToTahomaWithCookie(url, postData);
            String line = readResponse(response);

            JsonObject jobject = parser.parse(line).getAsJsonObject();
            String execId = jobject.get("execId").getAsString();

            if (!"".equals(execId)) {
                logger.debug("Exec id: " + execId);
            } else {
                logger.debug("Command response: " + line);
                throw new SomfyTahomaException(line);
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (IOException e) {
            if (e.toString().contains(UNAUTHORIZED)) {
                loggedIn = false;
            }
            logger.error("Cannot send apply command: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send apply command: " + e.toString());
        }
    }

    private int getState(String io) {
        String url = null;

        try {
            url = TAHOMA_URL + "getStates";
            String urlParameters = "[{\"deviceURL\": \"" + io + "\", \"states\": [{\"name\": \"core:ClosureState\"}]}]";

            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            InputStream response = sendDataToTahomaWithCookie(url, postData);
            String line = readResponse(response);

            JsonObject jobject = parser.parse(line).getAsJsonObject();
            jobject = jobject.get("devices").getAsJsonArray().get(0).getAsJsonObject();
            jobject = jobject.get("states").getAsJsonArray().get(0).getAsJsonObject();
            int state = jobject.get("value").getAsInt();

            if (state >= 0) {
                logger.debug("State: " + state);
                return state;
            } else {
                logger.debug("GetState response: " + line);
                throw new SomfyTahomaException(line);
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (IOException e) {
            if (e.toString().contains(UNAUTHORIZED)) {
                loggedIn = false;
                return -1;
            }
        } catch (Exception e) {
            logger.error("Cannot send getStates command: " + e.toString());
        }

        return 0;
    }

    private InputStream sendDataToTahomaWithCookie(String url, byte[] postData) throws Exception {

        URL cookieUrl = new URL(url);
        HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        setConnectionDefaults(connection);
        connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
        connection.setRequestProperty("Cookie", cookie);
        try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
            wr.write(postData);
        }

        return connection.getInputStream();
    }

    private InputStream sendToTahomaWithCookie(String url) throws Exception {

        URL cookieUrl = new URL(url);
        HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
        connection.setDoOutput(false);
        connection.setRequestMethod("GET");
        setConnectionDefaults(connection);
        connection.setRequestProperty("Cookie", cookie);

        return connection.getInputStream();
    }

    private InputStream sendDeleteToTahomaWithCookie(String url) throws Exception {

        URL cookieUrl = new URL(url);
        HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
        connection.setDoOutput(false);
        connection.setRequestMethod("DELETE");
        setConnectionDefaults(connection);
        connection.setRequestProperty("Cookie", cookie);

        return connection.getInputStream();
    }

    private void setConnectionDefaults(HttpsURLConnection connection) {
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", TAHOMA_AGENT);
        connection.setRequestProperty("Accept-Language", "de-de");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setUseCaches(false);
    }
}
