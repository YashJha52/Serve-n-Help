# 🍽️ Serve-N-Help
 
**Serve-N-Help** is an Android application designed to reduce food wastage by connecting food donors with NGOs and volunteers. The platform enables restaurants, hotels, caterers, supermarkets, event organizers, and individuals to donate surplus food, while NGOs and volunteers can discover nearby donations, request pickups, and ensure that the food reaches people in need before it expires.
 
The application aims to create a transparent, efficient, and community-driven food donation ecosystem that helps fight hunger while minimizing food waste.
 
---
 
## 🌍 Problem Statement
 
Every day, large quantities of edible food are discarded by restaurants, hotels, supermarkets, and households, while millions of people continue to face hunger. The lack of an organized platform for connecting food donors with organizations that distribute food leads to unnecessary food wastage.
 
Serve-N-Help bridges this gap by providing a centralized platform where donors can easily list surplus food and NGOs can quickly locate, request, and collect available donations.
 
---
 
## 🎯 Objectives
 
- Reduce food wastage by promoting food donation.
- Connect donors with NGOs and volunteers through a single platform.
- Simplify the donation and pickup process.
- Maintain transparency throughout the donation lifecycle.
- Encourage community participation in social welfare.
 
---
 
## ✨ Features
 
### 👤 User Authentication
- Secure user registration and login using Firebase Authentication.
- Separate roles for:
  - Food Donors
  - NGOs
  - Volunteers
 
### 🍛 Food Donation
- Post available food donations.
- Add food details:
  - Food Name
  - Quantity
  - Food Type
  - Pickup Address
  - Expiry Time
  - Food Images
 
### 📍 Location-Based Pickup
- Display pickup locations.
- Help NGOs and volunteers locate nearby food donations.
 
### 📦 Donation Management
- Browse available donations.
- Accept or reject donation requests.
- Track donation status:
  - Available
  - Accepted
  - Picked Up
  - Delivered
 
### 🔔 Notifications
- Notify NGOs when new food donations are posted.
- Notify donors when their donation is accepted.
- Real-time status updates.
 
### 📜 Donation History
- View previous donations.
- Track contribution history.
- Display impact statistics.
 
---
 
## 🛠 Tech Stack
 
### Frontend
- Android Studio
- Java
- XML
 
### Backend
- Firebase Authentication
- Firebase Cloud Firestore
- Firebase Storage
 
### APIs
- Google Maps API
- Firebase Cloud Messaging (FCM)
 
---
 
## 🗂 Project Modules
 
- User Authentication
- Donor Dashboard
- NGO Dashboard
- Volunteer Dashboard
- Food Donation Management
- Pickup Request Management
- Notifications
- Donation History
- User Profile
 
---
 
## 📂 Database Structure
 
### Users
- User ID
- Name
- Email
- Phone Number
- User Role
 
### Food Donations
- Donation ID
- Food Name
- Quantity
- Food Type
- Pickup Address
- Expiry Time
- Image URL
- Status
 
### Pickup Requests
- Request ID
- Donation ID
- NGO ID
- Volunteer ID
- Request Status
 
---
 
## ⚙️ Setup

1. Create a Firebase project and add an Android app with package name `com.example.serve_n_help`.
2. Download `google-services.json` and place it in `app/`.
3. In the Firebase console enable **Authentication → Email/Password** and create a **Cloud Firestore** database. Paste the contents of `firestore.rules` into the Firestore Rules tab.
4. In Google Cloud console enable **Maps SDK for Android** for the same project and create an API key.
5. Add the key to `local.properties` (not committed):
   ```
   MAPS_API_KEY=your_key_here
   ```
6. Sync Gradle and run the app.

---

## 🚀 Future Enhancements
 
- AI-based food quality and freshness detection.
- Smart donation recommendations based on NGO demand.
- Route optimization for volunteers.
- QR code verification during pickup.
- Analytics dashboard for donation trends.
- Multilingual support.
- In-app chat between donors and NGOs.
 
---
 
## 🎯 Target Users
 
- Restaurants
- Hotels
- Caterers
- Event Organizers
- Supermarkets
- NGOs
- Volunteers
- Individual Donors
 
---
 
## 🌱 Social Impact
 
Serve-N-Help contributes towards reducing food waste while helping provide meals to underprivileged communities. By creating an efficient donation network, the application promotes sustainability, community engagement, and responsible food distribution.
 
---
 
## 👨‍💻 Contributors
 
Developed as a Mobile Application Development project by our team.
 
---
 
## 📄 License
 
This project is developed for educational purposes.
 
