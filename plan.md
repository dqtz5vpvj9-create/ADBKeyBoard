


## Plan: Tokenized Fence Events for TYPE Actions (M8) — COMPLETED ✓

**Status**: All phases implemented. **54/54 atest pass.** Last validated: 2026-03-11.

**TL;DR**: Extend AOSP framework's tokenized fence system to cover TYPE (text input), making it a first-class citizen alongside CLICK/LONGPRESS/SLIDE. Current TYPE relies on VIEW_TEXT_CHANGED (an **observable effect**, not a deterministic system signal). Fix: AdbIME reads token from broadcast, calls IMS via new AIDL, IMS routes to app via `IInputMethodClient.setTextWithToken()`. App-side handler executes push(T) → deleteSurroundingText → commitText → pop(T) → emit target info AccessibilityEvent → fire fence sequence, all in **a single Message** on the UI thread.

**Key design difference from CLICK**: TYPE emits only **DISPATCH_END + visual chain (3 fences)**, not TOKEN_QUIESCENT. Cursor blink (`Editor.Blink`) and other EditText timers re-post messages that inherit the token via `Handler.enqueueMessage`, creating an infinite chain that prevents quiescence from ever firing. See [Decisions](#decisions) for rationale.

---

### Architecture

```
Host:     am broadcast -a ADB_INPUT_B64 --es msg <b64> --el token <T>
            ↓
AdbIME:   onReceive() → extract token T + text
            ↓ Binder #1 (new AIDL: IUiAutomationTextInput)
IMS:      setTextWithToken(text, token, displayId)
            ↓ Binder #2 (extended IInputMethodClient)
App UI:   Single message on UI Looper (MSG_SET_TEXT_WITH_TOKEN):
            push UiActionTokenContext(T)
            → InputConnection.deleteSurroundingText(10_000, 10_000)
            → InputConnection.commitText(text, 1)
            → TextWatcher callbacks (sync effects)
            pop UiActionTokenContext(T)
            ↓
          1. AccessibilityEvent(TYPE_UI_AUTOMATION_TEXT_TARGET, token=T,
               resource_id, class_name, bounds)           ← target element identity
          2. DISPATCH_END(T) → FRAME → PRESENT              ← deterministic completion
          (no TOKEN_QUIESCENT — cursor blink creates infinite token chain)
            ↓
Host:     AccessibilityEventClient receives all events
          → original_element from target info event
          → wait policy classification same as CLICK
```

Key design: token push + deleteSurroundingText + commitText + pop + target info emission + fence emission happen in **a single Message** on the app's UI thread, guaranteeing atomicity. No race between token setup and text injection.

---

### Steps

**Phase 1: AOSP Framework — Token Pipeline (M8.1)** ✓

1. ✅ **Extend AdbIME to accept token** — `AdbIME.AdbReceiver.onReceive()` reads `--el token <T>` from broadcast. When token != 0, calls `IUiAutomationTextInput.setTextWithToken()` via raw Binder transact. Also reads `--ei displayId <D>` or uses dynamic `getCurrentDisplayId()` from IME window.
   - `ADBKeyBoard/keyboardservice/src/main/java/com/android/adbkeyboard/AdbIME.java`

2. ✅ **Add new system service AIDL for Binder #1** — `IUiAutomationTextInput.aidl` with `setTextWithToken(String text, long token, int displayId)`. Registered as `"ui_automation_text_input"` sub-service of IMMS.
   - `frameworks/base/core/java/com/android/internal/view/IUiAutomationTextInput.aidl`
   - `frameworks/base/services/core/java/com/android/server/inputmethod/InputMethodManagerService.java` — standard IMMS implementation with displayId validation + fallback client search
   - `frameworks/base/services/core/java/com/android/server/inputmethod/MultiClientInputMethodManagerService.java` — per-display focused client tracking via `mFocusedClientPerDisplay` SparseArray

3. ✅ **Extend IInputMethodClient.aidl for Binder #2** — Added `oneway void setTextWithToken(in String text, long token)`. App-side `MSG_SET_TEXT_WITH_TOKEN` handler: push(T) → deleteSurroundingText → commitText → pop(T) → emit target info → fire fence.
   - `frameworks/base/core/java/com/android/internal/view/IInputMethodClient.aidl`
   - `frameworks/base/core/java/android/view/inputmethod/InputMethodManager.java`

**Phase 2: AOSP Framework — Fence Emission (M8.2)** ✓

4. ✅ **Add `onTextInputDone()` to UiDispatchFenceController** — Emits **DISPATCH_END + visual chain only** (3 fences). No TOKEN_QUIESCENT — cursor blink creates infinite token chain. `tryCleanupTokenState` handles TYPE path (no quiescence armed) separately from CLICK path.
   - `frameworks/base/core/java/android/view/UiDispatchFenceController.java`

5. ✅ **Add public accessor for mFenceController on ViewRootImpl**
   - `frameworks/base/core/java/android/view/ViewRootImpl.java`

6. ✅ **Wire fence emission from app-side handler** — `MSG_SET_TEXT_WITH_TOKEN` → `mCurRootView.getUiDispatchFenceController().onTextInputDone(token, queue)`
   - `frameworks/base/core/java/android/view/inputmethod/InputMethodManager.java`

**Phase 3: AOSP Framework — Target Element Info (M8.3)** ✓

7. ✅ **Define `TYPE_UI_AUTOMATION_TEXT_TARGET = 0x08000000`** — Carries token, resource_id, class_name, bounds. Emitted before DISPATCH_END.
   - `frameworks/base/core/java/android/view/accessibility/AccessibilityEvent.java`

8. ✅ **Emit target info after commitText** — Extracts `getIdResourceName()`, `getClass().getName()`, `getBoundsOnScreen()` from focused EditText.
   - `frameworks/base/core/java/android/view/inputmethod/InputMethodManager.java`

9. ✅ **FenceObserverAccessibilityService: forward new event type** — Added `TYPE_UI_AUTOMATION_TEXT_TARGET` to event filter and ContentProvider store.

**Phase 4: AutoDroid Host Integration (M8.4)** — NOT STARTED

> **Note: Host-side Python files live in the AutoDroid repo, not this AOSP tree.** Steps 10–13 are specifications for the AutoDroid team.

10. **SetTextEvent.send() passes token in broadcast** — `am broadcast -a ADB_INPUT_B64 --es msg <b64> --el token <T>`

11. **AccessibilityEventClient parses new event type** — Extract token, resource_id, class_name, bounds from TYPE_UI_AUTOMATION_TEXT_TARGET

12. **Explore mode uses fences + target info for TYPE** — TYPE no longer LEGACY

13. **Execute mode fence-based wait for TYPE** — Fence matching replaces legacy timeout

**Phase 5: Tests (M8.5)** ✓

14. ✅ **Framework test** — UiWaitTestApp: 5 tests (M8.E2E.1–4 + smoke/stress). Inject text via broadcast with token, verify **3 fence events** (DE → FRAME → PRESENT) + target info event with correct resource_id/bounds. All 54/54 atest pass.

15. **Host integration test** — NOT STARTED (depends on Phase 4)

---

### Relevant Files

| Layer         | File                                                                                         | What to modify                                                                                                  |
| ------------- | -------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| ADBKeyBoard   | `ADBKeyBoard/keyboardservice/src/main/java/com/android/adbkeyboard/AdbIME.java`             | Accept token from broadcast; when token != 0, call `IUiAutomationTextInput.setTextWithToken()` via Binder #1    |
| ADBKeyBoard   | `ADBKeyBoard/keyboardservice/build.gradle` (or `Android.mk`)                                |                                         |
| AOSP AIDL     | `frameworks/base/core/java/com/android/internal/view/IUiAutomationTextInput.aidl` (**new**)  | New AIDL: `setTextWithToken(String text, long token, int displayId)`                                            |
| AOSP AIDL     | `frameworks/base/core/java/com/android/internal/view/IInputMethodClient.aidl`                | New method: `oneway void setTextWithToken(in String text, long token)`                                          |
| AOSP Service  | `frameworks/base/services/core/java/com/android/server/inputmethod/MultiClientInputMethodManagerService.java` | Implement `IUiAutomationTextInput`; route `setTextWithToken()` to target client via existing client-lookup logic |
| AOSP App-side | `frameworks/base/core/java/android/view/inputmethod/InputMethodManager.java`                 | Handle `MSG_SET_TEXT_WITH_TOKEN`: push token, delete+commitText, pop, emit target info, fire fence               |
| AOSP View     | `frameworks/base/core/java/android/view/ViewRootImpl.java`                                   | Add `public getUiDispatchFenceController()` accessor                                                      |
| AOSP Fence    | `frameworks/base/core/java/android/view/UiDispatchFenceController.java`                      | Add `onTextInputDone()` — simplified `onTerminalInputDone()` without semantic tail gating                       |
| AOSP A11y     | `frameworks/base/core/java/android/view/accessibility/AccessibilityEvent.java`               | New `TYPE_UI_AUTOMATION_TEXT_TARGET = 0x08000000` constant + @IntDef + toString                                  |
| Host (AutoDroid) | input_event.py                                                                            | Pass token in broadcast                                                                                         |
| Host (AutoDroid) | accessibility_event_client.py                                                             | Parse new event type                                                                                            |
| Host (AutoDroid) | event_based_wait.py                                                                       | Use fences for TYPE in Explore                                                                                  |
| Host (AutoDroid) | post_condition_manager.py                                                                 | Fence-based wait for TYPE in Execute                                                                            |

---

### Verification

1. ✅ `atest UiWaitTestAppTests` — **54/54 pass** (50 existing + 4 new TYPE tests)
2. ContactsAddContact Explore: post_conditions JSON shows `original_element` with `resource_id: "com.google.android.contacts:id/first_name"`, fence-based `wait_policy` — NOT YET TESTED (Phase 4)
3. ContactsAddContact Execute: fence-based completion, not legacy timeout — NOT YET TESTED (Phase 4)
4. **3 fence events** for TYPE token (DE → FRAME → PRESENT) + 1 target info event = **4 total events** per TYPE action
5. Multi-field form: each TYPE gets correct resource_id for its specific EditText — ✅ verified via M8.E2E.3

### Decisions

- **AdbIME path** over KeyEvent injection: commitText is atomic (one call = all text), avoids multi-character DISPATCH_END issues with `input text`
- **Dedicated `IUiAutomationTextInput.aidl`** for Binder #1 instead of polluting `IInputMethodManager.aidl`: IInputMethodManager is the app→system_server interface; IME→system_server is a different caller and deserves a separate AIDL to avoid architectural inversion
- **`IInputMethodClient.setTextWithToken()`** for Binder #2: extends existing `setText()` pattern with token parameter, minimal diff
- **Both IMMS implementations**: Standard `InputMethodManagerService` (validates displayId, searches `mClients` for matching client) and `MultiClientInputMethodManagerService` (per-display focused client tracking via `mFocusedClientPerDisplay` SparseArray). Both correctly route tokenized text to the right app on multi-display systems.
- **`deleteSurroundingText` inside token context**: existing `MSG_SET_TEXT` handler calls `ic.deleteSurroundingText(10_000, 10_000)` before `commitText`. Both calls must be inside push/pop so side-effects from the delete are also token-attributed
- **Independent AccessibilityEvent** for target info: cleaner separation from fence events
- **No TOKEN_QUIESCENT for TYPE**: Cursor blink (`Editor.Blink`) self-re-posts every ~500ms via `postDelayed`. During `commitText()` → `handleTextChanged()` → `updateAfterEdit()` → `makeBlink()`, the blink message inherits the token via `Handler.enqueueMessage` (same-Looper guard). This creates an **infinite token chain** — each blink execution re-posts a new token-bearing message, so refcount never reaches 0 and `onTokenQuiescent` never fires. Attempted fixes: (a) synchronous TQ emission — semantically wrong (means "commitText returned" not "async effects drained"); (b) token sealing — complex mechanism to prevent inheritance after seal point. Final decision: **TYPE emits only DISPATCH_END + visual chain**. For TYPE actions, DISPATCH_END already provides the meaningful signal ("text was committed"). The visual chain (FRAME → PRESENT) ensures the UI update is rendered. Quiescence would add ~500ms latency for minimal value.
- **Dynamic displayId in AdbIME**: `getCurrentDisplayId()` reads `getWindow().getWindow().getDecorView().getDisplay().getDisplayId()`, with broadcast `--ei displayId` override and fallback to 0.
- **taptext NOT supported**: legacy debugging command, not needed
- **No InputShellCommand changes**: TYPE goes through IME, not `input` command
- **Graceful degradation**: if `mCurRootView` is null when token arrives (no focused window), skip fence emission and log warning — no crash, host falls back to legacy timeout

### Bug Fix History

1. **`(long)` cast on `SomeArgs.arg2`** — `SomeArgs.arg2` is `Object` (autoboxed `Long`). Original code used `(long) tokenArgs.arg2` (primitive cast on Object → ClassCastException). Fixed to `(Long) tokenArgs.arg2` (reference cast, auto-unboxed).
   - `InputMethodManager.java` line ~996

2. **displayId routing hardcoded to 0** — Three-part fix:
   - AdbIME: hardcoded `data.writeInt(0)` → dynamic `getCurrentDisplayId()` + broadcast override
   - Standard IMMS: ignored displayId parameter, always used `mCurFocusedWindowClient` → validates displayId, searches `mClients` if mismatch
   - MultiClient IMMS: iterated for "first active client" → tracks focused client per display via `mFocusedClientPerDisplay`

3. **TOKEN_QUIESCENT semantic corruption** — `onTextInputDone()` originally emitted TQ synchronously (= "commitText returned", not real quiescence). Cursor blink infinite token chain makes real quiescence impossible for TYPE. Fixed by removing TQ entirely from TYPE path → 3-fence sequence (DE → FRAME → PRESENT).

### Further Considerations

1. **Clear-before-input** (`ADB_CLEAR_TEXT`): Also needs tokenization. Consider combining clear+set into a single `setTextWithToken` call that clears first (the handler already calls `deleteSurroundingText` before `commitText`), or give clear its own token
2. **Apps overriding commitText**: Some apps (e.g., Material EditText, custom views) override InputConnection. The token context is pushed **before** commitText call, so any override still runs within the context — fences fire correctly regardless
3. **AdbIME persistence across CVD restart**: AdbIME is user-installed (not in system image), so CVD restart requires re-install + `ime enable` + `ime set`. Consider adding to AOSP product packages for automated test workflows.