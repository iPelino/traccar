# Traccar Development Guide

## Development Environment Setup

### Prerequisites

- Java Development Kit (JDK) 17 or higher
- Git
- Gradle (or use the included Gradle wrapper)
- IDE of your choice (IntelliJ IDEA, Eclipse, VS Code, etc.)
- Database (H2, MySQL, PostgreSQL, etc.)

### Setting Up the Development Environment

1. Clone the repository:
   ```
   git clone https://github.com/traccar/traccar.git
   cd traccar
   ```

2. Build the project:
   ```
   ./gradlew build
   ```

3. Run the application in development mode:
   ```
   java -jar target/traccar-6.8.1.jar debug.xml
   ```

4. Access the web interface at `http://localhost:8082`

## Project Structure

```
traccar/
├── schema/                  # Database schema files
├── setup/                   # Setup and installation scripts
├── src/
│   ├── main/
│   │   ├── java/            # Java source code
│   │   │   └── org/
│   │   │       └── traccar/ # Main package
│   │   ├── proto/           # Protocol buffer definitions
│   │   └── resources/       # Resource files
│   └── test/                # Test source code
├── templates/               # Email and notification templates
├── tools/                   # Development tools
├── traccar-web/             # Web interface submodule
├── build.gradle             # Gradle build configuration
├── debug.xml                # Development configuration
└── README.md                # Project documentation
```

### Key Packages

- `org.traccar` - Core application classes
- `org.traccar.api` - REST API resources
- `org.traccar.database` - Database access
- `org.traccar.model` - Data models
- `org.traccar.protocol` - Protocol implementations
- `org.traccar.handler` - Data processing handlers
- `org.traccar.geocoder` - Geocoding services
- `org.traccar.geolocation` - Geolocation services
- `org.traccar.notification` - Notification services
- `org.traccar.reports` - Report generation
- `org.traccar.web` - Web server

## Architecture Overview

Traccar follows a modular architecture with dependency injection using Google Guice. The main components are:

1. **Main** - Application entry point
2. **ServerManager** - Manages protocol implementations
3. **WebServer** - Handles HTTP requests and serves the web interface
4. **Storage** - Database access layer
5. **Protocol Handlers** - Implementations for different GPS protocols
6. **Data Processing Pipeline** - Handlers for processing position data

## Adding a New Protocol

To add support for a new GPS protocol:

1. Create a new class in the `org.traccar.protocol` package that extends `BaseProtocol`:

```java
package org.traccar.protocol;

import org.traccar.BaseProtocol;
import org.traccar.PipelineBuilder;
import org.traccar.TrackerConnector;
import org.traccar.config.Config;

import jakarta.inject.Inject;
import java.util.List;

public class NewProtocol extends BaseProtocol {

    @Inject
    public NewProtocol(Config config) {
        super(config, "new");
        addServer(new TrackerConnector(config, getName(), port -> {
            return new PipelineBuilder() {
                @Override
                protected void addProtocolHandlers(PipelineBuilder pipeline) {
                    pipeline.addLast(new NewProtocolDecoder(NewProtocol.this));
                }
            };
        }));
    }
}
```

2. Create a decoder class that extends `BaseProtocolDecoder`:

```java
package org.traccar.protocol;

import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.Protocol;
import org.traccar.helper.model.AttributeUtil;
import org.traccar.model.Position;

import java.net.SocketAddress;

public class NewProtocolDecoder extends BaseProtocolDecoder {

    public NewProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {
        
        // Protocol-specific decoding logic
        // ...
        
        // Create and populate position object
        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceId);
        position.setTime(time);
        position.setLatitude(latitude);
        position.setLongitude(longitude);
        // ...
        
        return position;
    }
}
```

3. Add tests for the new protocol in the `src/test/java/org/traccar/protocol` package.

4. Enable the protocol in the configuration file:

```xml
<protocol name="new">
    <port>5040</port>
</protocol>
```

## Adding a New API Endpoint

To add a new REST API endpoint:

1. Create a model class in the `org.traccar.model` package:

