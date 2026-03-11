ADBKeyBoard 
===========

Android Virtual Keyboard Input via ADB

ADBKeyBoard is a virtual keyboard that receives commands from system broadcast intents, which you can send text input using adb.

There is a shell command 'input', which can help you send text input to the Android system. 
<pre>
usage: input [text|keyevent]
  input text <string>
  input keyevent <event_code>
</pre>
  
But you cannot send unicode characters using this command, as it is not designed to use it this way.
<br />
Reference : http://stackoverflow.com/questions/14224549/adb-shell-input-unicode-character
<pre>
e.g.
adb shell input text '你好嗎' 
is not going to work.
</pre>

ADBKeyboard will help in these cases, especially in device automation and testings.

Download APK from release page
---------------------
* [Dev] Latest APK download: [https://github.com/senzhk/ADBKeyBoard/releases/download/v2.4-dev/keyboardservice-debug.apk]
* [Old] Release APK download: [https://github.com/senzhk/ADBKeyBoard/blob/master/ADBKeyboard.apk]

Build and install APK
---------------------

Requires: Java 17, Gradle 8.3, AGP 8.1.0 (compileSdk 33, minSdk 21).

With one device or emulator connected, use these simple steps to install the keyboard:

 * Get source: `git clone https://github.com/senzhk/ADBKeyBoard.git`
 * Go into project dir `cd ADBKeyBoard`
 * Set Android SDK location: `export ANDROID_HOME=$HOME/Android/Sdk` or edit file `local.properties`
 * Build: `./gradlew assembleDebug`
 * Install: `adb install keyboardservice/build/outputs/apk/debug/keyboardservice-debug.apk`
 * Or build and install in one step: `./gradlew installDebug`

**Note**: ADBKeyBoard is a user-installed APK (not part of the AOSP system image). After a Cuttlefish CVD restart or factory reset, you must re-install and re-activate:
```
adb install keyboardservice/build/outputs/apk/debug/keyboardservice-debug.apk
adb shell ime enable com.android.adbkeyboard/.AdbIME
adb shell ime set com.android.adbkeyboard/.AdbIME
```

How to Use
----------

 * Enable 'ADBKeyBoard' in the Language&Input Settings OR from adb.
```
adb install ADBKeyboard.apk 
adb shell ime enable com.android.adbkeyboard/.AdbIME
adb shell ime set com.android.adbkeyboard/.AdbIME   
```
 * Set it as Default Keyboard OR Select it as the current input method of certain EditText view.
 * Sending Broadcast intent via Adb or your Android Services/Apps.

Usage Example:
<pre>
1. Sending text input
adb shell am broadcast -a ADB_INPUT_TEXT --es msg '你好嗎? Hello?'

* This may not work for Oreo/P, am/adb command seems not accept utf-8 text string anymore

1.1 Sending text input (base64) if (1) is not working.

* For Mac/Linux, you can use the latest base64 input type with base64 command line tool:
adb shell am broadcast -a ADB_INPUT_B64 --es msg `echo -n '你好嗎? Hello?' | base64`

* For Windows, please try this script (provided by ssddi456): 
https://gist.github.com/ssddi456/889d5e8a2571a33e8fcd0ff6f1288291

* Sample python script to send b64 codes (provided by sunshinewithmoonlight):
import os
import base64
chars = '的广告'
charsb64 = str(base64.b64encode(chars.encode('utf-8')))[1:]
os.system("adb shell am broadcast -a ADB_INPUT_B64 --es msg %s" %charsb64)

2. Sending keyevent code  (67 = KEYCODE_DEL)
adb shell am broadcast -a ADB_INPUT_CODE --ei code 67

3. Sending editor action (2 = IME_ACTION_GO)
adb shell am broadcast -a ADB_EDITOR_CODE --ei code 2

4. Sending unicode characters
To send 😸 Cat
adb shell am broadcast -a ADB_INPUT_CHARS --eia chars '128568,32,67,97,116'

5. Send meta keys
To send Ctrl + A as below: (4096 is META_CONTROL_ON, 8192 is META_CONTROL_LEFT_ON, 29 is KEYCODE_A)
adb shell am broadcast -a ADB_INPUT_TEXT --es mcode '4096,29' // one metaState.
or
adb shell am broadcast -a ADB_INPUT_TEXT --es mcode '4096+8192,29' // two metaState.


6. CLEAR all text (starting from v2.0)
adb shell am broadcast -a ADB_CLEAR_TEXT

7. Tokenized text input (for AOSP UI automation fence system)

* Send text with a UiActionToken for deterministic fence-based completion detection.
* When token != 0, AdbIME routes through IUiAutomationTextInput AIDL to system_server,
  which delivers text to the focused app atomically on the UI thread with fence emission.
* When token == 0 or absent, behaves identically to normal ADB_INPUT_B64.

adb shell am broadcast -a ADB_INPUT_B64 --es msg `echo -n 'hello' | base64` --el token 12345

* Optionally specify displayId for multi-display systems (default: auto-detect from IME window):
adb shell am broadcast -a ADB_INPUT_B64 --es msg `echo -n 'hello' | base64` --el token 12345 --ei displayId 1

* The tokenized path produces 3 fence events via AccessibilityService:
  - DISPATCH_END: text committed to EditText
  - FRAME: UI frame rendered
  - PRESENT: frame presented on display
* Plus 1 target info event (TYPE_UI_AUTOMATION_TEXT_TARGET) with resource_id, class_name, bounds
  of the focused EditText, emitted before DISPATCH_END.

</pre>

Enable ADBKeyBoard from adb :
<pre>
adb shell ime enable com.android.adbkeyboard/.AdbIME
</pre>

Switch to ADBKeyBoard from adb (by [robertio](https://github.com/robertio)) :
<pre>
adb shell ime set com.android.adbkeyboard/.AdbIME   
</pre>

Switch back to original virtual keyboard: (swype in my case...)
<pre>
adb shell ime set com.nuance.swype.dtc/com.nuance.swype.input.IME  
</pre>

Check your available virtual keyboards:
<pre>
adb shell ime list -a  
</pre>

Reset to default, don't care which keyboard was chosen before switch:
<pre>
adb shell ime reset
</pre>

You can try the apk with my debug build: https://github.com/senzhk/ADBKeyBoard/raw/master/ADBKeyboard.apk

KeyEvent Code Ref: http://developer.android.com/reference/android/view/KeyEvent.html

Editor Action Code Ref: http://developer.android.com/reference/android/view/inputmethod/EditorInfo.html
