---
name: compile-kotlin
description: >-
  Mandatory verify step after editing Kotlin in this Android app: run
  compile-kotlin.sh (Java 17 env + compile + all JVM unit tests). Use when
  changing any .kt file, Gradle Kotlin deps, or before finishing any task
  that touches app/src/main/java or app/src/test/java.
---

# Compile Kotlin

After **every** change to Kotlin (`.kt`) files, run the verify script in **this**
session and confirm its `VERIFY OK` line before marking the task done.

Compile-only checks are **not** sufficient — `testDebugUnitTest` must run and pass.

## Mandatory command

**Always** use the project script. Do **not** invoke `./gradlew` directly
(unless you are editing the script itself):

```bash
bash .cursor/skills/compile-kotlin/scripts/compile-kotlin.sh
```

The script:

1. Sources `~/tmp/android-build/env.sh` (Java 17, `ANDROID_HOME`, etc.)
2. Runs `:app:compileDebugKotlin` then `:app:testDebugUnitTest`
3. Parses `app/build/test-results/testDebugUnitTest/` and fails on any failure/error
4. Prints `compile-kotlin: VERIFY OK — compileDebugKotlin + testDebugUnitTest (N tests, 0 failures)`

## Completion gate (hard rule)

Do **not** report the task complete unless **all** of these are true:

- [ ] You ran `compile-kotlin.sh` in **this** conversation turn (not a prior session)
- [ ] The script exited **0**
- [ ] Terminal output contains the exact `compile-kotlin: VERIFY OK` line
- [ ] You fixed and re-ran until pass if compile or any test failed

If you cannot run the script (missing `env.sh`, sandbox, etc.), say so explicitly
and **do not** claim compile or tests passed.

## When to run

- Edit, add, or delete any `.kt` file under `app/`
- Change Gradle deps/plugins that affect Kotlin compilation
- Fix a reported compile or unit-test failure

Do **not** skip because a change "looks trivial" or "only touches tests."

## If verify fails

1. Read the failure (compiler line, Gradle task, or test assertion).
2. Fix source or tests — do not weaken types, skip tests, or comment out code.
3. Re-run `compile-kotlin.sh` until you see `VERIFY OK`.
4. Only then finish the task (and run **update-readme** if user-facing docs changed).

## Forbidden (common failure modes)

| Do not | Why |
| ------ | --- |
| Run `./gradlew` without sourcing `env.sh` | Uses Java 11; compile fails or behaves differently |
| Run only `:app:compileDebugKotlin` | Broken main code can still compile while tests never run |
| Trust stale `BUILD SUCCESSFUL` from an old terminal | Artifacts may predate your edits |
| Say "tests should pass" without running the script | Skill requires proof in this session |
| Mark done when Gradle failed at compile | Tests never execute if compile fails |

## First-time / missing toolchain

If `~/tmp/android-build/env.sh` is missing, install userspace JDK 17, Android SDK
(platform-tools, `platforms;android-34`, `build-tools;34.0.0`), and Gradle 8.10.2
under `~/tmp/android-build`, then bootstrap the wrapper. See README "Build".

Alternatively set `ANDROID_HOME` and `JAVA_HOME` (17+) yourself, or add
`local.properties` with `sdk.dir` (gitignored).

## Notes

- **All JVM unit tests** under `app/src/test/` are included via `testDebugUnitTest`.
- Instrumented tests (`connectedDebugAndroidTest`) are **not** part of this skill.
- Deprecation warnings are OK; compile errors and test failures are not.
- KSP (Room) runs as part of compile.
