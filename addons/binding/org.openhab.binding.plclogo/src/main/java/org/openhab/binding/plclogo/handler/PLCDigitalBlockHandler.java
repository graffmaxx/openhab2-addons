/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.plclogo.handler;

import static org.openhab.binding.plclogo.PLCLogoBindingConstants.*;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.plclogo.config.PLCLogoDigitalConfiguration;
import org.openhab.binding.plclogo.internal.PLCLogoClient;
import org.openhab.binding.plclogo.internal.PLCLogoDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Moka7.S7;
import Moka7.S7Client;

/**
 * The {@link PLCDigitalBlockHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
public class PLCDigitalBlockHandler extends PLCBlockHandler {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_DIGITAL);

    private final Logger logger = LoggerFactory.getLogger(PLCDigitalBlockHandler.class);

    private PLCLogoDigitalConfiguration config = getConfigAs(PLCLogoDigitalConfiguration.class);
    private Integer oldValue = Integer.MAX_VALUE;

    /**
     * Constructor.
     */
    public PLCDigitalBlockHandler(@NonNull Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(@NonNull ChannelUID channelUID, @NonNull Command command) {
        logger.debug("Handle command {} on channel {}", command, channelUID);

        final Bridge bridge = getBridge();
        Objects.requireNonNull(bridge, "PLCDigitalBlockHandler: Bridge may not be null.");

        final Thing thing = getThing();
        Objects.requireNonNull(thing, "PLCDigitalBlockHandler: Thing may not be null.");
        if ((ThingStatus.ONLINE != thing.getStatus()) || (ThingStatus.ONLINE != bridge.getStatus())) {
            return;
        }

        final String channelId = channelUID.getId();
        final PLCLogoClient client = getLogoClient();
        if (!DIGITAL_CHANNEL_ID.equals(channelId) || (client == null)) {
            logger.warn("Can not update channel {}: {}.", channelUID, client);
            return;
        }

        if (command instanceof RefreshType) {
            super.handleCommand(channelUID, command);
        } else if ((command instanceof OpenClosedType) || (command instanceof OnOffType)) {
            final String name = getBlockName();
            final int offset = getBlockDataType(name).getByteCount();
            if (offset > 0) {
                final byte[] buffer = new byte[offset];
                if (command instanceof OpenClosedType) {
                    final OpenClosedType state = (OpenClosedType) command;
                    S7.SetBitAt(buffer, 0, 0, state == OpenClosedType.CLOSED);
                } else {
                    final OnOffType state = (OnOffType) command;
                    S7.SetBitAt(buffer, 0, 0, state == OnOffType.ON);
                }

                final int address = 8 * getAddress(name) + getBit(name);
                int result = client.writeDBArea(1, address, buffer.length, S7Client.S7WLBit, buffer);
                if (result != 0) {
                    logger.warn("Can not write data to LOGO!: {}.", S7Client.ErrorText(result));
                }
            } else {
                logger.warn("Invalid block {} found.", name);
            }
        } else {
            logger.debug("Not supported command {} received.", command);
        }
    }

    @Override
    public void initialize() {
        synchronized (config) {
            config = getConfigAs(PLCLogoDigitalConfiguration.class);
        }

        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                doInitialization();
            }
        });
    }

    @Override
    public void dispose() {
        logger.debug("Dispose LOGO! {} digital handler.", getBlockName());
        super.dispose();

        synchronized (oldValue) {
            oldValue = Integer.MAX_VALUE;
        }
    }

    @Override
    public void setData(final byte[] data) {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "PLCDigitalBlockHandler: Thing may not be null.");
        if (ThingStatus.ONLINE != thing.getStatus()) {
            return;
        }

        final String name = getBlockName();
        final Channel channel = thing.getChannel(DIGITAL_CHANNEL_ID);
        if ((data.length == 1) && (channel != null)) {
            final boolean value = S7.GetBitAt(data, 0, getBit(name));

            final String type = channel.getAcceptedItemType();
            if (logger.isTraceEnabled()) {
                final String raw = "[" + Integer.toBinaryString((data[0] & 0xFF) + 0x100).substring(1) + "]";
                logger.trace("Channel {} accepting {} received {}.", channel.getUID(), type, raw);
            }

            if ((oldValue != (value ? 1 : 0)) || config.isUpdateForced()) {
                if (type.equalsIgnoreCase("Contact")) {
                    updateState(channel.getUID(), value ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                } else if (type.equalsIgnoreCase("Switch")) {
                    updateState(channel.getUID(), value ? OnOffType.ON : OnOffType.OFF);
                } else {
                    logger.warn("Channel {} will not accept {} items.", channel.getUID(), type);
                }
                logger.debug("Channel {} accepting {} was set to {}.", channel.getUID(), type, value);

                synchronized (oldValue) {
                    oldValue = value ? 1 : 0;
                }
            }
        } else {
            logger.warn("Block {} received wrong data {}.", name, data);
        }
    }

    @Override
    public int getAddress(final @NonNull String name) {
        int address = INVALID;

        logger.debug("Get address of {} LOGO! for block {} .", getLogoFamily(), name);

        if (config.isBlockValid(name)) {
            final String block = name.trim().split("\\.")[0];
            if (Character.isDigit(block.charAt(1))) {
                address = Integer.parseInt(block.substring(1));
            } else if (Character.isDigit(block.charAt(2))) {
                address = Integer.parseInt(block.substring(2));
            }

            final int base = getBase(name);
            if (base != 0) { // Only VB/VD/VW memory ranges are 0 based
                address = base + (address - 1) / 8;
            }
        } else {
            logger.warn("Wrong configurated LOGO! block {} found.", name);
        }
        return address;
    }

    /**
     * Calculate bit within address for block with given name.
     *
     * @param name Name of the LOGO! block
     * @return Calculated bit
     */
    public int getBit(final @NonNull String name) {
        int bit = INVALID;

        logger.debug("Get bit of {} LOGO! for block {} .", getLogoFamily(), name);

        if (config.isBlockValid(name)) {
            final String[] parts = name.trim().split("\\.");
            if (Character.isDigit(parts[0].charAt(1))) {
                bit = Integer.parseInt(parts[0].substring(1));
            } else if (Character.isDigit(parts[0].charAt(2))) {
                bit = Integer.parseInt(parts[0].substring(2));
            }

            if (getBase(name) != 0) { // Only VB/VD/VW memory ranges are 0 based
                bit = (bit - 1) % 8;
            } else {
                bit = Integer.parseInt(parts[1]);
            }
        } else {
            logger.warn("Wrong configurated LOGO! block {} found.", name);
        }
        return bit;
    }

    @Override
    public @NonNull String getBlockName() {
        return config.getBlockName();
    }

    @Override
    public @NonNull PLCLogoDataType getBlockDataType(final @NonNull String name) {
        final String kind = config.getBlockKind(name);
        if ((kind != null) && config.isBlockValid(name)) {
            return PLCLogoDataType.BIT;
        }
        return PLCLogoDataType.INVALID;
    }

    @Override
    protected void updateConfiguration(Configuration configuration) {
        super.updateConfiguration(configuration);
        synchronized (config) {
            config = getConfigAs(PLCLogoDigitalConfiguration.class);
        }
    }

    @Override
    protected void doInitialization() {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "PLCDigitalBlockHandler: Thing may not be null.");

        final Bridge bridge = getBridge();
        Objects.requireNonNull(bridge, "PLCDigitalBlockHandler: Bridge may not be null.");

        final String name = getBlockName();
        logger.debug("Initialize LOGO! {} digital handler.", name);

        if (config.isBlockValid(name)) {
            ThingBuilder tBuilder = editThing();

            String text = config.isInputBlock(name) ? INPUT_CHANNEL : OUTPUT_CHANNEL;
            text = text.substring(0, 1).toUpperCase() + text.substring(1);
            tBuilder.withLabel(bridge.getLabel() + ": " + text + " " + name);

            final Channel channel = thing.getChannel(DIGITAL_CHANNEL_ID);
            if (channel != null) {
                tBuilder.withoutChannel(channel.getUID());
            }

            final String type = config.getItemType();
            final ChannelUID uid = new ChannelUID(thing.getUID(), DIGITAL_CHANNEL_ID);
            ChannelBuilder cBuilder = ChannelBuilder.create(uid, type);
            cBuilder.withType(new ChannelTypeUID(BINDING_ID, type.toLowerCase()));
            cBuilder.withLabel(name);
            cBuilder.withDescription("Digital " + text);
            tBuilder.withChannel(cBuilder.build());

            synchronized (oldValue) {
                oldValue = Integer.MAX_VALUE;
            }
            updateThing(tBuilder.build());
            super.doInitialization();
        } else {
            final String message = "Can not initialize LOGO! block " + name + ".";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
            logger.error("Can not initialize thing {} for LOGO! block {}.", thing.getUID(), name);
        }
    }

    /**
     * Calculate address offset for given block name.
     *
     * @param name Name of the data block
     * @return Calculated address offset
     */
    private int getBase(final @NonNull String name) {
        int base = 0;

        logger.debug("Get base address of {} LOGO! for block {} .", getLogoFamily(), name);

        final String block = name.trim().split("\\.")[0];
        final Map<?, Integer> family = LOGO_MEMORY_BLOCK.get(getLogoFamily());
        if (Character.isDigit(block.charAt(1))) {
            base = family.get(block.substring(0, 1));
        } else if (Character.isDigit(block.charAt(2))) {
            base = family.get(block.substring(0, 2));
        }
        return base;
    }

}