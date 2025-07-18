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

import org.apache.poi.ss.util.WorkbookUtil;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.model.Position;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.DeviceReportSection;
import org.traccar.reports.model.FuelReportItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class FuelReportProvider {

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public FuelReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    public Collection<FuelReportItem> getObjects(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException {
        reportUtils.checkPeriodLimit(from, to);

        ArrayList<FuelReportItem> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            result.addAll(calculateFuelData(device, from, to));
        }
        return result;
    }

    private Collection<FuelReportItem> calculateFuelData(Device device, Date from, Date to) throws StorageException {
        ArrayList<FuelReportItem> result = new ArrayList<>();

        // Get positions for the device within the time range
        List<Position> positions = storage.getObjects(Position.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("deviceId", device.getId()),
                        new Condition.Between("fixTime", from, to))));

        if (positions.isEmpty()) {
            return result;
        }

        // Create a fuel report item
        FuelReportItem item = new FuelReportItem();
        item.setDeviceId(device.getId());
        item.setDeviceName(device.getName());
        item.setStartTime(from);
        item.setEndTime(to);

        // Calculate fuel data
        Position firstPosition = positions.get(0);
        Position lastPosition = positions.get(positions.size() - 1);

        // Set odometer values
        Double startOdometer = firstPosition.getDouble(Position.KEY_ODOMETER);
        if (startOdometer != null) {
            item.setStartOdometer(startOdometer);
        }
        Double endOdometer = lastPosition.getDouble(Position.KEY_ODOMETER);
        if (endOdometer != null) {
            item.setEndOdometer(endOdometer);
        }

        // Calculate distance
        double distance = item.getEndOdometer() - item.getStartOdometer();
        item.setDistance(distance);

        // Set fuel levels
        Double startFuelLevel = firstPosition.getDouble(Position.KEY_FUEL_LEVEL);
        if (startFuelLevel != null) {
            item.setStartFuelLevel(startFuelLevel);
        }
        Double endFuelLevel = lastPosition.getDouble(Position.KEY_FUEL_LEVEL);
        if (endFuelLevel != null) {
            item.setEndFuelLevel(endFuelLevel);
        }

        // Calculate fuel consumed
        double fuelConsumed = 0;
        for (int i = 1; i < positions.size(); i++) {
            Position prev = positions.get(i - 1);
            Position curr = positions.get(i);

            Double prevFuel = prev.getDouble(Position.KEY_FUEL_LEVEL);
            Double currFuel = curr.getDouble(Position.KEY_FUEL_LEVEL);

            if (prevFuel != null && currFuel != null && prevFuel > currFuel) {
                fuelConsumed += (prevFuel - currFuel);
            }
        }
        item.setFuelConsumed(fuelConsumed);
        item.setSpentFuel(fuelConsumed); // Also set the base class field

        // Calculate fuel consumption rate (liters per 100 km)
        if (distance > 0) {
            double rate = (fuelConsumed * 100) / distance;
            item.setFuelConsumptionRate(rate);
        }

        result.add(item);
        return result;
    }

    public void getExcel(
            OutputStream outputStream, long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException, IOException {
        reportUtils.checkPeriodLimit(from, to);

        ArrayList<DeviceReportSection> devicesFuel = new ArrayList<>();
        ArrayList<String> sheetNames = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            Collection<FuelReportItem> fuelData = calculateFuelData(device, from, to);
            DeviceReportSection deviceFuel = new DeviceReportSection();
            deviceFuel.setDeviceName(device.getName());
            sheetNames.add(WorkbookUtil.createSafeSheetName(deviceFuel.getDeviceName()));
            if (device.getGroupId() > 0) {
                Group group = storage.getObject(Group.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getGroupId())));
                if (group != null) {
                    deviceFuel.setGroupName(group.getName());
                }
            }
            deviceFuel.setObjects(fuelData);
            devicesFuel.add(deviceFuel);
        }

        File file = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", "fuel.xlsx").toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            var context = reportUtils.initializeContext(userId);
            context.putVar("devices", devicesFuel);
            context.putVar("sheetNames", sheetNames);
            context.putVar("from", from);
            context.putVar("to", to);
            reportUtils.processTemplateWithSheets(inputStream, outputStream, context);
        }
    }
}
