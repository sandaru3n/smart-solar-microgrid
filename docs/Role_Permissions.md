# Role Permissions

## BACKOFFICE

Can:
- Login
- Create staff
- View staff
- Update staff
- Manage prosumers
- View pending activations
- Activate prosumers
- Deactivate prosumers
- Reactivate prosumers

Cannot:
- Use functions restricted to other roles unless explicitly permitted

## GRID_OPERATOR

Can:
- Login
- Access operator functions

Cannot:
- Create Backoffice accounts
- Reactivate deactivated accounts
- Access Backoffice-only administration

## PROSUMER

Can:
- Register
- Login
- View own profile
- Edit own permitted profile information
- Request account deactivation
- Use prosumer functionality

Cannot:
- Manage staff
- Activate other users
- Reactivate users
- Access another prosumer's private information