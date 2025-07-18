# Traccar API Documentation

## Overview

Traccar provides a comprehensive REST API that allows developers to integrate with the system and build custom applications. The API follows RESTful principles and uses JSON for data exchange.

## API Specification

The complete API specification is available in OpenAPI format at `/openapi.yaml` in the repository root. This specification can be imported into tools like Swagger UI, Postman, or other API development tools for easier exploration and testing.

## Authentication

The API supports two authentication methods:

### Basic Authentication

```
Authorization: Basic base64(username:password)
```

### API Key Authentication

```
Authorization: Bearer your-api-key
```

API keys can be generated in the user settings of the Traccar web interface.

## Base URL

The base URL for API requests depends on your Traccar server installation:

- Default installation: `http://localhost:8082/api`
- Demo servers: `https://demo.traccar.org/api`

## Common Endpoints

Below is a summary of the main API endpoints. For complete details, refer to the OpenAPI specification.

### Server Information

- `GET /server` - Get server information
- `PUT /server` - Update server configuration (admin only)

### Session Management

- `POST /session` - Create a new session (login)
- `GET /session` - Get current session information
- `DELETE /session` - Delete current session (logout)

### Devices

- `GET /devices` - Get a list of devices
- `POST /devices` - Create a new device
- `PUT /devices/{id}` - Update a device
- `DELETE /devices/{id}` - Delete a device

### Positions

- `GET /positions` - Get positions with various filtering options
- `GET /positions/latest` - Get latest positions for devices

### Events

- `GET /events` - Get events with various filtering options

### Reports

- `GET /reports/route` - Generate a route report
- `GET /reports/events` - Generate an events report
- `GET /reports/summary` - Generate a summary report
- `GET /reports/trips` - Generate a trips report
- `GET /reports/stops` - Generate a stops report

### Geofences

- `GET /geofences` - Get a list of geofences
- `POST /geofences` - Create a new geofence
- `PUT /geofences/{id}` - Update a geofence
- `DELETE /geofences/{id}` - Delete a geofence

### Commands

- `POST /commands` - Send a command to a device
- `GET /commands` - Get a list of saved commands
- `PUT /commands/{id}` - Update a saved command
- `DELETE /commands/{id}` - Delete a saved command

## Response Format

API responses are in JSON format. A typical successful response contains the requested data:

```json
{
  "id": 1,
  "name": "Device Name",
  "uniqueId": "123456789",
  "status": "online",
  "lastUpdate": "2023-01-01T12:00:00Z"
}
```

For collection endpoints, the response is an array of objects:

```json
[
  {
    "id": 1,
    "name": "Device 1",
    "uniqueId": "123456789",
    "status": "online",
    "lastUpdate": "2023-01-01T12:00:00Z"
  },
  {
    "id": 2,
    "name": "Device 2",
    "uniqueId": "987654321",
    "status": "offline",
    "lastUpdate": "2023-01-02T14:30:00Z"
  }
]
```

## Error Handling

In case of an error, the API returns an appropriate HTTP status code along with a JSON response containing error details:

```json
{
  "error": {
    "code": 400,
    "message": "Invalid request parameters"
  }
}
```

Common HTTP status codes:
- 200 OK - The request was successful
- 400 Bad Request - The request was invalid
- 401 Unauthorized - Authentication is required
- 403 Forbidden - The user doesn't have permission
- 404 Not Found - The requested resource was not found
- 500 Internal Server Error - Server error

## Pagination

For endpoints that return collections, pagination is supported using the following query parameters:

- `?page=1` - Page number (starts from 1)
- `?limit=10` - Number of items per page

Example: `GET /devices?page=2&limit=10`

## Filtering

Many endpoints support filtering using query parameters:

- `?userId=123` - Filter by user ID
- `?deviceId=456` - Filter by device ID
- `?groupId=789` - Filter by group ID
- `?from=2023-01-01T00:00:00Z` - Filter by start date/time
- `?to=2023-01-31T23:59:59Z` - Filter by end date/time

## Example Requests

### Get all devices

```
GET /api/devices
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
```

### Create a new device

```
POST /api/devices
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
Content-Type: application/json

{
  "name": "My Device",
  "uniqueId": "123456789",
  "phone": "+1234567890",
  "model": "Model X",
  "category": "vehicle"
}
```

### Send a command to a device

```
POST /api/commands
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
Content-Type: application/json

{
  "deviceId": 123,
  "type": "engineStop",
  "attributes": {
    "data": "custom data"
  }
}
```

## API Clients

The Traccar API can be accessed using any HTTP client. Here are some examples:

- cURL
- Postman
- Programming language HTTP clients (e.g., Axios for JavaScript, Requests for Python)
- Generated clients from the OpenAPI specification

## Rate Limiting

The API may implement rate limiting to prevent abuse. If you exceed the rate limit, you'll receive a 429 Too Many Requests response. It's recommended to implement proper error handling and backoff strategies in your client applications.

## Further Resources

- [Traccar Website](https://www.traccar.org/)
- [Traccar Forum](https://www.traccar.org/forums/)
- [GitHub Repository](https://github.com/traccar/traccar)
