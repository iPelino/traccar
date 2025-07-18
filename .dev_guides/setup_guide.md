# Traccar Setup Guide

## System Requirements

- Java 17 or higher
- Database system (H2, MySQL, MariaDB, PostgreSQL, or MS SQL Server)
- Minimum 1GB RAM (2GB or more recommended for production)
- 1GB free disk space (plus storage for database)
- Network connectivity for device communication

## Installation Methods

### Method 1: Using Pre-built Packages

Traccar provides pre-built packages for various operating systems:

#### Windows

1. Download the Windows installer from the [official website](https://www.traccar.org/download/).
2. Run the installer and follow the on-screen instructions.
3. The service will be installed and started automatically.
4. Access the web interface at `http://localhost:8082`.

#### Linux (Debian/Ubuntu)

1. Download the Linux package:
   ```
   wget https://github.com/traccar/traccar/releases/download/v6.8.1/traccar-linux-6.8.1.zip
   ```

2. Unzip the package:
   ```
   unzip traccar-linux-6.8.1.zip
   ```

3. Run the installer:
   ```
   sudo ./traccar.run
   ```

4. Start the service:
   ```
   sudo systemctl start traccar
   ```

5. Access the web interface at `http://localhost:8082`.

#### Linux (Other Distributions)

1. Download the Linux package.
2. Unzip the package.
3. Run the installer script.
4. Start the service using your distribution's service manager.

### Method 2: Building from Source

1. Clone the repository:
   ```
   git clone https://github.com/traccar/traccar.git
   ```

2. Navigate to the project directory:
   ```
   cd traccar
   ```

3. Build the project using Gradle:
   ```
   ./gradlew build
   ```

4. The built application will be in the `target` directory.

5. Run the application:
   ```
   java -jar target/traccar-6.8.1.jar debug.xml
   ```

## Configuration

Traccar is configured using an XML configuration file. The default configuration file is `conf/traccar.xml`.

### Basic Configuration

Here's a sample configuration file with basic settings:

```xml
<?xml version="1.0"?>
<config>
    <web>
        <port>8082</port>
    </web>
    <database>
        <driver>org.h2.Driver</driver>
        <url>jdbc:h2:./data/database</url>
        <user>sa</user>
        <password></password>
    </database>
</config>
```

### Database Configuration

#### H2 (Default)

```xml
<database>
    <driver>org.h2.Driver</driver>
    <url>jdbc:h2:./data/database</url>
    <user>sa</user>
    <password></password>
</database>
```

#### MySQL

```xml
<database>
    <driver>com.mysql.jdbc.Driver</driver>
    <url>jdbc:mysql://localhost:3306/traccar?serverTimezone=UTC&amp;useSSL=false&amp;allowMultiQueries=true&amp;autoReconnect=true&amp;useUnicode=yes&amp;characterEncoding=UTF-8&amp;sessionVariables=sql_mode=''</url>
    <user>root</user>
    <password>password</password>
</database>
```

#### PostgreSQL

```xml
<database>
    <driver>org.postgresql.Driver</driver>
    <url>jdbc:postgresql://localhost:5432/traccar</url>
    <user>postgres</user>
    <password>password</password>
</database>
```

### Web Interface Configuration

```xml
<web>
    <port>8082</port>
    <path>/opt/traccar/web</path>
    <address>localhost</address>
</web>
```

### Protocol Configuration

To enable a specific protocol, add a configuration entry with the protocol name and port:

```xml
<protocol name="gt06">
    <port>5023</port>
</protocol>
```

You can enable multiple protocols:

```xml
<protocol name="gt06">
    <port>5023</port>
</protocol>
<protocol name="h02">
    <port>5024</port>
</protocol>
<protocol name="teltonika">
    <port>5025</port>
</protocol>
```

### Geocoder Configuration

To enable geocoding (converting coordinates to addresses):

```xml
<geocoder>
    <type>nominatim</type>
    <url>https://nominatim.openstreetmap.org/reverse</url>
    <key></key>
    <language>en</language>
</geocoder>
```

### Email Configuration

To enable email notifications:

```xml
<mail>
    <smtp.host>smtp.example.com</smtp.host>
    <smtp.port>587</smtp.port>
    <smtp.starttls.enable>true</smtp.starttls.enable>
    <smtp.auth>true</smtp.auth>
    <smtp.username>user@example.com</smtp.username>
    <smtp.password>password</smtp.password>
    <smtp.from>user@example.com</smtp.from>
</mail>
```

### SMS Configuration

To enable SMS notifications using an HTTP gateway:

```xml
<sms>
    <http.url>https://api.example.com/sms</http.url>
    <http.authorization>Bearer token</http.authorization>
</sms>
```

## First-time Setup

1. After installation, access the web interface at `http://localhost:8082` (or the configured address and port).
2. Log in with the default credentials:
   - Username: `admin`
   - Password: `admin`
3. Change the default password immediately.
4. Add devices to the system:
   - Go to "Devices" and click "Add"
   - Enter a name and the device's unique identifier
   - Save the device
5. Configure user accounts and permissions as needed.

## Upgrading

### Windows

1. Download the new installer.
2. Run the installer, which will automatically upgrade the existing installation.
3. The service will be restarted automatically.

### Linux

1. Stop the service:
   ```
   sudo systemctl stop traccar
   ```

2. Download and install the new version using the same steps as for a new installation.

3. Start the service:
   ```
   sudo systemctl start traccar
   ```

## Backup and Restore

### Database Backup

#### H2 Database

1. Stop the Traccar service.
2. Copy the `data/database.mv.db` file to a safe location.
3. Start the Traccar service.

#### MySQL Database

```
mysqldump -u username -p traccar > traccar_backup.sql
```

#### PostgreSQL Database

```
pg_dump -U username traccar > traccar_backup.sql
```

### Configuration Backup

Make a copy of the configuration file (`conf/traccar.xml`) and any custom templates or resources.

## Troubleshooting

### Common Issues

1. **Service fails to start**
   - Check the logs in the `logs` directory
   - Verify Java version (`java -version`)
   - Check database connectivity
   - Ensure required ports are not in use

2. **Devices not connecting**
   - Verify the protocol configuration
   - Check firewall settings
   - Ensure the device is properly configured with the correct server address and port

3. **Web interface not accessible**
   - Check if the service is running
   - Verify web server configuration
   - Check firewall settings

### Logs

Log files are stored in the `logs` directory. The main log file is `tracker-server.log`.

To increase logging verbosity, add the following to the configuration file:

```xml
<logger>
    <level>DEBUG</level>
</logger>
```

## Additional Resources

- [Official Documentation](https://www.traccar.org/documentation/)
- [Traccar Forums](https://www.traccar.org/forums/)
- [GitHub Repository](https://github.com/traccar/traccar)