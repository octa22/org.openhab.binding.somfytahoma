/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.somfytahoma.internal;

import org.openhab.binding.somfytahoma.SomfyTahomaBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.*;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;

import static org.openhab.binding.somfytahoma.internal.SomfyTahomaConstants.*;


/**
 * This class is responsible for parsing the binding configuration.
 *
 * @author opecta@gmail.com
 * @since 1.0.0-SNAPSHOT
 */
public class SomfyTahomaGenericBindingProvider extends AbstractGenericBindingProvider implements SomfyTahomaBindingProvider {

    /**
     * {@inheritDoc}
     */
    public String getBindingType() {
        return "somfytahoma";
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {


        if (!(item instanceof RollershutterItem || item instanceof DimmerItem || item instanceof SwitchItem || item instanceof StringItem ))
        {
            throw new BindingConfigParseException("item '" + item.getName()
                    + "' is of type '" + item.getClass().getSimpleName()
                    + "', only RollershutterItem/DimmerItem/SwitchItem/StringItem are allowed - please check your *.items configuration");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
        super.processBindingConfiguration(context, item, bindingConfig);

        SomfyTahomaBindingConfig config = new SomfyTahomaBindingConfig(bindingConfig);

        //parse bindingconfig here ...

        addBindingConfig(item, config);

    }

    public String getItemType(String itemName) {
        final SomfyTahomaBindingConfig config = (SomfyTahomaBindingConfig) this.bindingConfigs.get(itemName);
        return config != null ? (config.getType()) : null;
    }

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


}
