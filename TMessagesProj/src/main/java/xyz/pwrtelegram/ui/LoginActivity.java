/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package xyz.pwrtelegram.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import xyz.pwrtelegram.messenger.AndroidUtilities;
import xyz.pwrtelegram.messenger.ContactsController;
import xyz.pwrtelegram.messenger.MessagesController;
import xyz.pwrtelegram.messenger.MessagesStorage;
import xyz.pwrtelegram.messenger.NotificationCenter;
import xyz.pwrtelegram.messenger.ApplicationLoader;
import xyz.pwrtelegram.messenger.BuildVars;
import xyz.pwrtelegram.messenger.FileLog;
import xyz.pwrtelegram.messenger.LocaleController;
import xyz.pwrtelegram.messenger.R;
import xyz.pwrtelegram.tgnet.ConnectionsManager;
import xyz.pwrtelegram.tgnet.RequestDelegate;
import xyz.pwrtelegram.tgnet.TLObject;
import xyz.pwrtelegram.tgnet.TLRPC;
import xyz.pwrtelegram.messenger.UserConfig;
import xyz.pwrtelegram.messenger.Utilities;
import xyz.pwrtelegram.ui.ActionBar.ActionBar;
import xyz.pwrtelegram.ui.ActionBar.ActionBarMenu;
import xyz.pwrtelegram.ui.ActionBar.BaseFragment;
import xyz.pwrtelegram.ui.Components.HintEditText;
import xyz.pwrtelegram.ui.Components.LayoutHelper;
import xyz.pwrtelegram.ui.Components.SlideView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LoginActivity extends BaseFragment {

    private SlideView view;
    private ProgressDialog progressDialog;
    private Dialog permissionsDialog;
    private ArrayList<String> permissionsItems = new ArrayList<>();
    private boolean checkPermissions = true;
    private View doneButton;

    private final static int done_button = 1;

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (view != null) {
            view.onDestroyActivity();
        }
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            progressDialog = null;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == done_button) {
                    view.onNextPressed();
                } else if (id == -1) {
                    onBackPressed();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        fragmentView = new ScrollView(context);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        view = new TokenView(context);
        view.setVisibility(View.VISIBLE);
        frameLayout.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 26 : 18, 30, AndroidUtilities.isTablet() ? 26 : 18, 0));


        Bundle savedInstanceState = loadCurrentState();
        actionBar.setTitle(view.getHeaderName());
        if (savedInstanceState != null) {
            view.restoreStateParams(savedInstanceState);
        }
        view.setVisibility(View.VISIBLE);
        view.onShow();

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 6) {
            checkPermissions = false;
            view.onNextPressed();
        }
    }

    private Bundle loadCurrentState() {
        try {
            Bundle bundle = new Bundle();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
            Map<String, ?> params = preferences.getAll();
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String[] args = key.split("_\\|_");
                if (args.length == 1) {
                    if (value instanceof String) {
                        bundle.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        bundle.putInt(key, (Integer) value);
                    }
                } else if (args.length == 2) {
                    Bundle inner = bundle.getBundle(args[0]);
                    if (inner == null) {
                        inner = new Bundle();
                        bundle.putBundle(args[0], inner);
                    }
                    if (value instanceof String) {
                        inner.putString(args[1], (String) value);
                    } else if (value instanceof Integer) {
                        inner.putInt(args[1], (Integer) value);
                    }
                }
            }
            return bundle;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    private void clearCurrentState() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }

    private void putBundleToEditor(Bundle bundle, SharedPreferences.Editor editor, String prefix) {
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object obj = bundle.get(key);
            if (obj instanceof String) {
                if (prefix != null) {
                    editor.putString(prefix + "_|_" + key, (String) obj);
                } else {
                    editor.putString(key, (String) obj);
                }
            } else if (obj instanceof Integer) {
                if (prefix != null) {
                    editor.putInt(prefix + "_|_" + key, (Integer) obj);
                } else {
                    editor.putInt(key, (Integer) obj);
                }
            } else if (obj instanceof Bundle) {
                putBundleToEditor((Bundle) obj, editor, key);
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (Build.VERSION.SDK_INT >= 23 && dialog == permissionsDialog && !permissionsItems.isEmpty() && getParentActivity() != null) {
            getParentActivity().requestPermissions(permissionsItems.toArray(new String[permissionsItems.size()]), 6);
        }
    }

    @Override
    public boolean onBackPressed() {
        view.onDestroyActivity();
        clearCurrentState();
        return true;
    }

    private void needShowAlert(String title, String text) {
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }
    private void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new ProgressDialog(getParentActivity());
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    public void needHideProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        progressDialog = null;
    }

    @Override
    public void saveSelfArgs(Bundle outState) {
        try {
            Bundle bundle = new Bundle();
            SlideView v = view;
            if (v != null) {
                v.saveStateParams(bundle);
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            putBundleToEditor(bundle, editor, null);
            editor.commit();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void needFinishActivity() {
        clearCurrentState();
        presentFragment(new DialogsActivity(null), true);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
    }

    public class TokenView extends SlideView implements AdapterView.OnItemSelectedListener {

        private HintEditText tokenField;


        private boolean ignoreSelection = false;
        private boolean ignoreOnTokenChange = false;
        private boolean nextPressed = false;
        public TokenView(Context context) {
            super(context);

            setOrientation(VERTICAL);
            View view = new View(context);
            view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            view.setBackgroundColor(0xffdbdbdb);
            addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 4, -17.5f, 4, 0));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

            TextView textView = new TextView(context);
            textView.setText("");
            textView.setTextColor(0xff212121);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));


            tokenField = new HintEditText(context);
            tokenField.setInputType(InputType.TYPE_CLASS_TEXT);
            tokenField.setTextColor(0xff212121);
            tokenField.setHintTextColor(0xff979797);
            tokenField.setPadding(0, 0, 0, 0);
            AndroidUtilities.clearCursorDrawable(tokenField);
            tokenField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            tokenField.setMaxLines(1);
            tokenField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            tokenField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            linearLayout.addView(tokenField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
            tokenField.addTextChangedListener(new TextWatcher() {

                private int characterAction = -1;
                private int actionPosition;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (count == 0 && after == 1) {
                        characterAction = 1;
                    } else if (count == 1 && after == 0) {
                        if (s.charAt(start) == ' ' && start > 0) {
                            characterAction = 3;
                            actionPosition = start - 1;
                        } else {
                            characterAction = 2;
                        }
                    } else {
                        characterAction = -1;
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {

                    if (ignoreOnTokenChange) {
                        return;
                    }
                    int start = tokenField.getSelectionStart();
                    String tokenChars = "0123456789:abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJKLMNOPQRSTUVWXYZ_";
                    String str = tokenField.getText().toString();
                    if (characterAction == 3) {
                        str = str.substring(0, actionPosition) + str.substring(actionPosition + 1, str.length());
                        start--;
                    }
                    StringBuilder builder = new StringBuilder(str.length());
                    for (int a = 0; a < str.length(); a++) {
                        String ch = str.substring(a, a + 1);
                        if (tokenChars.contains(ch)) {
                            builder.append(ch);
                        }
                    }
                    ignoreOnTokenChange = true;
                    String hint = tokenField.getHintText();
                    if (hint != null) {
                        for (int a = 0; a < builder.length(); a++) {
                            if (a < hint.length()) {
                                if (hint.charAt(a) == ' ') {
                                    builder.insert(a, ' ');
                                    a++;
                                    if (start == a && characterAction != 2 && characterAction != 3) {
                                        start++;
                                    }
                                }
                            } else {
                                builder.insert(a, ' ');
                                if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                    start++;
                                }
                                break;
                            }
                        }
                    }
                    tokenField.setText(builder);
                    if (start >= 0) {
                        tokenField.setSelection(start <= tokenField.length() ? start : tokenField.length());
                    }
                    tokenField.onTextChange();
                    ignoreOnTokenChange = false;

                }
            });
            tokenField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        onNextPressed();
                        return true;
                    }
                    return false;
                }
            });

            textView = new TextView(context);
            textView.setText(LocaleController.getString("StartText", R.string.StartText));
            textView.setTextColor(0xff757575);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 28, 0, 10));

        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (ignoreSelection) {
                ignoreSelection = false;
                return;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onNextPressed() {
            if (getParentActivity() == null || nextPressed) {
                return;
            }
            nextPressed = true;

            ConnectionsManager.getInstance().cleanup();
            final TLRPC.TL_auth_importBotAuthorization req = new TLRPC.TL_auth_importBotAuthorization();
            req.api_hash = BuildVars.APP_HASH;
            req.api_id = BuildVars.APP_ID;
            req.bot_auth_token = tokenField.getText().toString();
            req.flags = 0;

            needShowProgress();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            nextPressed = false;
                            needHideProgress();
                            if (error == null) {
                                TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization) response;
                                ConnectionsManager.getInstance().setUserId(res.user.id);
                                UserConfig.clearConfig();
                                MessagesController.getInstance().cleanup();
                                UserConfig.setCurrentUser(res.user);
                                UserConfig.saveConfig(true);
                                MessagesStorage.getInstance().cleanup(true);
                                ArrayList<TLRPC.User> users = new ArrayList<>();
                                users.add(res.user);
                                MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                                MessagesController.getInstance().putUser(res.user, false);
                                ContactsController.getInstance().checkAppAccount();
                                MessagesController.getInstance().getBlockedUsers(true);
                                needFinishActivity();
                            } else {
                                if (error.text != null) {
                                    if (error.text.contains("ACCESS_TOKEN_INVALID")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidAccessToken", R.string.InvalidAccessToken));
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                                    } else if (error.code != -1000) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                                    }
                                }
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public void onShow() {
            super.onShow();
            if (tokenField != null) {
                AndroidUtilities.showKeyboard(tokenField);
                tokenField.requestFocus();
                tokenField.setSelection(tokenField.length());
            }
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("YourToken", R.string.YourToken);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String token = tokenField.getText().toString();
            if (token.length() != 0) {
                bundle.putString("tokenview_token", token);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            String token = bundle.getString("tokenview_token");
            if (token != null) {
                tokenField.setText(token);
            }
        }
    }

}
