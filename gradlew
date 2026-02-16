#!/bin/sh
# Gradle wrapper script
exec "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@" 2>/dev/null || \
  gradle "$@" 2>/dev/null || \
  echo "Please install Gradle or Android Studio and run: ./gradlew assembleDebug"
