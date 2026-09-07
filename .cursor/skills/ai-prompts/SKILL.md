---
name: ai-prompts
description: >-
  Keep all OpenAI / LLM prompt text in AiPrompts.kt as named constants (or
  interpolating functions). Use when adding, editing, or moving prompts for
  Chart Assistant, camera flatten, scan OCR, chat completions, image edits,
  Whisper, OpenAiClient, ChartAssistantService, or LocalFileImport.
---

# AI prompts

Always add the prompt constant to `app/src/main/java/com/playlists/app/ai/AiPrompts.kt` and import them from there.

## Rules

1. **Single file** — every prompt string lives on `AiPrompts`. Do not inline system, user, or image-edit prompt text in `OpenAiClient`, `ChartAssistantService`, ViewModels, `LocalFileImport`, or camera import helpers.
2. **Named constants** — static prompts are `val` / `const val` on `AiPrompts` (`EXTRACT_TITLE_FROM_IMAGE_SYSTEM`, `FLATTEN_IMAGE`, …).
3. **Interpolation** — prompts that take runtime values are functions on `AiPrompts` (`parseIntentSystem`, `extractChartSystem`). Keep reusable fragments as constants and compose them in the function.
4. **Import** — callers use `AiPrompts.NAME` or `AiPrompts.function(...)`. Do not duplicate the string.
5. **New prompts** — add the constant (or function) to `AiPrompts.kt` first, then wire the call site.

## Current prompts

| Constant / function | Used by |
| ------------------- | ------- |
| `parseIntentSystem` | Chart assistant voice intent |
| `extractChartSystem` (+ `EXTRACT_CHART_*` fragments) | Chart assistant page extract |
| `EXTRACT_TITLE_FROM_IMAGE_SYSTEM` / `_USER` | Camera / gallery title OCR |
| `FLATTEN_IMAGE` | Camera page flatten (`gpt-image-1`) |

## After changing prompts

Run **rebuild-app**. Update **update-readme** only if user-facing AI behaviour changed.
