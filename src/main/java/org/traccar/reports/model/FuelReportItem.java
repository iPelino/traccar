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
package org.traccar.reports.model;

public class FuelReportItem extends BaseReportItem {

    private double startFuelLevel;

    public double getStartFuelLevel() {
        return startFuelLevel;
    }

    public void setStartFuelLevel(double startFuelLevel) {
        this.startFuelLevel = startFuelLevel;
    }

    private double endFuelLevel;

    public double getEndFuelLevel() {
        return endFuelLevel;
    }

    public void setEndFuelLevel(double endFuelLevel) {
        this.endFuelLevel = endFuelLevel;
    }

    private double fuelConsumed;

    public double getFuelConsumed() {
        return fuelConsumed;
    }

    public void setFuelConsumed(double fuelConsumed) {
        this.fuelConsumed = fuelConsumed;
    }

    private double fuelCost;

    public double getFuelCost() {
        return fuelCost;
    }

    public void setFuelCost(double fuelCost) {
        this.fuelCost = fuelCost;
    }

    private double fuelConsumptionRate; // liters per 100 km or gallons per 100 miles

    public double getFuelConsumptionRate() {
        return fuelConsumptionRate;
    }

    public void setFuelConsumptionRate(double fuelConsumptionRate) {
        this.fuelConsumptionRate = fuelConsumptionRate;
    }

}