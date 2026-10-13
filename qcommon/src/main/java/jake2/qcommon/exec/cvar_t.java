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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One registered variable with optional native typing. The registry coordinates all API writes.
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
