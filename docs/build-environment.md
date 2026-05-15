# Build Environment Notes

Last checked in the initial VPS workspace:

- Java/JDK: not installed
- Gradle: not installed
- ANDROID_HOME / ANDROID_SDK_ROOT: not set
- Android SDK: not present at `~/Android/Sdk`
- CMake: available
- Ninja: not installed
- pytest: available

Implication: host-side architecture tests can run now, but Android APK builds require a build host bootstrap.

## Required Android build stack

- JDK 17+
- Gradle wrapper or system Gradle for wrapper generation
- Android SDK commandline-tools
- Android platform 35
- Android build-tools
- Android NDK
- CMake 3.22.1+

## Preferred bootstrap policy

Prefer user-local installation under `~/Android/Sdk` and project-local Gradle wrapper. Avoid relying on globally mutable system state unless explicitly chosen.

## Verification gates

Do not claim APK build success until this command runs successfully:

```bash
./gradlew :app:assembleDebug
```

Do not claim device runtime success until a debug APK is installed and `MainActivity` displays the native runtime report on an Android device or emulator.
