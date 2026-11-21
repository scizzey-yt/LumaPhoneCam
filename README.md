# Luma Phone Cam

An Android Wi-Fi microphone and camera sender for OBS Studio. Streams stay on your local network; there are no ads, accounts, or cloud services.

## Install

Install `LumaPhoneCam.apk` on Android 8.0 (API 26) or later. This is a debug-signed APK built for direct installation.

## Use with OBS

1. Connect the phone and PC to the same Wi-Fi network.
2. Open Luma Phone Cam and tap the microphone button. Allow microphone permission.
3. Add the shown `http://PHONE-IP:PORT` URL as an OBS Browser Source. In the source properties, enable **Control audio via OBS**.
4. Tap **Enable Camera** and allow camera permission. For a separate video source, add `http://PHONE-IP:PORT/video.mjpg` as an OBS Browser Source.

Keep Luma Phone Cam open while streaming. Tapping the microphone button stops the local server. The microphone gain slider applies digital gain from 0% to 150%.

## Build from source

Install JDK 17 and Android SDK Platform 35, then run `gradlew.bat assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The app captures audio as mono 48 kHz PCM and serves it as a local streaming WAV response. Camera video is delivered as an MJPEG stream using Camera2. Both streams are served from the phone over the same local HTTP port.

## USB connection

Select **USB** in the app, connect the phone by cable, and enable Android Developer Options → USB debugging. On the Windows PC, install Android Platform Tools, accept the phone's USB debugging prompt, then run:

```bat
adb forward tcp:8080 tcp:8080
```

The app's USB mode listens on device port 8080. Add `http://127.0.0.1:8080` to OBS as a Browser Source. USB debugging is required because Android apps cannot create an ADB port forward on the PC by themselves. Re-run the command after disconnecting and reconnecting the phone.
