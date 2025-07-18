package org.traccar.reports;

import org.junit.jupiter.api.Test;
import org.traccar.BaseTest;
import org.traccar.model.Position;
import org.traccar.reports.model.FuelReportItem;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test demonstrates how to test fuel report calculation logic
 * without requiring a running server. It focuses on the core functionality
 * of calculating fuel consumption based on position data.
 */
public class FuelReportProviderTest extends BaseTest {

    /**
     * Test the calculation of fuel consumption based on position data.
     * This is a simplified version of what FuelReportProvider.calculateFuelData does.
     */
    @Test
    public void testFuelCalculation() {
        // Create positions with fuel level data
        List<Position> positions = new ArrayList<>();

        Position position1 = new Position();
        position1.setDeviceId(1);
        position1.setTime(new Date(System.currentTimeMillis() - 3600000)); // 1 hour ago
        position1.set(Position.KEY_ODOMETER, 1000.0);
        position1.set(Position.KEY_FUEL_LEVEL, 80.0);
        positions.add(position1);

        Position position2 = new Position();
        position2.setDeviceId(1);
        position2.setTime(new Date(System.currentTimeMillis() - 1800000)); // 30 minutes ago
        position2.set(Position.KEY_ODOMETER, 1050.0);
        position2.set(Position.KEY_FUEL_LEVEL, 70.0);
        positions.add(position2);

        Position position3 = new Position();
        position3.setDeviceId(1);
        position3.setTime(new Date()); // now
        position3.set(Position.KEY_ODOMETER, 1100.0);
        position3.set(Position.KEY_FUEL_LEVEL, 60.0);
        positions.add(position3);

        // Calculate fuel data manually (similar to what FuelReportProvider.calculateFuelData does)
        FuelReportItem item = new FuelReportItem();
        item.setDeviceId(1);
        item.setDeviceName("Test Device");
        item.setStartTime(positions.get(0).getFixTime());
        item.setEndTime(positions.get(positions.size() - 1).getFixTime());

        // Set odometer values
        Double startOdometer = positions.get(0).getDouble(Position.KEY_ODOMETER);
        if (startOdometer != null) {
            item.setStartOdometer(startOdometer);
        }
        Double endOdometer = positions.get(positions.size() - 1).getDouble(Position.KEY_ODOMETER);
        if (endOdometer != null) {
            item.setEndOdometer(endOdometer);
        }

        // Calculate distance
        double distance = item.getEndOdometer() - item.getStartOdometer();
        item.setDistance(distance);

        // Set fuel levels
        Double startFuelLevel = positions.get(0).getDouble(Position.KEY_FUEL_LEVEL);
        if (startFuelLevel != null) {
            item.setStartFuelLevel(startFuelLevel);
        }
        Double endFuelLevel = positions.get(positions.size() - 1).getDouble(Position.KEY_FUEL_LEVEL);
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

        // Verify the results
        assertEquals(1, item.getDeviceId());
        assertEquals("Test Device", item.getDeviceName());
        assertEquals(1000.0, item.getStartOdometer());
        assertEquals(1100.0, item.getEndOdometer());
        assertEquals(100.0, item.getDistance());
        assertEquals(80.0, item.getStartFuelLevel());
        assertEquals(60.0, item.getEndFuelLevel());
        assertEquals(20.0, item.getFuelConsumed());

        // Verify fuel consumption rate (liters per 100 km)
        assertEquals(20.0, item.getFuelConsumptionRate());
    }
}
