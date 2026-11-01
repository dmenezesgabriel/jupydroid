# shellcheck shell=sh
# Resolves JAVA_HOME from the Android Studio JBR when the caller has not set one.
# Sourced by every script that shells out to a JVM tool: scripts/gradlew-jbr (Gradle,
# via the pre-commit hooks) and the Maestro evidence drivers. Kept in one place so the
# path is not duplicated; export JAVA_HOME yourself to override it. Built from $HOME so the
# repository never names the account that runs it (scripts/java_home_test.py).
jbr="$HOME/android-studio/jbr"
if [ -z "${JAVA_HOME:-}" ] && [ -x "$jbr/bin/java" ]; then
  export JAVA_HOME="$jbr"
fi
unset jbr
