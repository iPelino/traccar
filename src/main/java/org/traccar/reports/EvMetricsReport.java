/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.reports;

import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.reports.model.EvMetricsReportItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class EvMetricsReport {

    private final Storage storage;

    @Inject
    public EvMetricsReport(Storage storage) {
        this.storage = storage;
    }

    public Collection<EvMetricsReportItem> getObjects(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException {

        ArrayList<EvMetricsReportItem> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            result.addAll(getEvMetrics(device, from, to));
        }
        return result;
    }

    private Collection<EvMetricsReportItem> getEvMetrics(Device device, Date from, Date to) throws StorageException {
        ArrayList<EvMetricsReportItem> result = new ArrayList<>();

        // Get positions for the device within the time range
        List<Position> positions = storage.getObjects(Position.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("deviceId", device.getId()),
                        new Condition.Between("fixTime", from, to)),
                new Order("fixTime")));

        if (positions.isEmpty()) {
            return result;
        }

        // Process positions to extract EV metrics
        for (Position position : positions) {
            // Check if position has any EV-related attributes
            if (hasEvAttributes(position)) {
                EvMetricsReportItem item = new EvMetricsReportItem();
                item.setDeviceId(device.getId());
                item.setDeviceName(device.getName());
                item.setTime(position.getFixTime());
                item.setLatitude(position.getLatitude());
                item.setLongitude(position.getLongitude());

                // Extract EV-specific metrics
                Double batteryLevel = position.getDouble(Position.KEY_BATTERY_LEVEL);
                if (batteryLevel != null) {
                    item.setBatteryLevel(batteryLevel);
                }

                Double motorTemp = position.getDouble("motorTemperature");
                if (motorTemp != null) {
                    item.setMotorTemperature(motorTemp);
                }

                Double rpm = position.getDouble(Position.KEY_RPM);
                if (rpm != null) {
                    item.setRpm(rpm);
                }

                String dtcs = position.getString(Position.KEY_DTCS);
                if (dtcs != null) {
                    item.setDtcs(dtcs);
                }

                Double power = position.getDouble("power");
                if (power != null) {
                    item.setPower(power);
                }

                Double range = position.getDouble("range");
                if (range != null) {
                    item.setRange(range);
                }

                Double chargingRate = position.getDouble("chargingRate");
                if (chargingRate != null) {
                    item.setChargingRate(chargingRate);
                }

                Boolean charging = position.getBoolean("charging");
                if (charging != null) {
                    item.setCharging(charging);
                }

                result.add(item);
            }
        }

        return result;
    }

    private boolean hasEvAttributes(Position position) {
        // Check if position has any EV-related attributes
        return position.hasAttribute(Position.KEY_BATTERY_LEVEL)
                || position.hasAttribute("motorTemperature")
                || position.hasAttribute(Position.KEY_RPM)
                || position.hasAttribute(Position.KEY_DTCS)
                || position.hasAttribute("power")
                || position.hasAttribute("range")
                || position.hasAttribute("chargingRate")
                || position.hasAttribute("charging");
    }
}
