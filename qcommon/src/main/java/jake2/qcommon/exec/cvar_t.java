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
public final class cvar_t {
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

    private CvarSpec<?> definition;
    // cached values
    private Boolean cachedBoolean, pendingBoolean;
    private Integer cachedInt, pendingInt;
    private Float cachedFloat, pendingFloat;
    private String cachedString, pendingString;
    private Enum<?> cachedEnum, pendingEnum;

    public @Nullable CvarSpec<?> getDefinition() {
        return definition;
    }

    void attachDefinition(CvarSpec<?> definition) {
        this.definition = definition;
    }

    public @NotNull String getRawString() {
        return string;
    }

    public @Nullable String getRawPendingString() {
        return latched_string;
    }

    /** Registry-only commits keep the legacy token/mirror and nullable caches coherent. */
    void commitActive(CvarValue resolved) {
        string = resolved.getToken();
        value = resolved.getLegacyFloat();
        cachedBoolean = resolved.getBooleanValue();
        cachedInt = resolved.getIntValue();
        cachedFloat = resolved.getFloatValue();
        cachedString = resolved.getStringValue();
        cachedEnum = resolved.getEnumValue();
    }

    void commitPending(@Nullable CvarValue resolved) {
        latched_string = resolved == null ? null : resolved.getToken();
        pendingBoolean = resolved == null ? null : resolved.getBooleanValue();
        pendingInt = resolved == null ? null : resolved.getIntValue();
        pendingFloat = resolved == null ? null : resolved.getFloatValue();
        pendingString = resolved == null ? null : resolved.getStringValue();
        pendingEnum = resolved == null ? null : resolved.getEnumValue();
    }

    private IllegalStateException wrongType(String expected) {
        return new IllegalStateException("Cvar " + name + " does not declare " + expected);
    }

    public boolean asBoolean() {
        if (cachedBoolean == null) throw wrongType("Boolean");
        return cachedBoolean;
    }

    public int asInt() {
        if (cachedInt == null) throw wrongType("Integer");
        return cachedInt;
    }

    public float asFloat() {
        if (cachedFloat == null) throw wrongType("Float");
        return cachedFloat;
    }

    public @NotNull String asString() {
        if (cachedString == null) throw wrongType("String");
        return cachedString;
    }

    public <E extends Enum<E>> E asEnum(Class<E> enumClass) {
        if (cachedEnum == null || cachedEnum.getDeclaringClass() != enumClass)
            throw wrongType(enumClass.getSimpleName());
        return enumClass.cast(cachedEnum);
    }

    // Check the active type: a null pending slot means no change, rather than a wrong-type read.
    public @Nullable Boolean getPendingBooleanValue() { asBoolean(); return pendingBoolean; }
    public @Nullable Integer getPendingIntValue() { asInt(); return pendingInt; }
    public @Nullable Float getPendingFloatValue() { asFloat(); return pendingFloat; }
    public @Nullable String getPendingStringValue() { asString(); return pendingString; }
    public <E extends Enum<E>> @Nullable E getPendingEnumValue(Class<E> enumClass) {
        asEnum(enumClass);
        return pendingEnum == null ? null : enumClass.cast(pendingEnum);
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
