package org.qpython.qsl4a.qsl4a.facade;

import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import org.qpython.qsl4a.qsl4a.jsonrpc.RpcReceiver;
import org.qpython.qsl4a.qsl4a.rpc.Rpc;
import org.qpython.qsl4a.qsl4a.rpc.RpcOptional;
import org.qpython.qsl4a.qsl4a.rpc.RpcParameter;

import java.io.IOException;
import java.util.Map;

/**
 * This facade allows access to the Preferences interface.
 * 
 * <br>
 * <b>Notes:</b> <br>
 * <b>filename</b> - Filename indicates which preference file to refer to. If no filename is
 * supplied (the default) then the SharedPreferences uses is the default for the SL4A application.<br>
 * <b>prefPutValue</b> - uses "MODE_PRIVATE" when writing to preferences. Save values to the default
 * shared preferences is explicitly disallowed.<br>
 * <br>
 * See <a
 * href=http://developer.android.com/reference/java/util/prefs/Preferences.html>Preferences</a> and
 * <a href=http://developer.android.com/guide/topics/data/data-storage.html#pref>Shared
 * Preferences</a> in the android documentation on how preferences work.
 * 
 * @author Robbie Matthews (rjmatthews62@gmail.com)
 */

public class PreferencesFacade extends RpcReceiver {

  private Service mService;
  private final AndroidFacade mAndroidFacade;
  private final Context context;

  public PreferencesFacade(FacadeManager manager) {
    super(manager);
    mAndroidFacade = manager.getReceiver(AndroidFacade.class);
    context = mAndroidFacade.context;
    mService = manager.getService();

  }

  @Rpc(description = "Read a value from shared preferences")
  public Object prefGetValue(
      @RpcParameter(name = "key") String key,
      @RpcParameter(name = "filename", description = "Desired preferences file. If not defined, uses the default Shared Preferences.") @RpcOptional String filename) {
    SharedPreferences p = getPref(filename);
    return p.getAll().get(key);
  }

  @Rpc(description = "Write a value to shared preferences")
  public void prefPutValue(
      @RpcParameter(name = "key") String key,
      @RpcParameter(name = "value") Object value,
      @RpcParameter(name = "filename", description = "Desired preferences file. If not defined, uses the default Shared Preferences.") @RpcOptional String filename) {
    SharedPreferences p;
    if (filename == null || filename.equals("")) {
      //throw new IOException("Can't write to default preferences.");
      p = PreferenceManager.getDefaultSharedPreferences(context);
    } else p = getPref(filename);
    Editor e = p.edit();
    if (value instanceof Boolean) {
      e.putBoolean(key, (Boolean) value);
    } else if (value instanceof Long) {
      e.putLong(key, (Long) value);
    } else if (value instanceof Integer) {
      e.putLong(key, (Integer) value);
    } else if (value instanceof Float) {
      e.putFloat(key, (Float) value);
    } else if (value instanceof Double) { // TODO: Not sure if this is a good idea
      e.putFloat(key, ((Double) value).floatValue());
    } else {
      e.putString(key, value.toString());
    }
    e.apply();
  }

  @Rpc(description = "Get list of Shared Preference Values", returns = "Map of key,value")
  public Map<String, ?> prefGetAll(
      @RpcParameter(name = "filename", description = "Desired preferences file. If not defined, uses the default Shared Preferences.") @RpcOptional String filename) {
    return getPref(filename).getAll();
  }

  private SharedPreferences getPref(String filename) {
    if (filename == null || filename.equals("")) {
      return PreferenceManager.getDefaultSharedPreferences(mService);
    }
    return mService.getSharedPreferences(filename, 0);

  }

  @Override
  public void shutdown() {

  }
}
