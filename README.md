# Luma Phone Cam

An Android app that sends your phone's microphone and camera to OBS over your local Wi-Fi. Audio uses a direct PCM WebSocket stream instead of a browser media element, avoiding the long buffering delay of the earlier WAV stream. The app has no ads, accounts, or cloud service.

## Install

Install `LumaPhoneCam.apk` on Android 8.0 (API 26) or later. This is a debug-signed APK for direct installation.

## Audio in OBS

1. Connect the phone and PC to the same Wi-Fi network.
2. Open Luma Phone Cam and tap **Start Mic**. Allow microphone access.
3. Tap **Copy Audio Source**.
4. In OBS, add a **Browser Source**, paste the copied URL, and set Width and Height to the pixel values shown in the app.
5. Enable **Control audio via OBS** in the Browser Source properties. Keep the source visible and unmuted.

If OBS reports a browser audio gesture is needed, right-click the Browser Source, choose **Interact**, and tap **Start Audio** in the page. The app shows **OBS Connected** when the browser source opens its audio link.

## Camera in OBS

Tap **Enable Camera**, choose the front or back camera, then tap **Copy Camera Source**. Add it as another Browser Source with the same Width and Height values. Video is delivered as a live MJPEG stream; the source can wait while the phone camera is off and begin showing frames when enabled.

## Latency and format

Microphone capture is mono 48 kHz PCM in 10 ms blocks. Audio reaches the OBS browser page over a direct WebSocket with a short bounded queue so network hiccups cannot build seconds of playback backlog. Camera output is JPEG over a local MJPEG HTTP stream. Both endpoints stay on the phone and local network.

## Build from source

Install JDK 17 and Android SDK Platform 35, then run `gradlew.bat assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
