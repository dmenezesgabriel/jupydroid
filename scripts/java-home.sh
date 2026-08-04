# shellcheck shell=sh
# Resolves JAVA_HOME from the Android Studio JBR when the caller has not set one.
# Sourced by every script that shells out to a JVM tool: scripts/gradlew-jbr (Gradle,
# via the pre-commit hooks) and the Maestro evidence drivers. Kept in one place so the
# machine-specific path is not duplicated; export JAVA_HOME yourself to override it.
jbr=/home/user/android-studio/jbr
if [ -z "${JAVA_HOME:-}" ] && [ -x "$jbr/bin/java" ]; then
  export JAVA_HOME="$jbr"
fi
unset jbr
