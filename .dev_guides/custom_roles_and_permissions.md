# Custom Roles and Permissions in Traccar

This document describes the implementation of custom roles and permissions in Traccar, including company-level data segregation.

## Overview

The implementation adds the following features to Traccar:

1. **Company Entity**: A new entity to represent companies with various attributes
2. **User Roles**: Four predefined roles with different permission levels
3. **Company-Level Data Segregation**: Users can only access data within their company
4. **Role-Based Access Control (RBAC)**: Permissions are based on user roles

## User Roles

The following roles are implemented:

1. **SUPER_USER**: Global access to all features and data
2. **ADMIN**: Manage company-specific users, vehicles, and reports
3. **COMPANY_USER**: Regular company user with limited access
4. **FINANCE_USER**: Access to payment and expense data only

## Database Changes

The implementation adds the following database changes:

1. New `tc_companies` table with company information
2. New `role` and `companyId` columns in the `tc_users` table
3. New `tc_user_company` table for many-to-many relationships between users and companies

## API Changes

The implementation adds the following API changes:

1. New `/companies` endpoint for managing companies
2. Modified `/users` endpoint to support role-based access control and company data segregation

## Permission System

The permission system has been extended to support role-based access control:

1. **SUPER_USER**: Has access to everything
2. **ADMIN**: Has access to everything within their company
3. **FINANCE_USER**: Has access to financial data within their company
4. **COMPANY_USER**: Has limited access to data within their company

## Company Data Model

The Company entity includes the following fields:

- `logo`: URL to the company logo (optional, JPG or PNG, max 2MB)
- `companyName`: Company name (required)
- `registrationNumber`: Registration number (optional, alphanumeric)
- `industryId`: Industry ID (required, foreign key)
- `companySize`: Company size (optional, from predefined list)
- `businessAddress`: Business address (required, multi-line)
- `phoneNumber`: Phone number (required, validated format)
- `companyEmail`: Company email (required, unique, validated format)
- `website`: Website URL (optional, validated format)
- `timeZone`: Time zone (required, from predefined list)

## Usage

### Creating a Company

Only SUPER_USER can create companies:

```
POST /api/companies
{
  "companyName": "Example Company",
  "industryId": 1,
  "businessAddress": "123 Main St, City, Country",
  "phoneNumber": "+1234567890",
  "companyEmail": "info@example.com",
  "timeZone": "Africa/Kigali"
}
```

### Creating Users with Roles

SUPER_USER can create any user with any role:

```
POST /api/users
{
  "name": "Admin User",
  "email": "admin@example.com",
  "password": "password",
  "role": "ADMIN",
  "companyId": 1
}
```

ADMIN can create COMPANY_USER and FINANCE_USER within their company:

```
POST /api/users
{
  "name": "Company User",
  "email": "user@example.com",
  "password": "password",
  "role": "COMPANY_USER"
}
```

## Implementation Details

The implementation includes the following components:

1. **Company Entity**: Defined in `org.traccar.model.Company`
2. **User Role Enum**: Defined in `org.traccar.model.UserRole`
3. **Company Resource**: Defined in `org.traccar.api.resource.CompanyResource`
4. **Modified User Resource**: Updated in `org.traccar.api.resource.UserResource`
5. **Modified Permissions Service**: Updated in `org.traccar.api.security.PermissionsService`
6. **Database Migration**: Defined in `schema/changelog-6.9.0.xml`

## Limitations

1. The UI has not been modified to support the new roles and permissions
2. The implementation focuses on the backend API only
3. Some advanced features like role-specific dashboards are not implemented