# SpeedWall Overlay

An Android app to overlay the official Speed Climbing Wall Layout onto a live camera feed at real-world scale.
Calibrate with a known distance and see the route projected in front of you.
Freely select the section You want to set.

## Demo

https://github.com/user-attachments/assets/1d5921c6-44d1-4f19-ada0-a5b633311d99

## Features

- **Two-point calibration** — tap two points of a known distance to establish real-world scale
- **Template overlay** — display the official IFSC Speed Climbing Route over the camera feed
- **Perspective tilt** — horizontal/vertical 3D tilt controls with optional auto-level via device motion
- **Overlay controls** — toggle grid and label layers, pick overlay color, drag to reposition
- **Screenshot capture** — save the camera + overlay composite to your gallery

## Usage

1. **Calibrate** — point the camera at a reference of known length, tap both ends, enter the distance
2. **Overlay** — the layout appears over the camera feed; drag to reposition, use controls to adjust tilt, color, and layer visibility
3. **Screenshot** — tap the camera button to save the current view
4. **Set Route** - Put Holds in position on wall
5. **Train the Move** - Enjoy!

## Requirements

- Android 8.0+ (API 26)
- Android Studio Hedgehog+
- Camera permission (requested at runtime)

## Build & Run

1. Clone the repo
2. Open the project in Android Studio
3. Let Gradle sync to download all dependencies
4. Build and run on a physical device (camera and accelerometer require a real device)

## License

MIT
