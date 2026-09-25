# Member 1 - API Design

## Authentication

### Login
Purpose:
Authenticate a user and return the information required by the client
to establish an authenticated session.

Allowed roles:
- Backoffice
- Grid Operator
- Prosumer


## Staff Management

### Create Staff
Purpose:
Create a Backoffice or Grid Operator account.

Allowed role:
- Backoffice


### View Staff
Purpose:
Retrieve staff account information.

Allowed role:
- Backoffice


### Update Staff
Purpose:
Update permitted staff account information.

Allowed role:
- Backoffice


### Deactivate Staff
Purpose:
Deactivate a staff account according to the agreed workflow.

Allowed role:
- Backoffice


## Prosumer Management

### Register Prosumer
Purpose:
Create a new Prosumer account using NIC.

Allowed:
- Public registration through the Android application


### Get Prosumer Profile
Purpose:
Retrieve a Prosumer profile.

Allowed:
- Backoffice
- The authenticated Prosumer for their own profile


### Update Prosumer Profile
Purpose:
Update permitted Prosumer information.

Allowed:
- Backoffice
- The authenticated Prosumer for their own profile


### Deactivate Prosumer
Purpose:
Deactivate a Prosumer account.

Allowed:
- Backoffice
- Authenticated Prosumer can request deactivation


## Activation

### Get Pending Accounts
Purpose:
Retrieve Prosumer accounts waiting for activation.

Allowed role:
- Backoffice


### Activate Account
Purpose:
Activate a pending Prosumer account.

Allowed role:
- Backoffice


### Reactivate Account
Purpose:
Reactivate a deactivated account.

Allowed role:
- Backoffice


## Security Requirements

- The API must authenticate users before protected operations.
- The API must check the user's role before protected operations.
- Prosummers must not access another Prosumer's private account.
- Duplicate NIC registration must be rejected.
- Passwords must not be stored as plaintext.
- Password hashes must not be returned to clients.
- Invalid or unauthorized requests must be rejected.