# Authentication, User Roles, and Company API Test Results

## Overview

This document summarizes the findings from analyzing the authentication, user roles, and company endpoints of the Traccar API.

## API Architecture

The Traccar API follows a RESTful architecture with the following key components:

1. **Authentication** - Endpoints for login, session management, and logout
2. **User Management** - Endpoints for managing users with different roles
3. **Company Management** - Endpoints for managing companies

## Authentication Endpoints

### 1. Login (Create Session)
- **Endpoint**: `POST /api/session`
- **Request**: Form data with `email` and `password` parameters
- **Response**: User object with session cookie
- **Example**:
  ```bash
  curl -X POST \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "email=admin&password=admin" \
    -c cookies.txt \
    http://localhost:8082/api/session
  ```

### 2. Get Session Information
- **Endpoint**: `GET /api/session`
- **Request**: Requires session cookie
- **Response**: User object
- **Example**:
  ```bash
  curl -X GET \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/session
  ```

### 3. Logout (Delete Session)
- **Endpoint**: `DELETE /api/session`
- **Request**: Requires session cookie
- **Response**: 204 No Content
- **Example**:
  ```bash
  curl -X DELETE \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/session
  ```

## User Roles

The Traccar API supports the following user roles:

1. **SUPER_USER** - Global access to all features and data
2. **ADMIN** - Manage company-specific users, vehicles, reports
3. **COMPANY_USER** - Regular company user with limited access
4. **FINANCE_USER** - Access to payment and expense data only

### User Endpoints

#### 1. Get All Users
- **Endpoint**: `GET /api/users`
- **Request**: Requires session cookie (admin or higher)
- **Response**: Array of User objects
- **Example**:
  ```bash
  curl -X GET \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/users
  ```

#### 2. Create User
- **Endpoint**: `POST /api/users`
- **Request**: Requires session cookie (admin or higher) and User object
- **Response**: Created User object
- **Example**:
  ```bash
  curl -X POST \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    -d '{
      "name": "Test User",
      "email": "test@example.com",
      "password": "password",
      "role": "COMPANY_USER"
    }' \
    http://localhost:8082/api/users
  ```

#### 3. Get User
- **Endpoint**: `GET /api/users/{id}`
- **Request**: Requires session cookie
- **Response**: User object
- **Example**:
  ```bash
  curl -X GET \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/users/1
  ```

#### 4. Update User
- **Endpoint**: `PUT /api/users/{id}`
- **Request**: Requires session cookie (admin or higher, or self) and User object
- **Response**: Updated User object
- **Example**:
  ```bash
  curl -X PUT \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    -d '{
      "id": 1,
      "name": "Updated User",
      "email": "updated@example.com",
      "role": "ADMIN"
    }' \
    http://localhost:8082/api/users/1
  ```

#### 5. Delete User
- **Endpoint**: `DELETE /api/users/{id}`
- **Request**: Requires session cookie (admin or higher)
- **Response**: 204 No Content
- **Example**:
  ```bash
  curl -X DELETE \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/users/1
  ```

## Company Endpoints

### 1. Get All Companies
- **Endpoint**: `GET /api/companies`
- **Request**: Requires session cookie
- **Response**: Array of Company objects
- **Example**:
  ```bash
  curl -X GET \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/companies
  ```

### 2. Create Company
- **Endpoint**: `POST /api/companies`
- **Request**: Requires session cookie (admin or higher) and Company object
- **Response**: Created Company object
- **Example**:
  ```bash
  curl -X POST \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    -d '{
      "companyName": "Test Company",
      "registrationNumber": "REG123456",
      "industryId": 1,
      "companySize": "Medium",
      "businessAddress": "123 Test Street",
      "phoneNumber": "+1234567890",
      "companyEmail": "test@testcompany.com",
      "website": "https://testcompany.com",
      "timeZone": "UTC"
    }' \
    http://localhost:8082/api/companies
  ```

### 3. Get Company
- **Endpoint**: `GET /api/companies/{id}`
- **Request**: Requires session cookie
- **Response**: Company object
- **Example**:
  ```bash
  curl -X GET \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/companies/1
  ```

### 4. Update Company
- **Endpoint**: `PUT /api/companies/{id}`
- **Request**: Requires session cookie (admin or higher) and Company object
- **Response**: Updated Company object
- **Example**:
  ```bash
  curl -X PUT \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    -d '{
      "id": 1,
      "companyName": "Updated Company",
      "registrationNumber": "REG123456",
      "industryId": 1,
      "companySize": "Large",
      "businessAddress": "456 Updated Street",
      "phoneNumber": "+9876543210",
      "companyEmail": "updated@testcompany.com",
      "website": "https://updated-testcompany.com",
      "timeZone": "UTC"
    }' \
    http://localhost:8082/api/companies/1
  ```

### 5. Delete Company
- **Endpoint**: `DELETE /api/companies/{id}`
- **Request**: Requires session cookie (admin or higher)
- **Response**: 204 No Content
- **Example**:
  ```bash
  curl -X DELETE \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    http://localhost:8082/api/companies/1
  ```

## Permissions and Access Control

The Traccar API implements role-based access control:

1. **SUPER_USER** can access all endpoints and perform all operations
2. **ADMIN** can manage users, companies, and other resources within their company
3. **COMPANY_USER** has limited access to resources within their company
4. **FINANCE_USER** has access to financial data only

## Conclusion

The Traccar API provides a comprehensive set of endpoints for authentication, user management with different roles, and company management. The API follows RESTful principles and implements role-based access control to ensure that users can only access resources they are authorized to access.

To fully test these endpoints, the Traccar server needs to be running on localhost:8082. The tests in SessionApiTest.java and CompanyApiTest.java provide examples of how to interact with these endpoints programmatically.
