# OPT_Pal Manual Setup Guide

This guide covers the manual steps you need to complete to enable all features of the Secure Vault and AI system.

## 🔥 Firebase Setup

### Step 1: Firebase Console Configuration

1. **Open Firebase Console**: Go to [https://console.firebase.google.com](https://console.firebase.google.com)
2. **Select Your Project**: Click on your `OPT_Pal` project (or create one if it doesn't exist)

### Step 2: Enable Firebase Services

#### Authentication
1. Navigate to **Build** → **Authentication**
2. Click **Get Started** (if not already enabled)
3. Enable **Email/Password** sign-in method
4. (Optional but Recommended) Enable **Google Sign-In**

#### Firestore Database
1. Navigate to **Build** → **Firestore Database**
2. Click **Create Database**
3. Choose **Production Mode** (we have custom rules)
4. Select a location close to your users (e.g., `us-central1`)

#### Cloud Storage
1. Navigate to **Build** → **Storage**
2. Click **Get Started**
3. Choose **Production Mode**
4. Use the same location as Firestore

#### Cloud Functions
1. Navigate to **Build** → **Functions**
2. Click **Get Started** (this just enables the service)
3. Note: We'll deploy functions later via CLI

---

## 🔑 Google Cloud Platform Setup

### Step 3: Enable ML Kit APIs

1. **Open Google Cloud Console**: Go to [https://console.cloud.google.com](https://console.cloud.google.com)
2. **Select Your Project**: Make sure the same project linked to Firebase is selected
3. **Enable APIs**:
   - Search for "**ML Kit API**" and enable it
   - Search for "**Cloud Vision API**" and enable it (used by ML Kit)

### Step 4: Enable Vertex AI (for Gemini)

1. In Google Cloud Console, search for "**Vertex AI API**"
2. Click **Enable**
3. Navigate to **Vertex AI** → **Generative AI Studio**
4. Accept any terms of service if prompted

### Step 5: Get Gemini API Key

> [!IMPORTANT]
> **For Production**: Use Firebase Functions to call Gemini via Vertex AI SDK to keep API keys secure.
> **For Development/Testing**: You can use a direct API key from Google AI Studio.

#### Option A: Direct API Key (Quick Start - Development Only)
1. Go to [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)
2. Click **Create API Key**
3. Select your Firebase project
4. Copy the API key
5. **Store it securely** (we'll add it to `local.properties` later)

#### Option B: Vertex AI SDK (Recommended for Production)
- No API key needed! Uses Firebase Authentication
- We'll implement this via Firebase Functions

---

## 📱 Android Studio Setup

### Step 6: Update local.properties

Add your Gemini API key to `local.properties` (for development):

```properties
sdk.dir=C\:\\Users\\surya\\AppData\\Local\\Android\\sdk
GEMINI_API_KEY=your_api_key_here
```

> [!WARNING]
> Never commit `local.properties` to version control!

---

## 🔐 Security Configuration

### Step 7: Firebase Security Rules

The following rules are already in your project, but verify they are deployed:

#### Firestore Rules (`firestore.rules`)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      
      match /documents/{document=**} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
      
      match /employment/{employment=**} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
}
```

#### Storage Rules (`storage.rules`)
```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

**Deploy these rules:**
```bash
firebase deploy --only firestore:rules
firebase deploy --only storage:rules
```

---

## ✅ Verification Checklist

Before proceeding, verify:

- [ ] Firebase Authentication is enabled (Email/Password)
- [ ] Firestore Database is created
- [ ] Cloud Storage is enabled
- [ ] ML Kit API is enabled in Google Cloud
- [ ] Vertex AI API is enabled
- [ ] Gemini API key is obtained (if using direct API)
- [ ] Security rules are deployed
- [ ] `local.properties` contains `GEMINI_API_KEY` (for dev)

---

## 🚀 Next Steps

Once all manual steps are complete, the automated implementation can proceed with:
1. Building the `SecurityManager` for encryption
2. Setting up SQLCipher database
3. Implementing ML Kit document scanning
4. Integrating Gemini AI for document processing

---

## 💰 Cost Monitoring

### Set up Budget Alerts

1. In Firebase Console, go to **Usage and Billing**
2. Click **Details & Settings**
3. Set a **Budget Alert** at **$5.00/month** to avoid surprises

### Free Tier Limits

- **Firebase Auth**: 50,000 MAU (monthly active users) - FREE
- **Firestore**: 1 GiB storage, 50k reads/day - FREE
- **Storage**: 5 GB - FREE
- **Gemini 1.5 Flash**: 15 requests/minute - FREE (Google AI Studio tier)
- **ML Kit**: Unlimited on-device - FREE

You should stay in the free tier for hundreds of users!
