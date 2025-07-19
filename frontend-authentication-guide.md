# Traccar Authentication Guide for Frontend Developers

## Introduction

This guide explains how authentication works in the Traccar system, with a focus on user roles, permissions, and the authentication flow. It is intended for frontend developers who need to integrate with the Traccar authentication system.

## Table of Contents

1. [User Roles and Permissions](#user-roles-and-permissions)
2. [Authentication Flow](#authentication-flow)
3. [API Endpoints](#api-endpoints)
4. [Handling Different User Roles in the Frontend](#handling-different-user-roles-in-the-frontend)
5. [Example API Calls](#example-api-calls)

## User Roles and Permissions

Traccar implements a role-based access control system with company-scoped permissions. There are four user roles:

### SUPER_USER
- Has global access to all features and data
- Not associated with any company (companyId is null)
- Can create, view, update, and delete any user, company, or resource
- Only SUPER_USER can access the default Traccar UI
- The first user created in the system is automatically assigned the SUPER_USER role

### ADMIN
- Can manage company-specific users, vehicles, and reports
- Associated with a specific company (has a companyId)
- Can create users with COMPANY_USER or FINANCE_USER roles within their company
- Can view and manage all resources within their company
- Cannot access resources from other companies

### COMPANY_USER
- Regular company user with limited access
- Associated with a specific company (has a companyId)
- Can view and manage resources they have been granted access to
- Cannot create or delete users

### FINANCE_USER
- Access to payment and expense data only
- Associated with a specific company (has a companyId)
- Can view and manage financial data within their company
- Cannot create or delete users

## Authentication Flow

1. **Registration**: The first user registered in the system is automatically assigned the SUPER_USER role. Subsequent user registration may be disabled by default and requires a SUPER_USER or ADMIN to create new users.

2. **Login**: Users authenticate by sending their email and password to the login endpoint. The server validates the credentials and returns a session with user information, including the user's role and companyId.

3. **Session Management**: After successful authentication, the server creates a session for the user. The frontend can retrieve the current session information to check if the user is authenticated and get their role and companyId.

4. **Logout**: Users can log out by sending a request to the logout endpoint, which invalidates their session.

## API Endpoints

### Authentication Endpoints

#### Login
- **URL**: `POST /api/session`
- **Content-Type**: `application/x-www-form-urlencoded`
- **Request Body**:
  - `email`: User's email address
  - `password`: User's password
  - `code`: (Optional) Two-factor authentication code
- **Response**: User session object with `user`, `role`, and `companyId` fields
- **Example Response**:
  ```json
  {
    "user": {
      "id": 1,
      "name": "Admin User",
      "email": "admin@example.com",
      "phone": "+1234567890",
      "administrator": true
    },
    "role": "SUPER_USER",
    "companyId": null
  }
  ```

#### Get Current Session
- **URL**: `GET /api/session`
- **Response**: User session object with `user`, `role`, and `companyId` fields
- **Example Response**: Same as login response

#### Logout
- **URL**: `DELETE /api/session`
- **Response**: 204 No Content

### User Management Endpoints

#### Get All Users
- **URL**: `GET /api/users`
- **Query Parameters**:
  - `userId`: (Optional) Filter by user ID
  - `deviceId`: (Optional) Filter by device ID
  - `companyId`: (Optional) Filter by company ID
- **Response**: Array of user objects
- **Access Control**:
  - SUPER_USER: Can see all users or filter by company
  - ADMIN: Can only see users in their company
  - COMPANY_USER/FINANCE_USER: Can only see themselves and users they manage

#### Create User
- **URL**: `POST /api/users`
- **Content-Type**: `application/json`
- **Request Body**: User object
- **Response**: Created user object
- **Access Control**:
  - SUPER_USER: Can create any user with any role
  - ADMIN: Can only create users for their own company with roles COMPANY_USER or FINANCE_USER
  - COMPANY_USER/FINANCE_USER: Cannot create new users

#### Get User
- **URL**: `GET /api/users/{id}`
- **Response**: User object
- **Access Control**: Same as Get All Users

#### Update User
- **URL**: `PUT /api/users/{id}`
- **Content-Type**: `application/json`
- **Request Body**: User object
- **Response**: Updated user object
- **Access Control**:
  - SUPER_USER: Can update any user
  - ADMIN: Can only update users in their company with lower roles
  - COMPANY_USER/FINANCE_USER: Can only update themselves

#### Delete User
- **URL**: `DELETE /api/users/{id}`
- **Response**: 204 No Content
- **Access Control**:
  - SUPER_USER: Can delete any user except themselves
  - ADMIN: Can only delete users in their company with lower roles
  - COMPANY_USER/FINANCE_USER: Cannot delete users

## Handling Different User Roles in the Frontend

When building a frontend application that integrates with Traccar, you need to handle different user roles appropriately:

1. **Role-Based Routing**: Implement role-based routing to restrict access to certain pages based on the user's role.

2. **UI Customization**: Customize the UI based on the user's role. For example:
   - SUPER_USER: Show global administration features
   - ADMIN: Show company administration features
   - COMPANY_USER: Show regular user features
   - FINANCE_USER: Show finance-related features

3. **Permission Checking**: Check the user's role before performing actions or displaying UI elements:
   ```javascript
   // Example in React
   function canCreateUser(userRole) {
     return userRole === 'SUPER_USER' || userRole === 'ADMIN';
   }

   // In a component
   if (canCreateUser(userSession.role)) {
     // Show create user button
   }
   ```

4. **Company Scope**: Always consider the company scope when fetching or displaying data:
   ```javascript
   // Example in React
   function fetchUsers() {
     const { role, companyId } = userSession;

     if (role === 'SUPER_USER' && !companyId) {
       // Fetch all users
       return api.get('/api/users');
     } else {
       // Fetch users for the current company
       return api.get(`/api/users?companyId=${companyId}`);
     }
   }
   ```

5. **Redirect to Custom UI**: Redirect non-SUPER_USER roles to your custom UI instead of the default Traccar UI.

## Example API Calls

### Login

```javascript
// Using fetch API
async function login(email, password) {
  const formData = new URLSearchParams();
  formData.append('email', email);
  formData.append('password', password);

  const response = await fetch('/api/session', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: formData,
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Login failed');
  }

  return response.json();
}

// Usage
login('admin@example.com', 'password')
  .then(session => {
    console.log('Logged in as:', session.user.name);
    console.log('Role:', session.role);
    console.log('Company ID:', session.companyId);

    // Store session information
    localStorage.setItem('userSession', JSON.stringify(session));

    // Redirect based on role
    if (session.role === 'SUPER_USER') {
      window.location.href = '/admin';
    } else {
      window.location.href = '/dashboard';
    }
  })
  .catch(error => {
    console.error('Login error:', error);
  });
```

### Get Current Session

```javascript
// Using fetch API
async function getCurrentSession() {
  const response = await fetch('/api/session', {
    method: 'GET',
    credentials: 'include',
  });

  if (!response.ok) {
    if (response.status === 404) {
      // Not logged in
      return null;
    }
    throw new Error('Failed to get session');
  }

  return response.json();
}

// Usage
getCurrentSession()
  .then(session => {
    if (session) {
      console.log('Logged in as:', session.user.name);
    } else {
      console.log('Not logged in');
      window.location.href = '/login';
    }
  })
  .catch(error => {
    console.error('Session error:', error);
  });
```

### Create User (Admin or Super User only)

```javascript
// Using fetch API
async function createUser(userData) {
  const response = await fetch('/api/users', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(userData),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Failed to create user');
  }

  return response.json();
}

// Usage
const newUser = {
  name: 'John Doe',
  email: 'john@example.com',
  password: 'password123',
  role: 'COMPANY_USER',
  companyId: 1, // Only needed for SUPER_USER, ADMIN will automatically use their company
};

createUser(newUser)
  .then(user => {
    console.log('Created user:', user);
  })
  .catch(error => {
    console.error('Error creating user:', error);
  });
```

### Logout

```javascript
// Using fetch API
async function logout() {
  const response = await fetch('/api/session', {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Logout failed');
  }

  // Clear local storage
  localStorage.removeItem('userSession');

  return true;
}

// Usage
logout()
  .then(() => {
    console.log('Logged out successfully');
    window.location.href = '/login';
  })
  .catch(error => {
    console.error('Logout error:', error);
  });
```

## Conclusion

This guide provides an overview of how authentication works in the Traccar system, with a focus on user roles, permissions, and the authentication flow. By understanding these concepts, frontend developers can build applications that integrate seamlessly with Traccar's authentication system and provide appropriate access control based on user roles.

For more detailed information, refer to the Traccar API documentation and the source code.
