# Telestroke - Operator Frontend

The **Telestroke** project aims to create a system to assist doctors (specialist) and first aid personnel (operator) in the assessment of a stroke case gravity (using [NIHSS](https://en.wikipedia.org/wiki/National_Institutes_of_Health_Stroke_Scale)). The system enables the specialist to perform remote reporting, to reduce disease treatment time and improve the quality of the medical supply. This project is my master thesis in Computer Science: [Study and development of a remote reporting system](https://amslaurea.unibo.it/20501/).

The project is structured in 3 main components:
- [backend](https://github.com/DaviGia/telestroke-backend): The microservices backend that handles frontend interaction and implements the main application login
- [web-frontend](https://github.com/DaviGia/telestroke-web-frontend): The web application used by the specialist to:
  * implements a WebRTC peer that sends audio to the operator and receives video and audio feed from his/her
  * remotely guide the operator to assess the patient status (by talking to the operator while watching the patient from the operator's feed)
  * guide him/her to perform the medical report and decide the course of action to treat the stroke
- [android-frontend](https://github.com/DaviGia/telestroke-android-frontend): The Android application used by the operator from hands-free wearable device (e.g. Smartglasses):
  * implements a WebRTC peer sends video and audio feed and receives audio from the specialist and receives audio from his/her
  * can display brief information about the current action that the specialist is performing from his/her device

## Description

An Android application with WebRTC capabilities. It's mean to be used mostly hands-free and it's capable of receive and display information relative to the current action performed by the specialist.

## Configuration

The application needs a single configuration file in `res/raw/config.json`. This is a configuration example:

``` js
{
  "peerjs": {
    "host": "localhost",
    "port": 9000,
    "apiUrl": "/telestroke",
    "apiKey": "peerjs", //change in production env
    "secure": false
  },
  "backend": {
    "host": "localhost",
    "port": 8001,
    "baseUrl": "api",
    "secure": false
  },
  "credentials": {
    "username": "guest",
    "password": "guest" //change in production env
  },
  "peerInfo": {
    "peerId": "my-peer-id",
    "description": "my-peer-description"
  },
  "camera": {
    "device": "1", //the name of the camera device to use
    "format": {
      "width": 640,
      "height": 480,
      "frameRate": 30
    }
  }
}
```

## Notes

- If you use Android emulator to run the app remember that you cannot specify `localhost` for `gateway` or `peerjs-server` because it's not resolved as the host machine address
- If you don't have available camera and/or microphone in the machine you use to connect to the web frontend, you can use built-in Chrome feature to use fake streams for development purposes (`--use-fake-device-for-media-stream`)
- If you want to run the app in a development environment and you cannot expose `gateway` and/or `peerjs-server` as secure endpoints, create a network security configuration following this [article](https://developer.android.com/training/articles/security-config)
