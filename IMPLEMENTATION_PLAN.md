# Ammu Project: Phased Implementation Plan

This document outlines a phased plan to implement the AI-powered Facebook Page management application within the `ammu` project.

---

### **Phase 1: Foundation & UI Shell [COMPLETED]**

*   **Goal:** Create the basic structure and non-functional user interface of the app.
*   **Tasks:**
    1.  **Project Cleanup:** Rename the package and relevant files from `com.example.compile_example` to something more meaningful, like `com.ammu.social`.
    2.  **Navigation:** Implement a simple bottom navigation bar with two main screens: "Comment Inbox" and "Strategy Report".
    3.  **Static UI (Compose):** Build the UI for both screens using static, hardcoded data.
        *   The "Comment Inbox" screen would show a list of fake comments, each with a placeholder for a "High" or "Low" priority tag.
        *   The "Strategy Report" screen would have placeholder text for "Top Insights," "Actionable Strategy," etc.
*   **Outcome:** A clickable prototype of the app that shows the layout and navigation, but with no real logic.

### **Phase 2: Implement the AI Comment Filter (Client-Side)**

*   **Goal:** Bring the "Comment Inbox" to life by integrating the Gemini "Flash" model.
*   **Tasks:**
    1.  **API Integration:** Add the necessary libraries to the project to make API calls to Gemini.
    2.  **ViewModel Logic:** Create a `ViewModel` that takes a list of hardcoded comments.
    3.  **AI Classification:** For each comment, call the Gemini API using the "Real-Time Comment Filter" prompt.
    4.  **Update UI:** Parse the JSON response from the AI and update the UI to dynamically show the correct priority ("HIGH_PRIORITY" or "LOW_PRIORITY") and the reason for each comment.

*   **AI Mechanism Implementation Approaches (Discussion):**
    During the implementation of the AI Comment Filter, two primary approaches for integrating the AI classification logic were considered:

    **Approach 1: Via a Node.js Backend (Recommended for Production)**
    *   **Mechanism:** The Android application sends raw comments to a dedicated Node.js backend server. The Node.js server then makes the call to the Gemini API, processes the response, and returns the classified comments to the Android app.
    *   **Pros:**
        *   **API Key Security:** Gemini API key is securely stored on the server, not embedded in the client application.
        *   **Centralized Logic:** AI prompt engineering, model selection, and pre/post-processing logic can be updated server-side without requiring an app update.
        *   **Rate Limiting/Cost Management:** Easier to manage API usage, rate limits, and costs from a single server endpoint.
        *   **Scalability:** Backend can be scaled independently to handle increased load.
    *   **Cons:**
        *   Requires deployment and maintenance of a separate backend server.
        *   Introduces an additional network hop, potentially increasing latency.
    *   **Current Status:** The `ai_strategy_app` web application demonstrates a working Node.js backend for AI classification. The `ammu` Android app was initially configured to use this backend, but its `InboxViewModel.kt` was found to be incorrectly pointing directly to the Gemini API.

    **Approach 2: Direct Gemini API Call from Android App (Simpler for Development/Testing)**
    *   **Mechanism:** The Android application directly constructs the Gemini API request and sends it to the Gemini API endpoint. The app then parses the Gemini API's response directly.
    *   **Pros:**
        *   Simpler architecture, no separate backend server to manage.
        *   Potentially lower latency if direct connection is faster.
    *   **Cons:**
        *   **API Key Security Risk:** The Gemini API key must be embedded in the Android application, making it vulnerable to extraction via decompilation.
        *   **Maintenance Overhead:** Any changes to the AI prompt or processing logic require an app update and redeployment.
        *   **Rate Limiting/Cost Management:** More challenging to manage API usage and costs across many client devices.
    *   **Current Status:** This approach would involve porting the prompt construction and response parsing logic from the Node.js `server.js` directly into `InboxViewModel.kt`.

*   **Decision:** For initial development and testing, direct Gemini API calls might be simpler. However, for a production-ready application, the Node.js backend approach is strongly recommended due to security, maintainability, and scalability benefits. The current task involves rectifying the `InboxViewModel.kt` to either correctly point to the Node.js backend or to implement the direct Gemini API call as per user's preference.

*   **Outcome:** A functional "Comment Inbox" that can classify a predefined list of comments, proving the core AI filtering logic works within the app, using either a backend or direct API calls.

### **Phase 3: Facebook API Integration (Comments)**

*   **Goal:** Replace the hardcoded comments with real, live comments from a Facebook Page.
*   **Tasks:**
    1.  **Facebook SDK/API:** Integrate the Facebook Graph API to handle authentication and data fetching. This would require setting up a Facebook Developer account.
    2.  **Fetch Comments:** Implement the logic to pull the latest comments from a specific Facebook Page.
    3.  **Connect the Pipes:** Feed the live comments into the classification engine built in Phase 2.
*   **Outcome:** A fully functional, real-time comment inbox that automatically prioritizes new comments as they come in.

### **Phase 4: Implement the AI Strategy Report**

*   **Goal:** Build the logic to generate and display the strategic brief using the Gemini "Pro" model.
*   **Tasks:**
    1.  **Sample Data:** Create a sample JSON or text file in the app's assets that mimics the structure of Facebook analytics data.
    2.  **ViewModel Logic:** Create a `ViewModel` for the "Strategy Report" screen.
    3.  **AI Analysis:** On a button press (e.g., "Generate Report"), send the sample data to the Gemini API using the "Daily Content Strategy Analysis" prompt.
    4.  **Display Report:** Parse the AI's response and display the formatted report on the screen.
*   **Outcome:** A functional "Strategy Report" screen that can generate a detailed analysis from sample data.

### **Phase 5: Facebook API Integration (Analytics)**

*   **Goal:** Power the strategy report with real Facebook analytics.
*   **Tasks:**
    1.  **Fetch Analytics:** Expand the Facebook Graph API integration to pull page analytics (reach, engagement, demographics, etc.).
    2.  **Data Formatting:** Write the code to process and format this live data into the structure your prompt expects.
    3.  **Connect the Pipes:** Feed the live analytics into the strategy generation engine from Phase 4.
*   **Outcome:** The app can now generate a real, data-driven strategy report on demand.