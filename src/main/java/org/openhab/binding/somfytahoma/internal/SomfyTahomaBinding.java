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
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

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


    /**
     * the refresh interval which is used to poll values from the SomfyTahoma
     * server (optional, defaults to 60000ms)
     */
    private long refreshInterval = 60000;
    private String email;
    private String password;
    private String cookie = "";

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
        login();
        listDevices();
        listActionGroups();
        setProperlyConfigured(cookie != "");
    }

    private void listActionGroups() {
        String groups = getGroups();
        StringBuilder sb = new StringBuilder();

        JsonObject jobject = parser.parse(groups).getAsJsonObject();
        JsonArray jactionGroups = jobject.get("actionGroups").getAsJsonArray();
        for (JsonElement jactionGroup : jactionGroups) {
            jobject = jactionGroup.getAsJsonObject();
            String oid = ACTION_GROUP+jobject.get("oid").getAsString();
            String label = jobject.get("label").getAsString();
            if (!isBound(oid))
                sb.append("\tName: " + label + " URL: " + oid + "\n");
        }
        if( sb.length() > 0 )
        {
            logger.info("Found unbound Somfy Tahoma action group(s): \n" +  sb.toString());
        }
    }

    private String getGroups() {
        String url = null;
        StringBuilder body = null;

        try {
            url = TAHOMA_URL + "getActionGroups";
            String urlParameters = "";

            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            InputStream response = sendDataToTahomaWithCookie(url, postData);

            String line = readResponse(response);
            return line;

        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send Somfy Tahoma getSetup command: " + e.toString());
        }
        return "";
    }

    private void listDevices() {
        String url = null;
        StringBuilder body = null;

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
                        sb.append("\tName: " + label + " URL: " + deviceURL + "\n");
                }
            }
            if( sb.length() > 0 )
            {
                logger.info("Found unbound Somfy Tahoma RollerShutter(s): \n" +  sb.toString());
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send Somfy Tahoma getSetup command: " + e.toString());
        }

    }

    private String readResponse(InputStream response) throws Exception {
        String line;
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(response));

        try {
            while ((line = reader.readLine()) != null) {
                body.append(line + "\n");
            }
            line = body.toString();
            logger.debug(line);
            return line;
        } catch (Exception ex) {
            throw ex;
        }
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
        readConfiguration(configuration);
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
        // deallocate resources here that are no longer needed and
        // should be reset when activating this binding again
    }


    /**
     * TODO
     */
    protected String getEmail() {
        return email;
    }

    /**
     * TODO
     */
    protected String getPassword() {
        return password;
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
        logger.debug("execute() method is called!");

        if (!bindingsExist()) {
            logger.debug("There is no existing Somfy Tahoma binding configuration => refresh cycle aborted!");
            return;
        }

        for (final SomfyTahomaBindingProvider provider : providers) {
            for (final String itemName : provider.getItemNames()) {
                String deviceUrl = provider.getItemType(itemName);
                if (deviceUrl.startsWith(ACTION_GROUP))
                    continue;

                int state = getState(deviceUrl);
                if (provider.getItemState(itemName) != state) {
                    provider.setItemState(itemName, state);
                    eventPublisher.postUpdate(itemName, new PercentType(state));
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
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", TAHOMA_AGENT);
            connection.setRequestProperty("Accept-Language", "de-de");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
            connection.setUseCaches(false);
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
                String version = jobject.get("version").getAsString();
                logger.debug("SomfyTahoma cookie: " + cookie);
                logger.info("SomfyTahoma version: " + version);
            } else {
                logger.debug("SomfyTahoma login response: " + line);
                throw new SomfyTahomaException(line);
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot get SomfyTahoma login cookie: " + e.toString());
        }
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
            String param = cmd.equals("setClosure") ? "[" + command.toString() + "]" : "[]";
            sendCommand(type, cmd, param);
        }
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
            for (final String name : provider.getItemNames()) {
                if (itemName.equals(name)) {
                    return provider.getItemType(itemName);
                }
            }
        }
        return "";
    }

    private boolean isBound(String io) {

        for (final SomfyTahomaBindingProvider provider : providers) {
            for (final String name : provider.getItemNames()) {
                String type = provider.getItemType(name);
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

            //String command = getTahomaCommand(cmd.toString());
            //String param = command.equals("setClosure") ? cmd : "";
            String urlParameters = "{\"actions\": [{\"deviceURL\": \"" + io + "\", \"commands\": [{ \"name\": \"" + command + "\", \"parameters\": " + params + "}]}]}";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);


            InputStream response = sendDataToTahomaWithCookie(url, postData);
            //BufferedReader reader = new BufferedReader(new InputStreamReader(response));
            String line = readResponse(response);

            JsonObject jobject = parser.parse(line).getAsJsonObject();
            String execId = jobject.get("execId").getAsString();

            if (!"".equals(execId)) {
                logger.debug("Somfy Tahoma exec id: " + execId);
            } else {
                logger.debug("Somfy Tahoma command response: " + line);
                throw new SomfyTahomaException(line);
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send Somfy Tahoma command: " + e.toString());
        }
    }

    private int getState(String io) {
        String url = null;

        try {
            url = TAHOMA_URL + "getStates";
            //String urlParameters  = "[{\"deviceURL\": \""+ io + "\", \"states\": [{\"name\": \"core:OpenClosedState\"}]}]";
            String urlParameters = "[{\"deviceURL\": \"" + io + "\", \"states\": [{\"name\": \"core:ClosureState\"}]}]";

            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            InputStream response = sendDataToTahomaWithCookie(url, postData);

            //BufferedReader reader = new BufferedReader(new InputStreamReader(response));
            String line = readResponse(response);

            JsonObject jobject = parser.parse(line).getAsJsonObject();
            jobject = jobject.get("devices").getAsJsonArray().get(0).getAsJsonObject();
            jobject = jobject.get("states").getAsJsonArray().get(0).getAsJsonObject();
            int state = jobject.get("value").getAsInt();

            if (state >= 0) {
                logger.debug("Somfy Tahoma state: " + state);
                return state;
            } else {
                logger.debug("Somfy Tahoma getState response: " + line);
                throw new SomfyTahomaException(line);
            }
        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot send Somfy Tahoma getStates command: " + e.toString());
        }

        return 0;
    }

    private InputStream sendDataToTahomaWithCookie(String url, byte[] postData) throws Exception {

        try {
            URL cookieUrl = new URL(url);
            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", TAHOMA_AGENT);
            connection.setRequestProperty("Accept-Language", "de-de");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
            connection.setUseCaches(false);
            connection.setRequestProperty("Cookie", cookie);
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }

            return connection.getInputStream();
        } catch (Exception ex) {
            throw ex;
        }
    }
}
