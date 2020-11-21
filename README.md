# Telestroke - Android Frontend

Android frontend.

## Configuration

The application needs a single configuration file in `res/raw/config.json`.
An example of configuration is the following:

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
