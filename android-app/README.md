# ULTRON AI — Android 8.1+

This is an Android-native Java/XML companion app for the existing desktop-first ULTRON project. The original Python/PyQt desktop files remain unchanged.

## Build APK
GitHub Actions builds a debug APK on pushes and pull requests that change `android-app/**`, and can also be run manually from **Actions → Android APK → Run workflow**.

Download the `Ultron-Android-8.1-APK` artifact from a successful workflow run.

## Requirements
- Minimum Android version: Android 8.1 (API 27)
- Java source: Java 17
- UI: Android XML layouts and Java
- Online AI: a Gemini API key entered in the app's Settings screen
- Voice input: Android speech-recognition service; availability/language depends on the device
- Voice output: Android TextToSpeech engine

## Important scope note
This is an Android adaptation, not a byte-for-byte conversion of the desktop program. The existing desktop automation, Playwright/browser control, PC system controls, and Python plugin runtime depend on desktop operating-system APIs and are not automatically available to Android apps. This first mobile slice provides a native ULTRON-themed interface, Gemini chat, voice input, and text-to-speech. Additional desktop features need explicit Android-native implementations.

The API key is stored in this app's private preferences, but a key embedded in or entered into a client app is not suitable for a public production deployment. Do not commit API keys to GitHub.
