package org.openhab.binding.somfytahoma.internal;

import org.openhab.core.binding.BindingConfig;

/**
 * This is a helper class holding binding specific configuration details
 *
 * @author opecta@gmail.com
 * @since 1.0.0-SNAPSHOT
 */
class SomfyTahomaBindingConfig implements BindingConfig {

    // put member fields here which holds the parsed values
    private String type;

    SomfyTahomaBindingConfig(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
