# Luna Phone MicCam

Luna Phone MicCam turns an Android phone into a low-latency microphone and camera source for OBS over local Wi-Fi. Audio is sent as short PCM WebSocket packets rather than a buffered media file. No ads, accounts, or cloud service.

## Install

Install `LunaPhoneMicCam.apk` on Android 8.0 (API 26) or later. This is a debug-signed APK for direct installation. Android treats this renamed package as a separate app; the earlier install can be removed after switching. 

## Audio in OBS

1. Connect the phone and PC to the same Wi-Fi network.
2. Open Luna Phone MicCam and tap **Start Mic**. Allow microphone access.
3. Tap **Copy Audio Source**.
4. In OBS, add a **Browser Source**, paste the copied URL, and set Width and Height to the pixel values shown in the app.
5. Enable **Control audio via OBS** in the Browser Source properties. Keep the source visible and unmuted.

If OBS reports a browser audio gesture is needed, right-click the Browser Source, choose **Interact**, and tap **Start Audio** in the page. The app shows **OBS Connected** when the browser source opens its audio link.

## Camera in OBS

Tap **Enable Camera**, choose the front or back camera, then tap **Copy Camera Source**. Add it as another Browser Source with the same Width and Height values. Video is delivered as a live MJPEG stream; the source can wait while the phone camera is off and begin showing frames when enabled.

## Latency and format

Microphone capture is mono 48 kHz PCM in 10 ms blocks. Audio reaches OBS over a direct WebSocket with a short bounded queue to prevent network hiccups from building a playback backlog. Camera output is JPEG over local MJPEG HTTP. Both streams stay on the phone and local network.

## Build from source

Install JDK 17 and Android SDK Platform 35, then run `gradlew.bat assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.


