/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nest.internal.data;

import com.google.gson.annotations.SerializedName;

/**
 * Data for the Nest smoke detector.
 *
 * @author David Bennett - Initial Contribution
 */
public class SmokeDetector extends BaseNestDevice {
    @SerializedName("battery_health")
    private BatteryHealth batteryHealth;
    @SerializedName("co_alarm_state")
    private AlarmState coAlarmState;
    @SerializedName("smoke_alarm_state")
    private AlarmState smokeAlarmState;
    @SerializedName("is_manual_test_active")
    private boolean isManualTestActive;
    @SerializedName("ui_color_state")
    private UiColorState uiColorState;

    public UiColorState getUiColorState() {
        return uiColorState;
    }

    public BatteryHealth getBatteryHealth() {
        return batteryHealth;
    }

    public AlarmState getCoAlarmState() {
        return coAlarmState;
    }

    public AlarmState getSmokeAlarmState() {
        return smokeAlarmState;
    }

    public boolean isManualTestActive() {
        return isManualTestActive;
    }

    public enum BatteryHealth {
        @SerializedName("ok")
        OK,
        @SerializedName("replace")
        REPLACE
    }

    public enum AlarmState {
        @SerializedName("ok")
        OK,
        @SerializedName("emergency")
        EMERGENCY,
        @SerializedName("warning")
        WARNING
    }

    public enum UiColorState {
        @SerializedName("gray")
        GRAY,
        @SerializedName("green")
        GREEN,
        @SerializedName("yellow")
        YELLOW,
        @SerializedName("red")
        RED
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SmokeDetector [batteryHealth=").append(batteryHealth).append(", coAlarmState=")
                .append(coAlarmState).append(", smokeAlarmState=").append(smokeAlarmState)
                .append(", isManualTestActive=").append(isManualTestActive).append(", uiColorState=")
                .append(uiColorState).append(", getId()=").append(getId()).append(", getName()=").append(getName())
                .append(", getDeviceId()=").append(getDeviceId()).append(", getLastConnection()=")
                .append(getLastConnection()).append(", isOnline()=").append(isOnline()).append(", getNameLong()=")
                .append(getNameLong()).append(", getSoftwareVersion()=").append(getSoftwareVersion())
                .append(", getStructureId()=").append(getStructureId()).append(", getWhereId()=").append(getWhereId())
                .append("]");
        return builder.toString();
    }

}
