# Smart Solar Microgrid Trading System

Welcome to the **Solarix Smart Solar Microgrid Trading System**! This repository contains the comprehensive solution for managing prosumer accounts, solar energy trading, station reservations, and energy monitoring. The project is designed with a modern technology stack covering a web backoffice, a mobile application for prosumers, and a scalable RESTful API backend.

## 🔗 Project Links
- **Git Repository:** [https://github.com/sandaru3n/smart-solar-microgrid.git](https://github.com/sandaru3n/smart-solar-microgrid.git)
- **Demonstration Video:** [Watch the Demo](https://mysliit-my.sharepoint.com/:f:/g/personal/it23163904_my_sliit_lk/IgB1Qe2FeZIORa0YSfrm1gV2AXnpE_2xql3bo51Fzc0RXws?e=AUtnRV)

---

## 🏗️ Architecture Overview

The system is built on a distributed micro-services-inspired architecture:

1. **Backend API (C# / .NET Core)**
   - Acts as the core logic hub handling business rules, validation, and data processing.
   - Built securely with JWT token-based authentication and role-based access control.
   - Exposes RESTful endpoints for both the Web Backoffice and the Mobile Application.

2. **Database (MongoDB)**
   - A NoSQL database used to store unstructured/semi-structured models like user profiles, pending registrations, stations, and time-slot reservations.

3. **Web Backoffice (React & TailwindCSS)**
   - A modern, responsive dashboard used by administrators and station operators.
   - Manages station approvals, staff creation, scheduling configurations, and analytics.

4. **Mobile Application (Android / Kotlin)**
   - The primary interface for "Prosumers" to register, verify their identities via OTP, explore solar stations via Google Maps, and book energy transfer slots using a secure QR code system.

---

## 👥 Team Members & Contributions

| Member | Name | Student ID | Primary Responsibility |
| :--- | :--- | :--- | :--- |
| **Member 1** | W V A D K Chamara | IT23163904 | Accounts & User Management |
| **Member 2** | M. S. N. Peiris | IT23201132 | Stations & Availability |
| **Member 3** | M. T. C. Peiris | IT23201200 | Reservation Management |
| **Member 4** | Dilshan. N | IT23250574 | Monitoring & QR |

### Detailed Individual Contributions

#### **Member 1: Accounts & User management**
- **Web:** Staff sign-in, account administration, prosumer status management, and reactivation screens.
- **Android:** Prosumer registration, sign-in, profile editing, deactivation, and account-related local persistence.
- **API & Database:** User model, authentication, password handling, roles, profile APIs, and account status rules.

#### **Member 2: Stations & Availability**
- **Web:** Create, edit, and deactivate stations; manage schedules, slots, and availability.
- **Android:** Google Maps nearby stations, markers, station details, schedules, and slot-selection handoff.
- **API & Database:** Station and slot operations, location queries, schedules, capacity validation, and deactivation checks.

#### **Member 3: Reservation Management**
- **Web:** Create bookings for prosumers, view reservation details, update and cancel reservations, and display action summaries.
- **Android:** Create reservations from selected slots, view details, update and cancel bookings, and display validation messages.
- **API & Database:** Reservation APIs, seven-day scheduling rule, 12-hour update/cancellation rule, conflict prevention, atomic capacity changes, and reservation persistence.

#### **Member 4: Monitoring & QR**
- **Web:** Booking lists, pending views, history, search, dashboard counts, and approval workflow.
- **Android:** Booking dashboard and history, approved QR display, operator scanning, and completion screens.
- **API & Database:** Authorised booking queries, totals, approval transitions, secure QR validation, and transfer completion.
