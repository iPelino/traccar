# Authentication API Testing

This document provides instructions on how to run the Traccar application and test the authentication API endpoints.

## Running the Application

1. Build the project:
   ```bash
   ./gradlew build -x checkstyleMain
   ```

2. Run the application:
   ```bash
   java -jar target/tracker-server.jar debug.xml
   ```

3. The application will start and listen on port 8082. You can access the web interface at http://localhost:8082.

## Authentication API Endpoints

The Traccar application provides the following authentication API endpoints:

1. **Login** - `POST /api/session`
   - Creates a new session (login)
   - Request: Form data with `email` and `password` parameters
   - Response: User session object with `user`, `role`, and `companyId` fields

2. **Session Info** - `GET /api/session`
   - Retrieves the current session information
   - Request: Requires session cookie
   - Response: User session object with `user`, `role`, and `companyId` fields

3. **Logout** - `DELETE /api/session`
   - Removes the session (logout)
   - Request: Requires session cookie
   - Response: 204 No Content

## Testing the Authentication API

You can test the authentication API endpoints using the provided shell script:

```bash
chmod +x test_auth_api.sh
./test_auth_api.sh
```

The script tests all three authentication endpoints and verifies that the responses include the expected information (role and companyId).

### Manual Testing

You can also test the endpoints manually using curl:

#### Login

```bash
curl -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "email=admin&password=admin" \
  -c cookies.txt \
  http://localhost:8082/api/session
```

#### Session Info

```bash
curl -X GET \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  http://localhost:8082/api/session
```

#### Logout

```bash
curl -X DELETE \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  http://localhost:8082/api/session
```

## Expected Results

When testing the authentication API endpoints, you should verify that:

1. The login endpoint returns a user session object with `role` and `companyId` fields
2. The session info endpoint returns a user session object with `role` and `companyId` fields
3. The logout endpoint returns a 204 No Content response

This confirms that the authentication API has been enhanced to return role and companyId as required by task auth-001.