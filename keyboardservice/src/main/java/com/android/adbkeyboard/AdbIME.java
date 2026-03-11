package com.android.adbkeyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Method;

public class AdbIME extends InputMethodService {
	private String IME_MESSAGE = "ADB_INPUT_TEXT";
	private String IME_CHARS = "ADB_INPUT_CHARS";
	private String IME_KEYCODE = "ADB_INPUT_CODE";
	private String IME_META_KEYCODE = "ADB_INPUT_MCODE";
	private String IME_EDITORCODE = "ADB_EDITOR_CODE";
	private String IME_MESSAGE_B64 = "ADB_INPUT_B64";
	private String IME_CLEAR_TEXT = "ADB_CLEAR_TEXT";
	private String IME_ACTION_SEARCH = "ADB_ACTION_SEARCH";
	private String IME_ACTION_GO = "ADB_ACTION_GO";
	private String IME_ACTION_DONE = "ADB_ACTION_DONE";
	private String IME_ACTION_NEXT = "ADB_ACTION_NEXT";
	private String IME_ACTION_SEND = "ADB_ACTION_SEND";
	private BroadcastReceiver mReceiver = null;
	private static final String TAG = "UIWAIT";

	/**
	 * Cached binder for IUiAutomationTextInput service.
	 */
	private android.os.IBinder mTextInputBinder = null;

	/** AIDL interface descriptor for IUiAutomationTextInput. */
	private static final String TEXT_INPUT_DESCRIPTOR =
			"com.android.internal.view.IUiAutomationTextInput";
	/** Transaction code for setTextWithToken (FIRST_CALL_TRANSACTION + 0). */
	private static final int TRANSACTION_setTextWithToken =
			android.os.IBinder.FIRST_CALL_TRANSACTION + 0;

	@Override
	public void onCreate() {
		super.onCreate();
		registerAdbReceiver();
	}

	private void registerAdbReceiver() {
		if (mReceiver == null) {
			IntentFilter filter = new IntentFilter(IME_MESSAGE);
			filter.addAction(IME_CHARS);
			filter.addAction(IME_KEYCODE);
			filter.addAction(IME_MESSAGE);
			filter.addAction(IME_EDITORCODE);
			filter.addAction(IME_MESSAGE_B64);
			filter.addAction(IME_CLEAR_TEXT);
			filter.addAction(IME_ACTION_SEARCH);
			filter.addAction(IME_ACTION_GO);
			filter.addAction(IME_ACTION_DONE);
			filter.addAction(IME_ACTION_NEXT);
			filter.addAction(IME_ACTION_SEND);
			mReceiver = new AdbReceiver();
			registerReceiver(mReceiver, filter);
		}
	}

	@Override
	public View onCreateInputView() {
		View mInputView = getLayoutInflater().inflate(R.layout.view, null);
		return mInputView;
	}

	public void onDestroy() {
		if (mReceiver != null)
			unregisterReceiver(mReceiver);
		super.onDestroy();
	}

