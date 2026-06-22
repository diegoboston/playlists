---
name: rebuild-app
description: >-
  Mandatory verify step after editing this Android app: run rebuild-app.sh
  (Java 17 env, compile, JVM unit tests, debug APK). Use when changing any
  file under app/, Gradle/Android config, resources, manifest, or before
  finishing any task that touches the app build.
---

# Rebuild App

After **every** change that affects the app, run the rebuild script in **this**
session and confirm its `VERIFY OK` line before marking the task done.

Compile-only or test-only checks are **not** sufficient — `assembleDebug` must
also succeed so packaging and resources are validated.

## Mandatory command

**Always** use the project script. Do **not** invoke `./gradlew` directly
(unless you are editing the script itself):

```bash
bash .cursor/skills/rebuild-app/scripts/rebuild-app.sh
```

The script:

1. Sources `~/tmp/android-build/env.sh` (Java 17, `ANDROID_HOME`, etc.)
2. Runs `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`
3. Parses `app/build/test-results/testDebugUnitTest/` and fails on any failure/error
4. Confirms a debug APK exists under `app/build/outputs/apk/debug/`
5. Prints `rebuild-app: VERIFY OK — compileDebugKotlin + testDebugUnitTest (N tests, 0 failures) + assembleDebug (path/to.apk)`

## Completion gate (hard rule)

Do **not** report the task complete unless **all** of these are true:

- [ ] You ran `rebuild-app.sh` in **this** conversation turn (not a prior session)
- [ ] The script exited **0**
- [ ] Terminal output contains the exact `rebuild-app: VERIFY OK` line
- [ ] You fixed and re-ran until pass if compile, tests, or APK build failed

If you cannot run the script (missing `env.sh`, sandbox, etc.), say so explicitly
and **do not** claim the app rebuilt successfully.

## When to run

- Edit, add, or delete any file under `app/` (Kotlin, resources, manifest, assets)
- Change `build.gradle.kts`, `gradle.properties`, or Gradle wrapper/deps
- Fix a reported compile, test, or CI build failure
- Before pushing or opening a PR

Do **not** skip because a change "looks trivial" or "only touches strings/XML."

## If verify fails

1. Read the failure (compiler line, Gradle task, test assertion, or missing APK).
2. Fix source, resources, or tests — do not weaken types, skip tests, or comment out code.
3. Re-run `rebuild-app.sh` until you see `VERIFY OK`.
4. Only then finish the task (and run **update-readme** if user-facing docs changed).

## Faster subset (optional)

For a quick Kotlin-only check when the user explicitly asks **not** to package an APK,
use **compile-kotlin** instead. It does **not** replace `rebuild-app` for task completion.

## Forbidden (common failure modes)

| Do not | Why |
| ------ | --- |
| Run `./gradlew` without sourcing `env.sh` | Uses Java 11; compile fails or behaves differently |
| Run only `:app:compileDebugKotlin` | Resources/packaging errors are missed; tests may not run |
| Trust stale `BUILD SUCCESSFUL` from an old terminal | Artifacts may predate your edits |
| Say "should build" without running the script | Skill requires proof in this session |
| Mark done when Gradle failed at compile | Tests and APK never execute if compile fails |

## First-time / missing toolchain

If `~/tmp/android-build/env.sh` is missing, install userspace JDK 17, Android SDK
(platform-tools, `platforms;android-34`, `build-tools;34.0.0`), and Gradle 8.10.2
under `~/tmp/android-build`, then bootstrap the wrapper. See README "Build".

Alternatively set `ANDROID_HOME` and `JAVA_HOME` (17+) yourself, or add
`local.properties` with `sdk.dir` (gitignored).

## Notes

- Matches CI's compile + unit-test gate; CI also runs `assembleRelease` separately.
- Instrumented tests (`connectedDebugAndroidTest`) are **not** part of this skill.
- Deprecation warnings are OK; compile errors, test failures, and APK failures are not.
- KSP (Room) runs as part of compile.
