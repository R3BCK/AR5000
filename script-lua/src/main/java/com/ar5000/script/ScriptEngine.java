package com.ar5000.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ScriptEngine {
    private final Globals globals;
    private final RadioApi api;
    private volatile boolean running = false;

    public ScriptEngine(RadioApi a) {
        globals = JsePlatform.standardGlobals();
        api = a;
        register();
    }

    private void register() {
        globals.set("radio", org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(api));
    }

    public boolean run(InputStream is) {
        try {
            running = true;
            // FIX: LuaJ требует Reader, оборачиваем InputStream
            globals.load(new InputStreamReader(is, "UTF-8"), "script.lua").call();
            return true;
        } catch (Exception e) {
            android.util.Log.e("Lua", "Script execution failed", e);
            return false;
        } finally {
            running = false;
        }
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}