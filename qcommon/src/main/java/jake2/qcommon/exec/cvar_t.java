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

import static jake2.qcommon.Defines.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One variable with optional native typing, validation and write/latch behavior.
 * TODO: Retire public legacy value fields after callers/writers migrate; direct writes bypass validation and leave native state stale.
 */
public final class cvar_t<T> {
    public String name;
    // TODO: After legacy field users migrate, derive declared entries' rawString from the native value.
    // Raw text access stays; unconstrained entries still need text storage. Preserve 0/1 and enum-name tokens.
    public String string;
    public String description;
    // TODO: After legacy latch users migrate, derive rawPendingString from pendingValue;
    // retain canonical text conversion for console/config, without repeating validation.
    public String latched_string;
    public int flags = 0;
    public boolean modified = false;
    // TODO: Remove this Float mirror when legacy numeric readers migrate to typedValue.
    public float value = 0.0f;

    private final CvarType<T> type;
    private final T defaultValue;
    // Lasting state: the active native value and an optional queued replacement (existing CVAR_LATCH).
    private T cachedValue, pendingValue;

    /** Legacy explicit raw registration; typed registration uses the owning declaration constructor. */
    public cvar_t() {
        this.type = null;
        this.defaultValue = null;
    }

    cvar_t(String name, CvarType<T> type, T defaultValue, int flags, String description) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.flags = flags;
        this.description = description;
    }

    /** Reusable conversion rules, or null for an explicitly registered legacy raw entry. */
    public @Nullable CvarType<T> getType() { return type; }

    /** Owning default, independent of current active/pending state. */
    public @Nullable T getDefaultValue() { return defaultValue; }

    // TODO: Rename to value once the legacy Float field is removed.
    /** Returns the native value prepared by registration or the latest accepted write. */
    public @NotNull T getTypedValue() {
        if (cachedValue == null) throw wrongType("a typed value");
        return cachedValue;
    }

    /** Null means no queued change; false, zero and an empty String remain valid pending values. */
    public @Nullable T getPendingValue() {
        getTypedValue();
        return pendingValue;
    }

    public @NotNull String getRawString() {
        return string;
    }

    public @Nullable String getRawPendingString() {
        return latched_string;
    }

    /** Validates a native value, respecting write protection and latching. */
    public CvarSetResult set(T value) { return setNative(value, false); }

    /** Applies a native value immediately, bypassing protection and latching but not validation. */
    public CvarSetResult forceSet(T value) { return setNative(value, true); }

    boolean requiresAppRestart() { return name != null && name.startsWith("vid_"); }

    static boolean invalidInfoString(String value) {
        return value.indexOf('\\') >= 0 || value.indexOf(';') >= 0 || value.indexOf('"') >= 0;
    }

    private static CvarSetResult rejected(String diagnostic) {
        return new CvarSetResult(CvarSetResult.Status.REJECTED, diagnostic);
    }

    @SuppressWarnings("unchecked")
    CvarSetResult setNative(Object value, boolean force) {
        if (this.getType() == null) return rejected("Cvar " + this.name + " has no native type");
        if (value == null || nativeClass(value) != nativeClass(this.getDefaultValue()))
            return rejected("Wrong native value type for " + this.name);
        try {
            CvarType<Object> type = (CvarType<Object>) this.getType();
            return commitWrite(normalizeResolved(type.validate(value), this.flags), this.flags, force);
        } catch (IllegalArgumentException e) {
            return rejected("Invalid value for " + this.name + ": " + e.getMessage());
        }
    }

    static Class<?> nativeClass(Object value) {
        return value instanceof Enum<?> ? ((Enum<?>) value).getDeclaringClass() : value.getClass();
    }

    /** Prepares the canonical token and typed caches, checking type and transport limits before commit. */
    CvarValue normalize(String text, int flags) {
        if (text == null) throw new IllegalArgumentException("missing value");
        return normalizeResolved(this.getType().parse(text), flags);
    }

    /** Checks interchange limits for both native and parsed writes without revalidating their values. */
    CvarValue normalizeResolved(CvarType.Resolved<?> resolved, int flags) {
        String token = resolved.getToken();
        if ((flags & CVAR_ARCHIVE) != 0 && (token.indexOf('"') >= 0 || token.indexOf('\n') >= 0
                || token.indexOf('\r') >= 0 || token.indexOf('\0') >= 0
                || token.length() + this.name.length() + 7 >= Defines.MAX_STRING_CHARS))
            throw new IllegalArgumentException("value cannot round-trip through config command quoting");
        if ((flags & (CVAR_USERINFO | CVAR_SERVERINFO)) != 0 && (invalidInfoString(this.name) || invalidInfoString(token)))
            throw new IllegalArgumentException("invalid info string characters");
        return new CvarValue(token, resolved.getValue());
    }

    /**
     * Shares validation, latch handling and cache commits across writes to constrained entries.
     * Force bypasses protection and delay, but still requires valid values.
     */
    CvarSetResult setText(String text, int flags, boolean force) {
        CvarValue resolved;
        try {
            resolved = normalize(text, flags);
        }
        catch (IllegalArgumentException e) { return rejected("Invalid value for " + this.name + ": " + e.getMessage()); }
        return commitWrite(resolved, flags, force);
    }

    /** Applies an already validated value, sharing notifications and latch semantics across input forms. */
    private CvarSetResult commitWrite(CvarValue resolved, int flags, boolean force) {
        String token = resolved.getToken();
        if (!force && (this.flags & CVAR_NOSET) != 0) return rejected(this.name + " is write protected");
        if (!force && (flags & CVAR_LATCH) != 0 && (Globals.server_state != ServerStates.SS_DEAD || requiresAppRestart())) {
            if (token.equals(this.latched_string)) return new CvarSetResult(CvarSetResult.Status.UNCHANGED, null);
            if (token.equals(this.string)) {
                boolean cancelled = this.latched_string != null;
                this.commitPending(null);
                return new CvarSetResult(cancelled ? CvarSetResult.Status.APPLIED : CvarSetResult.Status.UNCHANGED, null);
            }
            this.commitPending(resolved);
            String timing = requiresAppRestart() ? "after restart" : "for next game";
            return new CvarSetResult(CvarSetResult.Status.QUEUED, this.name + " will be changed " + timing + ".");
        }
        boolean valueChanged = !token.equals(this.string);
        boolean flagsChanged = flags != this.flags;
        boolean hadPending = this.latched_string != null;
        int oldFlags = this.flags;
        this.commitActive(resolved);
        this.flags = flags;
        this.commitPending(null);
        if (valueChanged || flagsChanged) {
            this.modified = true;
            if (((oldFlags | flags) & CVAR_USERINFO) != 0) Globals.userinfo_modified = true;
        }
        if (valueChanged && this.name.equals("game")) {
            EngineFilesystemLifecycle.setGameDir(token);
            EngineFilesystemLifecycle.execAutoexec();
        }
        return new CvarSetResult(valueChanged || flagsChanged || hadPending ? CvarSetResult.Status.APPLIED : CvarSetResult.Status.UNCHANGED, null);
    }

    /** Updates active state without changing queued intent; keeps migration-era mirrors coherent. */
    @SuppressWarnings("unchecked")
    void commitActive(CvarValue resolved) {
        string = resolved.getToken();
        value = resolved.getLegacyFloat();
        cachedValue = (T) resolved.getValue();
    }

    /** Updates or clears queued intent without changing the active value; this split remains after migration. */
    @SuppressWarnings("unchecked")
    void commitPending(@Nullable CvarValue resolved) {
        latched_string = resolved == null ? null : resolved.getToken();
        pendingValue = resolved == null ? null : (T) resolved.getValue();
    }

    private IllegalStateException wrongType(String expected) {
        return new IllegalStateException("Cvar " + name + " does not declare " + expected);
    }

    private <V> V readAs(Class<V> expected) {
        if (!expected.isInstance(cachedValue)) throw wrongType(expected.getSimpleName());
        return expected.cast(cachedValue);
    }

    // TODO: Remove these compatibility readers (including asEnum) once callers use generic references.
    public boolean asBoolean() { return readAs(Boolean.class); }
    public int asInt() { return readAs(Integer.class); }
    public float asFloat() { return readAs(Float.class); }
    public @NotNull String asString() { return readAs(String.class); }

    public <E extends Enum<E>> E asEnum(Class<E> enumClass) {
        if (!(cachedValue instanceof Enum<?>) || ((Enum<?>) cachedValue).getDeclaringClass() != enumClass)
            throw wrongType(enumClass.getSimpleName());
        return enumClass.cast(cachedValue);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("cvar_t{");
        if (name != null)
            sb.append("name='").append(name).append('\'');
        if (string != null)
            sb.append(", string='").append(string).append('\'');
        if (description != null)
            sb.append(", description='").append(description).append('\'');
        if (latched_string != null)
        	sb.append(", latched_string='").append(latched_string).append('\'');
        if (flags != 0)
        	sb.append(", flags=").append(flags);
        if (modified)
        	sb.append(", modified=").append(modified);
        if (value != 0f)
        	sb.append(", value=").append(value);
        sb.append('}');
        return sb.toString();
    }
}
