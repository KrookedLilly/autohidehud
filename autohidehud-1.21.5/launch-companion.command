#!/bin/bash
# Double-click to launch the AutoHideHUD companion overlay.
# Finds the newest built jar under build/libs and runs it with JDK 21.
set -euo pipefail
cd "$(dirname "$0")"

JDK="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
if [ ! -x "$JDK/bin/java" ]; then
  JDK="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi

JAR=$(ls -t build/libs/autohidehud-*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR" ]; then
  echo "No built jar found. Run ./gradlew build first." >&2
  read -r -p "Press enter to close…" _
  exit 1
fi

exec "$JDK/bin/java" -jar "$JAR"
