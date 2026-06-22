#!/usr/bin/env bash
# Compile :app debug Kotlin and run all JVM unit tests.
# Exits non-zero unless compile succeeds, tests run, and every test passes.
set -euo pipefail

ENV_SH="${ANDROID_BUILD_ENV:-$HOME/tmp/android-build/env.sh}"
if [[ ! -f "$ENV_SH" ]]; then
  echo "compile-kotlin: ERROR — missing build env: $ENV_SH" >&2
  echo "Install JDK 17 + Android SDK under ~/tmp/android-build and create env.sh first." >&2
  echo "Do not run ./gradlew without sourcing env.sh (Gradle needs Java 17)." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_SH"

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "compile-kotlin: ERROR — JAVA_HOME is unset after sourcing $ENV_SH" >&2
  exit 1
fi

java_major="$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n 's/.* version "\([0-9]*\).*/\1/p' | head -1)"
if [[ -z "$java_major" || "$java_major" -lt 17 ]]; then
  echo "compile-kotlin: ERROR — Java 17+ required (JAVA_HOME=$JAVA_HOME, major=$java_major)" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$ROOT"

echo "compile-kotlin: running :app:compileDebugKotlin :app:testDebugUnitTest (Java $java_major)"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest "$@"

RESULTS_DIR="app/build/test-results/testDebugUnitTest"
if [[ ! -d "$RESULTS_DIR" ]]; then
  echo "compile-kotlin: ERROR — $RESULTS_DIR missing; testDebugUnitTest did not run" >&2
  exit 1
fi

shopt -s nullglob
xml_files=("$RESULTS_DIR"/TEST-*.xml)
shopt -u nullglob
if (( ${#xml_files[@]} == 0 )); then
  echo "compile-kotlin: ERROR — no TEST-*.xml under $RESULTS_DIR" >&2
  exit 1
fi

failures=0
errors=0
tests=0
for f in "${xml_files[@]}"; do
  line="$(grep -m1 '<testsuite ' "$f")"
  failures=$((failures + $(sed -n 's/.* failures="\([0-9]*\)".*/\1/p' <<<"$line")))
  errors=$((errors + $(sed -n 's/.* errors="\([0-9]*\)".*/\1/p' <<<"$line")))
  tests=$((tests + $(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$line")))
done

if (( failures > 0 || errors > 0 )); then
  echo "compile-kotlin: ERROR — unit tests failed ($failures failures, $errors errors in $tests tests)" >&2
  exit 1
fi

if (( tests == 0 )); then
  echo "compile-kotlin: ERROR — zero tests recorded; testDebugUnitTest may not have executed" >&2
  exit 1
fi

echo "compile-kotlin: VERIFY OK — compileDebugKotlin + testDebugUnitTest ($tests tests, 0 failures)"
