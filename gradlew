#!/usr/bin/env sh

DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_BIN="java"
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  else
    echo "ERROR: JAVA_HOME is set but java not found at $JAVA_HOME/bin/java" >&2
    exit 1
  fi
fi

WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_IMPL_JAR="$DIR/gradle/wrapper/gradle-wrapper-impl.jar"
WRAPPER_SHARED_JAR="$DIR/gradle/wrapper/gradle-wrapper-shared.jar"
if [ ! -f "$WRAPPER_IMPL_JAR" ]; then
  unzip -oq "$WRAPPER_JAR" gradle-wrapper.jar -d "$DIR/gradle/wrapper/tmp"
  mv "$DIR/gradle/wrapper/tmp/gradle-wrapper.jar" "$WRAPPER_IMPL_JAR"
  rm -rf "$DIR/gradle/wrapper/tmp"
fi
CLASSPATH="$WRAPPER_JAR:$WRAPPER_IMPL_JAR:$WRAPPER_SHARED_JAR"
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"
exec "$JAVA_BIN" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
