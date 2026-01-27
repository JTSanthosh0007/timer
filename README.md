# MindControl - Android Productivity Lock

A minimal productivity app that blocks distracting apps for a set duration.

## Features
- **Mandatory Setup**: Users must select 5 apps (Phone + 4 others).
- **Strict Timer**: Once started, only selected apps allowed. No pause, no exit.
- **Persistence**: survive reboots and prevents force-stops via Accessibility Service.
- **Clean UI**: Minimal dark mode interface.

## Build Instructions
This project is ready to be opened in **Android Studio**.

1. **Open** Android Studio.
2. Select **Open an existing Android Studio project**.
3. Navigate to this folder (`mindcontrol`).
4. Wait for Gradle sync to complete.
5. **Run** on an Emulator or Real Device.

## Permissions
The app requires the following sensitive permissions to function:
1. **Accessibility Service**: To monitor app usage and block disallowed apps. You must enable "MindControl" in Android Settings -> Accessibility.
2. **Notification Permission**: To show the persistent timer.
3. **Query All Packages**: To list installed apps for selection.

## Architecture
- **MainActivity**: UI for app selection and timer display.
- **LockService**: `AccessibilityService` that monitors window changes and enforces blocking.
- **TimerService**: `ForegroundService` that maintains the timer state and prevents process death.
- **BootReceiver**: Restarts the lock if the phone is rebooted during a session.
- **Utils**: Handles `SharedPreferences` logic.
