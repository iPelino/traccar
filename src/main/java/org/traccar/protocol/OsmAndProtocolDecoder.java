/*
 * Copyright 2013 - 2025 Anton Tananaev (anton@traccar.org)
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
package org.traccar.protocol;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.helper.UnitsConverter;
import org.traccar.session.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.DateUtil;
import org.traccar.model.CellTower;
import org.traccar.model.Command;
import org.traccar.model.Network;
import org.traccar.model.Position;
import org.traccar.model.WifiAccessPoint;

import java.io.StringReader;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class OsmAndProtocolDecoder extends BaseHttpProtocolDecoder {

    public OsmAndProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected Object decode(Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        FullHttpRequest request = (FullHttpRequest) msg;
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null && contentType.startsWith(HttpHeaderValues.APPLICATION_JSON.toString())) {
            return decodeJson(channel, remoteAddress, request);
        } else {
            return decodeQuery(channel, remoteAddress, request);
        }
    }

    private Object decodeQuery(
            Channel channel, SocketAddress remoteAddress, FullHttpRequest request) throws Exception {

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Map<String, List<String>> params = decoder.parameters();
        if (params.isEmpty()) {
            decoder = new QueryStringDecoder(request.content().toString(StandardCharsets.US_ASCII), false);
            params = decoder.parameters();
        }

        Position position = new Position(getProtocolName());
        position.setValid(true);

        Network network = new Network();
        Double latitude = null;
        Double longitude = null;

        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            for (String value : entry.getValue()) {
                switch (entry.getKey()) {
                    case "id":
                    case "deviceid":
                        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, value);
                        if (deviceSession == null) {
                            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
                            return null;
                        }
                        position.setDeviceId(deviceSession.getDeviceId());
                        break;
                    case "notificationToken":
                        if (position.getDeviceId() > 0) {
                            getCommandsManager().updateNotificationToken(position.getDeviceId(), value);
                        }
                        break;
                    case "valid":
                        position.setValid(Boolean.parseBoolean(value) || "1".equals(value));
                        break;
                    case "timestamp":
                        try {
                            long timestamp = Long.parseLong(value);
                            if (timestamp < Integer.MAX_VALUE) {
                                timestamp *= 1000;
                            }
                            position.setTime(new Date(timestamp));
                        } catch (NumberFormatException error) {
                            if (value.contains("T")) {
                                position.setTime(DateUtil.parseDate(value));
                            } else {
                                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                position.setTime(dateFormat.parse(value));
                            }
                        }
                        break;
                    case "lat":
                        latitude = Double.parseDouble(value);
                        break;
                    case "lon":
                        longitude = Double.parseDouble(value);
                        break;
                    case "location":
                        String[] location = value.split(",");
                        latitude = Double.parseDouble(location[0]);
                        longitude = Double.parseDouble(location[1]);
                        break;
                    case "cell":
                        String[] cell = value.split(",");
                        if (cell.length > 4) {
                            network.addCellTower(CellTower.from(
                                    Integer.parseInt(cell[0]), Integer.parseInt(cell[1]),
                                    Integer.parseInt(cell[2]), Integer.parseInt(cell[3]), Integer.parseInt(cell[4])));
                        } else {
                            network.addCellTower(CellTower.from(
                                    Integer.parseInt(cell[0]), Integer.parseInt(cell[1]),
                                    Integer.parseInt(cell[2]), Integer.parseInt(cell[3])));
                        }
                        break;
                    case "wifi":
                        String[] wifi = value.split(",");
                        network.addWifiAccessPoint(WifiAccessPoint.from(
                                wifi[0].replace('-', ':'), Integer.parseInt(wifi[1])));
                        break;
                    case "speed":
                        position.setSpeed(convertSpeed(Double.parseDouble(value), "kn"));
                        break;
                    case "bearing":
                    case "heading":
                        position.setCourse(Double.parseDouble(value));
                        break;
                    case "altitude":
                        position.setAltitude(Double.parseDouble(value));
                        break;
                    case "accuracy":
                        position.setAccuracy(Double.parseDouble(value));
                        break;
                    case "hdop":
                        position.set(Position.KEY_HDOP, Double.parseDouble(value));
                        break;
                    case "batt":
                        position.set(Position.KEY_BATTERY_LEVEL, Double.parseDouble(value));
                        break;
                    case "driverUniqueId":
                        position.set(Position.KEY_DRIVER_UNIQUE_ID, value);
                        break;
                    case "charge":
                        position.set(Position.KEY_CHARGE, Boolean.parseBoolean(value));
                        break;
                    default:
                        try {
                            position.set(entry.getKey(), Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            switch (value) {
                                case "true" -> position.set(entry.getKey(), true);
                                case "false" -> position.set(entry.getKey(), false);
                                default -> position.set(entry.getKey(), value);
                            }
                        }
                        break;
                }
            }
        }

        if (position.getFixTime() == null) {
            position.setTime(new Date());
        }

        if (network.getCellTowers() != null || network.getWifiAccessPoints() != null) {
            position.setNetwork(network);
        }

        if (latitude != null && longitude != null) {
            position.setLatitude(latitude);
            position.setLongitude(longitude);
        } else {
            getLastLocation(position, position.getDeviceTime());
        }

        if (position.getDeviceId() != 0) {
            String response = null;
            for (Command command : getCommandsManager().readQueuedCommands(position.getDeviceId(), 1)) {
                response = command.getString(Command.KEY_DATA);
            }
            if (response != null) {
                sendResponse(channel, HttpResponseStatus.OK, Unpooled.copiedBuffer(response, StandardCharsets.UTF_8));
            } else {
                sendResponse(channel, HttpResponseStatus.OK);
            }
            return position;
        } else {
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }
    }

    private Object decodeJson(
            Channel channel, SocketAddress remoteAddress, FullHttpRequest request) throws Exception {

        String content = request.content().toString(StandardCharsets.UTF_8);
        JsonObject root = Json.createReader(new StringReader(content)).readObject();

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, root.getString("device_id"));
        if (deviceSession == null) {
            sendResponse(channel, HttpResponseStatus.NOT_FOUND);
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        JsonObject location = root.getJsonObject("location");

        position.setTime(DateUtil.parseDate(location.getString("timestamp")));

        if (location.containsKey("coords")) {
            JsonObject coordinates = location.getJsonObject("coords");
            position.setValid(true);
            position.setLatitude(coordinates.getJsonNumber("latitude").doubleValue());
            position.setLongitude(coordinates.getJsonNumber("longitude").doubleValue());
            double speed = coordinates.getJsonNumber("speed").doubleValue();
            if (speed >= 0) {
                position.setSpeed(UnitsConverter.knotsFromMps(speed));
            }
            double heading = coordinates.getJsonNumber("heading").doubleValue();
            if (heading >= 0) {
                position.setCourse(heading);
            }
            if (speed >= 0 || heading >= 0) {
                position.setAccuracy(coordinates.getJsonNumber("accuracy").doubleValue());
            }
            position.setAltitude(coordinates.getJsonNumber("altitude").doubleValue());
        } else {
            getLastLocation(position, null);
        }

        if (location.containsKey("event")) {
            position.set(Position.KEY_EVENT, location.getString("event"));
        }
        if (location.containsKey("is_moving")) {
            position.set(Position.KEY_MOTION, location.getBoolean("is_moving"));
        }
        if (location.containsKey("odometer")) {
            position.set(Position.KEY_ODOMETER, location.getInt("odometer"));
        }
        if (location.containsKey("mock")) {
            position.set("mock", location.getBoolean("mock"));
        }
        if (location.containsKey("activity")) {
            position.set("activity", location.getJsonObject("activity").getString("type"));
        }
        if (location.containsKey("battery")) {
            JsonObject battery = location.getJsonObject("battery");
            double level = battery.getJsonNumber("level").doubleValue();
            if (level >= 0) {
                position.set(Position.KEY_BATTERY_LEVEL, (int) (level * 100));
            }
            if (battery.getBoolean("is_charging")) {
                position.set(Position.KEY_CHARGE, true);
            }
        }

        if (location.containsKey("alarm")) {
            position.set(Position.KEY_ALARM, location.getString("alarm"));
        } else if (location.containsKey("extras")) {
            JsonObject extras = location.getJsonObject("extras");
            if (extras.containsKey("alarm")) {
                position.set(Position.KEY_ALARM, extras.getString("alarm"));
            }
        }

        sendResponse(channel, HttpResponseStatus.OK);
        return position;
    }

    @Override
    protected void sendQueuedCommands(Channel channel, SocketAddress remoteAddress, long deviceId) {
    }

}
