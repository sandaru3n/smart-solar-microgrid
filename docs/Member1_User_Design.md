# Member 1 - User Account System

## Roles

1. Backoffice
2. Grid Operator
3. Prosumer

## Account Statuses

1. Pending
2. Active
3. Deactivated

## Main Responsibilities

### Backoffice
- Create staff accounts
- View staff accounts
- Manage prosumers
- Activate pending prosumers
- Deactivate prosumers
- Reactivate deactivated prosumers

### Grid Operator
- Login
- Access operator functionality
- Cannot access Backoffice-only administration

### Prosumer
- Register
- Login
- View own profile
- Edit own profile
- Request account deactivation

## Authentication

Users must provide valid credentials.
The API verifies the credentials and account status.

## Authorization

The API determines whether the authenticated role
is allowed to perform the requested operation.

## Security

- Passwords must not be stored as plaintext.
- Password hashes must not be returned to clients.
- Prosummers must not access another prosumer's private account.
- Unauthorized roles must be rejected by the API.

## Client Architecture

Web -> C# API -> MongoDB

Android -> C# API -> MongoDB

Android may use SQLite for required local persistence.

## Account Lifecycle

Prosumer registration
        |
        v
     Pending
        |
        v
Backoffice activation
        |
        v
      Active
        |
        v
Deactivation
        |
        v
   Deactivated
        |
        v
Backoffice reactivation
        |
        v
      Active