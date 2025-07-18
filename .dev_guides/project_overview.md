# Traccar Project Overview

## Introduction

Traccar is an open-source GPS tracking system designed to track and manage various GPS tracking devices. It's a comprehensive solution that supports over 200 GPS protocols and more than 2000 models of GPS tracking devices. The project is licensed under the Apache License 2.0.

## Core Components

Traccar consists of several components:

1. **Traccar Server (This Repository)**: The Java-based backend service that handles device communication, data processing, and provides a REST API.
2. **Traccar Web App**: A web-based interface for users to interact with the system.
3. **Traccar Manager Apps**: Mobile applications for Android and iOS that allow management of the tracking system.
4. **Traccar Client Apps**: Mobile applications for Android and iOS that can be used to turn mobile devices into tracking devices.

## Key Features

- Real-time GPS tracking
- Support for 200+ GPS protocols
- Support for 2000+ GPS device models
- Driver behavior monitoring
- Detailed and summary reports
- Geofencing functionality
- Alarms and notifications
- Account and device management
- Email and SMS support
- REST API for integration with other systems

## Technology Stack

Traccar is built using the following technologies:

- **Java 17**: The primary programming language
- **Gradle**: Build system
- **Guice**: Dependency injection framework
- **Netty**: Network application framework
- **Jetty**: Web server
- **Jersey**: REST API framework
- **Jackson**: JSON processing
- **Liquibase**: Database migration
- **Multiple database support**: H2, MySQL, MariaDB, PostgreSQL, MS SQL Server

## System Requirements

- Java 17 or higher
- Database system (H2, MySQL, MariaDB, PostgreSQL, or MS SQL Server)
- Sufficient memory and CPU resources depending on the number of tracked devices

## Integration Capabilities

Traccar provides extensive integration capabilities:

- REST API for custom applications
- Event forwarding to external systems (JSON, AMQP, Kafka, MQTT)
- Position forwarding to external systems (JSON, AMQP, Kafka, MQTT, Redis, Wialon)
- Email and SMS notifications
- Authentication integration (LDAP, OpenID)
- Geocoding services integration (Google, Nominatim, and many others)
- Speed limit data providers

## Community and Support

Traccar is maintained by a dedicated team and has an active community. Commercial support options are also available through the official website.