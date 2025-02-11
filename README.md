# Real-time Navigation & Messaging App

## Overview
This Android application demonstrates real-time location sharing and navigation by integrating Google Maps with RabbitMQ messaging. It periodically sends the device’s current location to a messaging queue and listens for updates from other devices. Additionally, the app lets users tap on the map to generate driving directions via the Google Maps Directions API and draws the resulting route on the map.

## Features
- **Live Location Tracking:** Retrieves and displays your current location on a Google Map.
- **Real-time Messaging:** Uses RabbitMQ to send and receive location updates, enabling dynamic marker updates.
- **Custom Markers:** Generates personalized map markers with custom text and background colors.
- **Route Mapping:** Fetches and decodes driving directions between two points, drawing a polyline on the map.
- **Asynchronous Processing:** Leverages Kotlin Coroutines and AsyncTask for efficient background operations.

## Technologies & Libraries
- **Google Maps API:** For interactive maps, markers, and route visualization.
- **Google Location Services (FusedLocationProviderClient):** To obtain the device's current location.
- **RabbitMQ:** Implements real-time messaging between devices.
- **OkHttp:** Handles HTTP requests to fetch directions from the Google Maps Directions API.
- **Gson:** Parses JSON responses for route data.
- **Kotlin & Android:** Primary development language and platform.
- **Kotlin Coroutines:** Manages asynchronous tasks effectively.
- **AndroidX Libraries:** Provides modern support and compatibility features.
