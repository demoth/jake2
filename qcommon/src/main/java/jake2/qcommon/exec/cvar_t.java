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
 * One variable, optionally constrained by a definition. The registry coordinates all API writes.
 * TODO: Encapsulate legacy value fields after the scoped writer migration; direct writes bypass validation and leave typed caches stale.
 */
public final class cvar_t<T> {
    public String name;
    public String string;
    public String description;
    // allow changes, but for the next game
    // see jake2.server.SV_INIT.SV_InitGame()
    // todo rename to deferred_value
    public String latched_string;
    public int flags = 0;
    public boolean modified = false;
    public float value = 0.0f;

    private CvarSpec<T> definition;
    private T cachedValue, pendingValue;

    public @Nullable CvarSpec<T> getDefinition() {
        return definition;
    }

    void attachDefinition(CvarSpec<T> definition) {
        this.definition = definition;
    }

    // todo: will become simply 'value' once value name is freed from the legacy usages
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

    /** Registry-only commits keep the legacy token/mirror and native cache coherent. */
    @SuppressWarnings("unchecked")
    void commitActive(CvarValue resolved) {
        string = resolved.getToken();
        value = resolved.getLegacyFloat();
        cachedValue = (T) resolved.getValue();
    }

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

    // Compatibility readers for consumers still migrating to generic references.
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
