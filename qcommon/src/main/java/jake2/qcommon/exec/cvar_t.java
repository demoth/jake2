/*
 * cvar_t.java
 * Copyright (C) 2003
 *
 * $Id: cvar_t.java,v 1.2 2004-07-08 15:58:44 hzi Exp $
 */
/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package jake2.qcommon.exec;

import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.ServerStates;
import jake2.qcommon.vfs.EngineFilesystemLifecycle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jake2.qcommon.Defines.*;

/** One typed variable with immutable declaration metadata and validated active/pending values. */
public final class cvar_t<T> {
    public final String name;
    public final @Nullable String description;
    public final int flags;
    public boolean modified;

    private final CvarType<T> type;
    private final T defaultValue;
    private CvarType.Resolved<T> active, pending;

    cvar_t(String name, CvarType<T> type, T defaultValue, int flags, String description) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.flags = flags;
        this.description = description;
    }

    public @NotNull CvarType<T> getType() { return type; }
    public @NotNull T getDefaultValue() { return defaultValue; }
    public @NotNull T getTypedValue() { return active.getValue(); }

    /** Null means no queued change; false, zero and an empty String remain valid pending values. */
    public @Nullable T getPendingValue() { return pending == null ? null : pending.getValue(); }
    public @NotNull String getRawString() { return active.getToken(); }
    public @Nullable String getRawPendingString() { return pending == null ? null : pending.getToken(); }

    /** Validates a native value, respecting write protection and latching. */
    public CvarSetResult set(T value) { return setNative(value, false); }

    /** Applies a native value immediately, bypassing protection and latching but not validation. */
    public CvarSetResult forceSet(T value) { return setNative(value, true); }

    /** Parses external text and applies the same validation, protection and latch rules as native edits. */
    public CvarSetResult setText(String text) { return setText(text, false); }

    boolean requiresAppRestart() { return name.startsWith("vid_"); }

    static boolean invalidInfoString(String value) {
        return value.indexOf('\\') >= 0 || value.indexOf(';') >= 0 || value.indexOf('"') >= 0;
    }

    private static CvarSetResult rejected(String diagnostic) {
        return new CvarSetResult(CvarSetResult.Status.REJECTED, diagnostic);
    }

    @SuppressWarnings("unchecked")
    CvarSetResult setNative(Object value, boolean force) {
        if (value == null || nativeClass(value) != nativeClass(defaultValue))
            return rejected("Wrong native value type for " + name);
        CvarType.Resolved<?> resolved;
        try {
            resolved = normalizeResolved(((CvarType<Object>) type).validate(value));
        } catch (IllegalArgumentException e) {
            return rejected("Invalid value for " + name + ": " + e.getMessage());
        }
        return commitWrite(resolved, force);
    }

    static Class<?> nativeClass(Object value) {
        return value instanceof Enum<?> ? ((Enum<?>) value).getDeclaringClass() : value.getClass();
    }

    CvarType.Resolved<T> normalize(String text) {
        if (text == null) throw new IllegalArgumentException("missing value");
        return normalizeResolved(type.parse(text));
    }

    /** Checks interchange limits without revalidating an already converted value. */
    <V> CvarType.Resolved<V> normalizeResolved(CvarType.Resolved<V> resolved) {
        String token = resolved.getToken();
        if ((flags & CVAR_ARCHIVE) != 0 && (token.indexOf('"') >= 0 || token.indexOf('\n') >= 0
                || token.indexOf('\r') >= 0 || token.indexOf('\0') >= 0
                || token.length() + name.length() + 7 >= Defines.MAX_STRING_CHARS))
            throw new IllegalArgumentException("value cannot round-trip through config command quoting");
        if ((flags & (CVAR_USERINFO | CVAR_SERVERINFO)) != 0 && (invalidInfoString(name) || invalidInfoString(token)))
            throw new IllegalArgumentException("invalid info string characters");
        return resolved;
    }

    CvarSetResult setText(String text, boolean force) {
        CvarType.Resolved<T> resolved;
        try {
            resolved = normalize(text);
        } catch (IllegalArgumentException e) {
            return rejected("Invalid value for " + name + ": " + e.getMessage());
        }
        return commitWrite(resolved, force);
    }

    private CvarSetResult commitWrite(CvarType.Resolved<?> resolved, boolean force) {
        String token = resolved.getToken();
        if (!force && (flags & CVAR_NOSET) != 0) return rejected(name + " is write protected");
        if (!force && (flags & CVAR_LATCH) != 0 && (Globals.server_state != ServerStates.SS_DEAD || requiresAppRestart())) {
            if (token.equals(getRawPendingString())) return new CvarSetResult(CvarSetResult.Status.UNCHANGED, null);
            if (token.equals(getRawString())) {
                boolean cancelled = pending != null;
                commitPending(null);
                return new CvarSetResult(cancelled ? CvarSetResult.Status.APPLIED : CvarSetResult.Status.UNCHANGED, null);
            }
            commitPending(resolved);
            String timing = requiresAppRestart() ? "after restart" : "for next game";
            return new CvarSetResult(CvarSetResult.Status.QUEUED, name + " will be changed " + timing + ".");
        }
        boolean valueChanged = !token.equals(getRawString());
        boolean hadPending = pending != null;
        commitActive(resolved);
        commitPending(null);
        if (valueChanged) {
            modified = true;
            if ((flags & CVAR_USERINFO) != 0) Globals.userinfo_modified = true;
        }
        if (valueChanged && name.equals("game")) {
            EngineFilesystemLifecycle.onGameCvarChanged(token);
        }
        return new CvarSetResult(valueChanged || hadPending ? CvarSetResult.Status.APPLIED : CvarSetResult.Status.UNCHANGED, null);
    }

    /** Pending state was validated when queued and declaration metadata cannot change. */
    CvarSetResult applyPending() {
        return pending == null ? new CvarSetResult(CvarSetResult.Status.UNCHANGED, null) : commitWrite(pending, true);
    }

    @SuppressWarnings("unchecked")
    void commitActive(CvarType.Resolved<?> resolved) { active = (CvarType.Resolved<T>) resolved; }

    @SuppressWarnings("unchecked")
    private void commitPending(@Nullable CvarType.Resolved<?> resolved) { pending = (CvarType.Resolved<T>) resolved; }

    @Override
    public String toString() {
        return "cvar_t{name='" + name + "', value=" + getTypedValue()
                + (pending == null ? "" : ", pending=" + pending.getValue()) + ", flags=" + flags + "}";
    }
}