	class AdbReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(IME_MESSAGE)) {
				// normal message
				String msg = intent.getStringExtra("msg");
				if (msg != null) {
					InputConnection ic = getCurrentInputConnection();
					if (ic != null)
						ic.commitText(msg, 1);
				}
				// meta codes
				String metaCodes = intent.getStringExtra("mcode"); // Get message.
				if (metaCodes != null) {
					String[] mcodes = metaCodes.split(","); // Get mcodes in string.
					if (mcodes != null) {
						int i;
						InputConnection ic = getCurrentInputConnection();
						for (i = 0; i < mcodes.length - 1; i = i + 2) {
							if (ic != null) {
								KeyEvent ke;
								if (mcodes[i].contains("+")) { // Check metaState if more than one. Use '+' as delimiter
									String[] arrCode = mcodes[i].split("\\+"); // Get metaState if more than one.
									ke = new KeyEvent(
											0,
											0,
											KeyEvent.ACTION_DOWN, // Action code.
											Integer.parseInt(mcodes[i + 1].toString()), // Key code.
											0, // Repeat. // -1
											Integer.parseInt(arrCode[0].toString()) | Integer.parseInt(arrCode[1].toString()), // Flag
											0, // The device ID that generated the key event.
											0, // Raw device scan code of the event.
											KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, // The flags for this key event.
											InputDevice.SOURCE_KEYBOARD // The input source such as SOURCE_KEYBOARD.
									);
								} else { // Only one metaState.
									ke = new KeyEvent(
											0,
											0,
											KeyEvent.ACTION_DOWN, // Action code.
											Integer.parseInt(mcodes[i + 1].toString()), // Key code.
											0, // Repeat.
											Integer.parseInt(mcodes[i].toString()), // Flag
											0, // The device ID that generated the key event.
											0, // Raw device scan code of the event.
											KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, // The flags for this key event.
											InputDevice.SOURCE_KEYBOARD // The input source such as SOURCE_KEYBOARD.
									);
								}
								ic.sendKeyEvent(ke);
							}
						}
					}
				}
			}

			if (intent.getAction().equals(IME_MESSAGE_B64)) {
				String data = intent.getStringExtra("msg");

				byte[] b64 = Base64.decode(data, Base64.DEFAULT);
				String msg = "NOT SUPPORTED";
				try {
					msg = new String(b64, "UTF-8");
				} catch (Exception e) {

				}

				if (msg != null) {
					long token = intent.getLongExtra("token", 0);
					if (token != 0) {
						// Tokenized path: route through system service for fence tracking
						int displayId = intent.getIntExtra("displayId", -1);
						if (displayId < 0) {
							displayId = getCurrentDisplayId();
						}
						callSetTextWithToken(msg, token, displayId);
					} else {
						// Legacy path: direct commitText
						InputConnection ic = getCurrentInputConnection();
						if (ic != null)
							ic.commitText(msg, 1);
					}
				}
			}

			if (intent.getAction().equals(IME_CHARS)) {
				int[] chars = intent.getIntArrayExtra("chars");
				if (chars != null) {
					String msg = new String(chars, 0, chars.length);
					InputConnection ic = getCurrentInputConnection();
					if (ic != null)
						ic.commitText(msg, 1);
				}
			}

			if (intent.getAction().equals(IME_KEYCODE)) {
				int code = intent.getIntExtra("code", -1);
				if (code != -1) {
					InputConnection ic = getCurrentInputConnection();
					if (ic != null)
						ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
				}
			}

			if (intent.getAction().equals(IME_EDITORCODE)) {
				int code = intent.getIntExtra("code", -1);
				if (code != -1) {
					InputConnection ic = getCurrentInputConnection();
					if (ic != null)
						ic.performEditorAction(code);
				}
			}

			if (intent.getAction().equals(IME_CLEAR_TEXT)) {
				InputConnection ic = getCurrentInputConnection();
				if (ic != null) {
					// Try to get extracted text first
					ExtractedTextRequest req = new ExtractedTextRequest();
					req.hintMaxChars = 100000;
					req.hintMaxLines = 10000;
					android.view.inputmethod.ExtractedText et = ic.getExtractedText(req, 0);
					if (et != null && et.text != null) {
						CharSequence beforePos = ic.getTextBeforeCursor(et.text.length(), 0);
						CharSequence afterPos = ic.getTextAfterCursor(et.text.length(), 0);
						if (beforePos != null && afterPos != null) {
							ic.deleteSurroundingText(beforePos.length(), afterPos.length());
						}
					} else {
						// Fallback: select all and delete
						ic.performContextMenuAction(android.R.id.selectAll);
						ic.commitText("", 1);
					}
				}
			}

			// IME Actions - convenient shortcuts
			if (intent.getAction().equals(IME_ACTION_SEARCH)) {
				InputConnection ic = getCurrentInputConnection();
				if (ic != null) {
					ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH);
				}
			}

			if (intent.getAction().equals(IME_ACTION_GO)) {
				InputConnection ic = getCurrentInputConnection();
				if (ic != null) {
					ic.performEditorAction(EditorInfo.IME_ACTION_GO);
				}
			}

			if (intent.getAction().equals(IME_ACTION_DONE)) {
				InputConnection ic = getCurrentInputConnection();
				if (ic != null) {
					ic.performEditorAction(EditorInfo.IME_ACTION_DONE);
				}
			}

			if (intent.getAction().equals(IME_ACTION_NEXT)) {
				InputConnection ic = getCurrentInputConnection();
				if (ic != null) {
					ic.performEditorAction(EditorInfo.IME_ACTION_NEXT);
				}
			}

			if (intent.getAction().equals(IME_ACTION_SEND)) {
				InputConnection ic = getCurrentInputConnection();
				if (ic != null) {
					ic.performEditorAction(EditorInfo.IME_ACTION_SEND);
				}
			}
		}
	}

	/**
	 * Returns the display ID this IME is currently serving.
	 * Uses the IME window's display; falls back to DEFAULT_DISPLAY (0).
	 */
	private int getCurrentDisplayId() {
		try {
			if (getWindow() != null && getWindow().getWindow() != null) {
				View decor = getWindow().getWindow().getDecorView();
				if (decor != null && decor.getDisplay() != null) {
					return decor.getDisplay().getDisplayId();
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "getCurrentDisplayId: failed, defaulting to 0", e);
		}
		return 0;
	}

	private void callSetTextWithToken(String text, long token, int displayId) {
		try {
			if (mTextInputBinder == null) {
				Class<?> smClass = Class.forName("android.os.ServiceManager");
				Method getService = smClass.getMethod("getService", String.class);
				mTextInputBinder = (android.os.IBinder) getService.invoke(
						null, "ui_automation_text_input");
				if (mTextInputBinder == null) {
					Log.w(TAG, "callSetTextWithToken: service not found, "
							+ "falling back to direct commitText");
					InputConnection ic = getCurrentInputConnection();
					if (ic != null) ic.commitText(text, 1);
					return;
				}
			}

			// Build the Parcel matching AIDL-generated Proxy.setTextWithToken()
			android.os.Parcel data = android.os.Parcel.obtain();
			android.os.Parcel reply = android.os.Parcel.obtain();
			try {
				data.writeInterfaceToken(TEXT_INPUT_DESCRIPTOR);
				data.writeString(text);
				data.writeLong(token);
				data.writeInt(displayId);
				mTextInputBinder.transact(TRANSACTION_setTextWithToken,
						data, reply, 0);
				reply.readException();
				Log.d(TAG, "callSetTextWithToken: success, token=" + token
						+ " displayId=" + displayId);
			} finally {
				data.recycle();
				reply.recycle();
			}
		} catch (Exception e) {
			Log.e(TAG, "callSetTextWithToken: transact failed, "
					+ "falling back to direct commitText", e);
			// Fallback: direct commitText (no token/fence tracking)
			InputConnection ic = getCurrentInputConnection();
			if (ic != null) ic.commitText(text, 1);
		}
	}
}