```java
package org.traccar.model;

public class NewEntity extends ExtendedModel {

    private String name;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    // Additional properties and methods
}
```

2. Create a resource class in the `org.traccar.api.resource` package:

```java
package org.traccar.api.resource;

import org.traccar.api.SimpleObjectResource;
import org.traccar.model.NewEntity;
import org.traccar.storage.Storage;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("newentities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NewEntityResource extends SimpleObjectResource<NewEntity> {

    @Inject
    public NewEntityResource(Storage storage) {
        super(NewEntity.class, storage);
    }
}
```

3. Update the OpenAPI specification in `openapi.yaml` to document the new endpoint.

## Testing

### Unit Tests

Traccar uses JUnit for unit testing. To run the tests:

```
./gradlew test
```

Unit tests are located in the `src/test/java` directory.

### Writing Tests

1. Create a test class in the appropriate package:

```java
package org.traccar.protocol;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NewProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecode() throws Exception {
        var decoder = inject(new NewProtocolDecoder(null));

        verifyPosition(decoder, binary(
                "your binary test data here"));
        
        verifyAttributes(decoder, binary(
                "your binary test data here"));
        
        verifyPosition(decoder, text(
                "your text test data here"));
    }
}
```

2. Use the helper methods provided by `ProtocolTest` to verify the decoding results.

## Code Style and Guidelines

Traccar follows these coding guidelines:

1. Use 4 spaces for indentation (no tabs)
2. Follow Java naming conventions:
   - CamelCase for class names
   - camelCase for method and variable names
   - ALL_CAPS for constants
3. Keep methods short and focused on a single responsibility
4. Add JavaDoc comments for public classes and methods
5. Write unit tests for new functionality
6. Follow the existing architectural patterns

## Building for Production

To build the application for production:

```
./gradlew build
```

The built application will be in the `target` directory.

## Debugging

### Remote Debugging

To enable remote debugging, run the application with these JVM options:

```
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar target/traccar-6.8.1.jar debug.xml
```

Then connect your IDE to port 5005.

### Logging

To increase logging verbosity, add the following to the configuration file:

```xml
<logger>
    <level>DEBUG</level>
</logger>
```

Log files are stored in the `logs` directory.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for your changes
5. Ensure all tests pass
6. Submit a pull request

## Extending Traccar

### Custom Handlers

You can create custom handlers to process position data:

```java
package org.traccar.handler;

import org.traccar.BaseDataHandler;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

public class CustomHandler extends BaseDataHandler {

    private final CacheManager cacheManager;

    public CustomHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    protected Position handlePosition(Position position) {
        // Custom processing logic
        return position;
    }
}
```

Register your handler in a custom module:

```java
public class CustomModule extends AbstractModule {
    @Override
    protected void configure() {
        // Bindings
    }
    
    @Singleton
    @Provides
    public CustomHandler provideCustomHandler(CacheManager cacheManager) {
        return new CustomHandler(cacheManager);
    }
}
```

### Custom Reports

To add a custom report:

1. Create a report class in the `org.traccar.reports` package
2. Add a resource endpoint in the `org.traccar.api.resource` package
3. Implement the report generation logic

### Custom Notifications

To add a custom notification type:

1. Add a new notification type in the `org.traccar.notificators` package
2. Register it in the notification manager
3. Add templates for the new notification type

## Troubleshooting Development Issues

1. **Build failures**
   - Check Java version compatibility
   - Ensure all dependencies are available
   - Look for compilation errors in the build output

2. **Runtime errors**
   - Check the logs in the `logs` directory
   - Enable DEBUG logging for more detailed information
   - Use a debugger to step through the code

3. **Protocol issues**
   - Use protocol analyzers to inspect the raw data
   - Add debug logging to the protocol decoder
   - Create test cases with sample data

## Additional Resources

- [Traccar GitHub Repository](https://github.com/traccar/traccar)
- [Traccar Forums](https://www.traccar.org/forums/)
- [Traccar API Documentation](https://www.traccar.org/traccar-api/)
- [Java Documentation](https://docs.oracle.com/en/java/)
- [Netty Documentation](https://netty.io/wiki/user-guide.html)