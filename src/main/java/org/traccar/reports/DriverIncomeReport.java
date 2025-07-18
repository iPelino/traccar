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
import org.traccar.model.Driver;
import org.traccar.model.DriverIncome;
import org.traccar.model.User;
import org.traccar.model.UserRole;
import org.traccar.reports.model.DriverIncomeReportItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverIncomeReport {

    private final Config config;
    private final Storage storage;

    @Inject
    public DriverIncomeReport(Config config, Storage storage) {
        this.config = config;
        this.storage = storage;
    }

    public Collection<DriverIncomeReportItem> getObjects(
            long userId, long driverId, Date from, Date to, String period) throws StorageException {

        User user = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", userId)));

        ArrayList<DriverIncomeReportItem> result = new ArrayList<>();

        // Build query conditions
        var conditions = new ArrayList<Condition>();

        // Filter by driver if specified
        if (driverId > 0) {
            conditions.add(new Condition.Equals("driverId", driverId));
        }

        // Filter by date range
        if (from != null && to != null) {
            conditions.add(new Condition.Between("date", from, to));
        }

        // Filter by period if specified
        if (period != null && !period.isEmpty()) {
            conditions.add(new Condition.Equals("period", period));
        }

        // Apply company-based filtering for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
        }

        // Get income records
        List<DriverIncome> incomeRecords = storage.getObjects(DriverIncome.class, new Request(
                new Columns.All(), 
                Condition.merge(conditions),
                new Order("date")));

        // Get all relevant drivers
        Map<Long, Driver> drivers = new HashMap<>();
        for (DriverIncome income : incomeRecords) {
            if (!drivers.containsKey(income.getDriverId())) {
                Driver driver = storage.getObject(Driver.class, new Request(
                        new Columns.All(), new Condition.Equals("id", income.getDriverId())));
                if (driver != null) {
                    drivers.put(driver.getId(), driver);
                }
            }
        }

        // Create report items
        for (DriverIncome income : incomeRecords) {
            Driver driver = drivers.get(income.getDriverId());
            if (driver != null) {
                DriverIncomeReportItem item = new DriverIncomeReportItem();
                item.setDriverId(driver.getId());
                item.setDriverName(driver.getName());
                item.setAmount(income.getAmount());
                item.setDescription(income.getDescription());
                item.setDate(income.getDate());
                item.setPeriod(income.getPeriod());
                result.add(item);
            }
        }

        return result;
    }

    public Collection<DriverIncomeReportItem> getSummary(
            long userId, long driverId, String period) throws StorageException {

        User user = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", userId)));

        ArrayList<DriverIncomeReportItem> result = new ArrayList<>();

        // Build query conditions
        var conditions = new ArrayList<Condition>();

        // Filter by driver if specified
        if (driverId > 0) {
            conditions.add(new Condition.Equals("driverId", driverId));
        }

        // Filter by period if specified
        if (period != null && !period.isEmpty()) {
            conditions.add(new Condition.Equals("period", period));
        }

        // Apply company-based filtering for non-super users
        if (user.getRole() != UserRole.SUPER_USER && user.getCompanyId() > 0) {
            conditions.add(new Condition.Equals("companyId", user.getCompanyId()));
        }

        // Get income records
        List<DriverIncome> incomeRecords = storage.getObjects(DriverIncome.class, new Request(
                new Columns.All(), 
                Condition.merge(conditions)));

        // Get all relevant drivers
        Map<Long, Driver> drivers = new HashMap<>();
        for (DriverIncome income : incomeRecords) {
            if (!drivers.containsKey(income.getDriverId())) {
                Driver driver = storage.getObject(Driver.class, new Request(
                        new Columns.All(), new Condition.Equals("id", income.getDriverId())));
                if (driver != null) {
                    drivers.put(driver.getId(), driver);
                }
            }
        }

        // Create summary map (driver ID -> total amount)
        Map<Long, Double> driverTotals = new HashMap<>();
        for (DriverIncome income : incomeRecords) {
            driverTotals.merge(income.getDriverId(), income.getAmount(), Double::sum);
        }

        // Create report items
        for (Map.Entry<Long, Double> entry : driverTotals.entrySet()) {
            Driver driver = drivers.get(entry.getKey());
            if (driver != null) {
                DriverIncomeReportItem item = new DriverIncomeReportItem();
                item.setDriverId(driver.getId());
                item.setDriverName(driver.getName());
                item.setAmount(entry.getValue());
                item.setPeriod(period);
                result.add(item);
            }
        }

        return result;
    }

    public void getExcel(
            OutputStream outputStream, long userId, long driverId, 
            Date from, Date to, String period) throws StorageException, IOException {

        Collection<DriverIncomeReportItem> reportItems = getObjects(userId, driverId, from, to, period);

        File file = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", "driver-income.xlsx").toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            var context = new org.jxls.common.Context();
            context.putVar("items", reportItems);
            context.putVar("from", from);
            context.putVar("to", to);
            context.putVar("period", period);

            org.jxls.util.JxlsHelper.getInstance().processTemplate(inputStream, outputStream, context);
        }
    }
}
