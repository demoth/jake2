/*
 * Cvar.java
 * Copyright (C) 2003
 * 
 * $Id: Cvar.java,v 1.10 2007-05-14 22:29:30 cawe Exp $
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

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jake2.qcommon.util.Lib;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static jake2.qcommon.Defines.*;
import static jake2.qcommon.exec.cvar_t.nativeClass;

/**
 * Cvar implements console variables. The original code is located in cvar.c
 */
public class Cvar {

    private static final Cvar instance = new Cvar();
    // Symbols \ ; " are not allowed in the info strings
    private static final Pattern INFO_DISALLOWED_CHARS = Pattern.compile("[\\\\;\"]");


    public static Cvar getInstance() {
        return instance;
    }

    // should be thread safe implementation if we are talking multithreading
    private final Map<String, cvar_t<?>> cvarMap;
    private final Map<String, String> cvarAliases;
    private final Map<String, EarlyCvarValue> earlyValues = new LinkedHashMap<>();
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public Cvar() {
        this.cvarMap = new HashMap<>();
        this.cvarAliases = new HashMap<>();
    }

    public static void Init() {
        Cmd.AddCommand("set", (List<String> args) -> {
            int flags;
            if (args.size() != 3 && args.size() != 4) {
                Com.Printf("usage: set <variable> <value> [u / s]\n");
                return;
            }

            if (args.size() == 4) {
                if (args.get(3).equals("u"))
                    flags = CVAR_USERINFO;
                else if (args.get(3).equals("s"))
                    flags = CVAR_SERVERINFO;
                else {
                    Com.Printf("flags can only be 'u' or 's'\n");
                    return;
                }
                report(Cvar.getInstance().setWithSuggestedFlags(args.get(1), args.get(2), flags));
            } else
                report(Cvar.getInstance().setResult(args.get(1), args.get(2)));

        });
        Cmd.AddCommand("cvarlist", (List<String> args) -> {
            for (cvar_t<?> var : Cvar.getInstance().cvarMap.values()) {
                if ((var.flags & CVAR_ARCHIVE) != 0)
                    Com.Printf("*");
                else
                    Com.Printf(" ");
                if ((var.flags & CVAR_USERINFO) != 0)
                    Com.Printf("U");
                else
                    Com.Printf(" ");
                if ((var.flags & CVAR_SERVERINFO) != 0)
                    Com.Printf("S");
                else
                    Com.Printf(" ");
                if ((var.flags & CVAR_NOSET) != 0)
                    Com.Printf("-");
                else if ((var.flags & CVAR_LATCH) != 0)
                    Com.Printf("L");
                else
                    Com.Printf(" ");
                Com.Printf(" " + var.name + " \"" + var.getRawString() + "\"\n");
            }
            Com.Printf(Cvar.getInstance().cvarMap.size() + " cvars\n");
        });
    }

    private cvar_t<?> lookup(String name) {
        return cvarMap.get(resolveCanonicalName(name));
    }

    /** Quiet namespace probe for command registration and scoped registry ownership checks. */
    public boolean isRegistered(String name) {
        return lookup(name) != null;
    }

    /** Moves an unregistered input to its owning registry, retaining suggested flags for declaration validation. */
    public void transferEarlyValue(String name, Cvar destination) {
        if (destination == this || destination.isRegistered(name)) return;
        EarlyCvarValue input = earlyValues.remove(resolveCanonicalName(name));
        if (input != null) {
            // Explicit input already staged in the owning registry takes precedence over process startup input.
            destination.earlyValues.putIfAbsent(destination.resolveCanonicalName(name), input);
        }
    }

    /** Seeds a new game registry with independent inputs; its declarations still own types and flags.
     * Queued changes take effect in the new instance, without mutating the source instance.
     */
    public void copyValuesTo(Cvar destination) {
        if (destination == this) return;
        for (cvar_t<?> var : cvarMap.values()) {
            if (!destination.isRegistered(var.name))
                destination.earlyValues.putIfAbsent(destination.resolveCanonicalName(var.name),
                        new EarlyCvarValue(var.getRawPendingString() != null ? var.getRawPendingString() : var.getRawString(), null));
        }
        earlyValues.forEach((name, input) -> {
            if (!destination.isRegistered(name))
                destination.earlyValues.putIfAbsent(destination.resolveCanonicalName(name), input);
        });
    }

    /** Unknown-type registered lookup. Early inputs are not entries; missing names warn and return null. */
    public @Nullable cvar_t<?> find(String name) {
        cvar_t<?> var = lookup(name);
        if (var == null) Com.Warn("Cvar '" + name + "' is not registered\n");
        return var;
    }

    /** Console text access includes staged startup input without creating an entry. */
    public String getRawString(String name) {
        cvar_t<?> var = lookup(name);
        if (var != null) return var.getRawString();
        EarlyCvarValue early = earlyValues.get(resolveCanonicalName(name));
        if (early != null) {
            Com.Warn("Reading early value for '" + name + "' before cvar registration\n");
            return early.getText();
        }
        Com.Warn("Cvar '" + name + "' is not registered\n");
        return "";
    }

    /** Stages input until an owner supplies its type/default/flags; latest text wins. */
    private CvarSetResult stageEarly(String name, String text, Integer suggestedFlags) {
        if (text == null) return rejected("Missing value for " + name);
        EarlyCvarValue previous = earlyValues.remove(name);
        earlyValues.put(name, new EarlyCvarValue(text, suggestedFlags != null ? suggestedFlags
                : previous == null ? null : previous.getSuggestedFlags()));
        Com.Warn("Cvar '" + name + "' is not registered; storing early input\n");
        return new CvarSetResult(CvarSetResult.Status.QUEUED, "Awaiting registration of " + name);
    }

    /** Console flag suffixes check the owning declaration; they never grant flags or force writes. */
    private CvarSetResult setWithSuggestedFlags(String name, String text, int suggestedFlags) {
        cvar_t<?> var = lookup(name);
        if (var == null) return stageEarly(resolveCanonicalName(name), text, suggestedFlags);
        if ((var.flags & suggestedFlags) != suggestedFlags)
            return rejected("Suggested flags conflict with owning declaration for " + name);
        return var.setText(text);
    }

    /** Owner flags must include suggested bits; stored input cannot grant flags to an entry. */
    private static void validateSuggestedFlags(EarlyCvarValue early, int flags) {
        if (early.getSuggestedFlags() != null && (early.getSuggestedFlags() & flags) != early.getSuggestedFlags())
            throw new IllegalArgumentException("suggested flags conflict with owning declaration");
    }

    public void AddAlias(String alias, String canonicalName) {
        if (alias == null || canonicalName == null) {
            return;
        }

        String trimmedAlias = alias.trim();
        String trimmedCanonicalName = canonicalName.trim();
        if (trimmedAlias.isEmpty() || trimmedCanonicalName.isEmpty()) {
            return;
        }

        String resolvedCanonicalName = resolveCanonicalName(trimmedCanonicalName);
        if (trimmedAlias.equals(resolvedCanonicalName)) {
            return;
        }

        cvar_t<?> existing = cvarMap.get(trimmedAlias);
        if (existing != null && !existing.name.equals(resolvedCanonicalName)) {
            throw new IllegalArgumentException("Cannot alias existing cvar '" + trimmedAlias + "'");
        }

        boolean usedEarly = earlyValues.containsKey(trimmedAlias);
        cvarAliases.put(trimmedAlias, resolvedCanonicalName);
        if (usedEarly) {
            Com.Warn("Alias '" + trimmedAlias + "' was used before alias registration\n");
            Map<String, EarlyCvarValue> routed = new LinkedHashMap<>();
            for (Map.Entry<String, EarlyCvarValue> entry : earlyValues.entrySet()) {
                String name = resolveCanonicalName(entry.getKey());
                EarlyCvarValue previous = routed.remove(name);
                EarlyCvarValue input = entry.getValue();
                routed.put(name, new EarlyCvarValue(input.getText(), input.getSuggestedFlags() != null
                        ? input.getSuggestedFlags() : previous == null ? null : previous.getSuggestedFlags()));
            }
            earlyValues.clear();
            earlyValues.putAll(routed);
            cvar_t<?> target = lookup(resolvedCanonicalName);
            if (target != null) {
                EarlyCvarValue input = earlyValues.remove(resolvedCanonicalName);
                try {
                    validateSuggestedFlags(input, target.flags);
                    report(setResult(resolvedCanonicalName, input.getText()));
                } catch (IllegalArgumentException e) {
                    Com.Warn("Rejected early alias input for '" + trimmedAlias + "': " + e.getMessage() + "\n");
                }
            }
        }
    }

    /**
     * Handles variable inspection and changing from the console.
     */
    boolean printOrSet(List<String> args) {
        if (args.isEmpty())
            return false;

        // check variables
        cvar_t<?> v = lookup(args.get(0));
        if (v == null)
            return false;

        // perform a variable print or set
        if (args.size() == 1) {
            Com.Printf("\"" + v.name + "\" is \"" + v.getRawString() + "\"\n");
            return true;
        }

        report(v.setText(args.get(1)));
        return true;
    }

    /**
     * Returns a string of \key\value pairs for all cvars with the given flags.
     */
    private String BitInfo(int flags) {

        String keyValuePairs = cvarMap.values().stream()
                .filter(cv -> (cv.flags & flags) != 0)
                .filter(cv -> (!INFO_DISALLOWED_CHARS.matcher(cv.name).find()))
                .filter(cv -> (!INFO_DISALLOWED_CHARS.matcher(cv.getRawString()).find()))
                .filter(cv -> !cv.getRawString().isBlank())
                .map(cv -> "\\" + cv.name + "\\" + cv.getRawString())
                .collect(Collectors.joining());

        if (keyValuePairs.length() >= Defines.MAX_INFO_STRING) {
            // fixme: in vanilla q2 in such case the result was not discarded but rather trimmed to fit into MAX_INFO_STRING
            return "";
        } else {
            return keyValuePairs;
        }
    }

    /**
     * Returns an info string containing all the CVAR_SERVERINFO cvars. 
     */
    public String Serverinfo() {
        return BitInfo(Defines.CVAR_SERVERINFO);
    }

    public void eachCvarByFlags(int flags, Consumer<cvar_t<?>> consumer) {
        for (cvar_t<?> var : cvarMap.values()) {
            if (0 != (var.flags & flags)) {
                consumer.accept(var);
            }
        }
    }
    
    /**
     * Any variables with latched values will be updated.
     */
    public void updateLatchedVars() {
        for (cvar_t<?> var : cvarMap.values()) {
            if (var.getPendingValue() != null) report(var.applyPending());
        }
    }

    /**
     * Returns an info string containing all the CVAR_USERINFO cvars.
     */
    public String Userinfo() {
        return BitInfo(CVAR_USERINFO);
    }
    
    /**
     * Appends lines containing \"set variable value\" for all variables
     * with the archive flag set true. 
     */

    public void writeArchiveVariables(String path) {
        RandomAccessFile f;
        String buffer;

        f = Lib.fopen(path, "rw");
        if (f == null)
            return;

        try {
            f.seek(f.length());
        } catch (IOException e1) {
            Lib.fclose(f);
            return;
        }
        for (cvar_t<?> var : cvarMap.values()) {
            if ((var.flags & CVAR_ARCHIVE) != 0) {
                String persistedValue = var.getRawPendingString() != null ? var.getRawPendingString() : var.getRawString();
                buffer = "set " + var.name + " \"" + persistedValue + "\"\n";
                try {
                    f.write(buffer.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (IOException e) {
                    Com.Printf("Could not write cvar " + var + " to " + path);
                }
            }
        }
        Lib.fclose(f);
    }

    /**
     * Variable typing auto completition.
     */
    public List<String> CompleteVariable(String prefix) {

        List<String> vars = new ArrayList<>();

        for (String cvarName : cvarMap.keySet()) {
            if (cvarName.startsWith(prefix))
                vars.add(cvarName);
        }
        for (String alias : cvarAliases.keySet()) {
            if (alias.startsWith(prefix))
                vars.add(alias);
        }

        return vars;
    }

    public List<cvar_t<?>> listByPrefixAndFlags(String prefix, int flags) {
        String normalizedPrefix = prefix == null ? "" : prefix;
        return cvarMap.values().stream()
                .filter(var -> normalizedPrefix.isEmpty() || var.name.startsWith(normalizedPrefix))
                .filter(var -> (var.flags & flags) == flags)
                .sorted(Comparator.comparing(var -> var.name))
                .collect(Collectors.toList());
    }

    private String resolveCanonicalName(String var_name) {
        if (var_name == null) {
            return null;
        }

        String resolved = var_name;
        while (true) {
            String canonical = cvarAliases.get(resolved);
            if (canonical == null || canonical.equals(resolved)) {
                return resolved;
            }
            resolved = canonical;
        }
    }

    /** Acquires an existing entry and checks its declared native type. */
    @SuppressWarnings("unchecked")
    public <T> @NotNull cvar_t<T> find(String name, Class<T> expectedType) {
        cvar_t<?> var = find(name);
        if (var == null) throw new IllegalArgumentException("Unknown cvar: " + name);
        Object defaultValue = var.getDefaultValue();
        Class<?> declaredType = nativeClass(defaultValue);
        Class<?> expected = expectedType == boolean.class ? Boolean.class
                : expectedType == int.class ? Integer.class
                : expectedType == float.class ? Float.class : expectedType;
        if (declaredType != expected)
            throw new IllegalArgumentException("Cvar " + name + " declares " + declaredType.getSimpleName()
                    + ", requested " + expected.getSimpleName());
        return (cvar_t<T>) var;
    }

    /** Creates the owning declaration, applying any early input before publishing the entry. */
    @SuppressWarnings("unchecked")
    public <T> @NotNull cvar_t<T> register(String name, CvarType<T> type, T defaultValue, int flags, String description) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("invalid cvar name");
        if (!name.equals(resolveCanonicalName(name)))
            throw new IllegalArgumentException("Define the canonical cvar name, not an alias: " + name);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        cvar_t<?> previous = lookup(name);
        if (previous != null) {
            if (!Objects.equals(previous.getType(), type) || !Objects.equals(previous.getDefaultValue(), defaultValue)
                    || previous.flags != flags || !Objects.equals(previous.description, description))
                throw new IllegalArgumentException("Conflicting cvar declaration: " + name);
            return (cvar_t<T>) previous;
        }
        cvar_t<T> var = new cvar_t<>(name, type, defaultValue, flags, description);
        CvarType.Resolved<T> active = var.normalizeResolved(type.validate(defaultValue));
        EarlyCvarValue early = earlyValues.remove(name);
        if (early != null) {
            try {
                validateSuggestedFlags(early, flags);
                active = var.normalize(early.getText());
            } catch (IllegalArgumentException e) {
                Com.Warn("Rejected early input for '" + name + "'; using default: " + e.getMessage() + "\n");
            }
        }
        var.commitActive(active);
        var.modified = true;
        cvarMap.put(name, var);
        if ((flags & CVAR_USERINFO) != 0) Globals.userinfo_modified = true;
        return var;
    }

    /** Registers an owning declaration without a description. */
    public <T> @NotNull cvar_t<T> register(String name, CvarType<T> type, T defaultValue, int flags) {
        return register(name, type, defaultValue, flags, null);
    }

    /** Registers an owning declaration with no flags or description. */
    public <T> @NotNull cvar_t<T> register(String name, CvarType<T> type, T defaultValue) {
        return register(name, type, defaultValue, 0, null);
    }

    /** Lets callers distinguish acceptance, rejection and delayed application without console parsing. */
    public CvarSetResult setResult(String name, String value) { return setResult(name, value, false); }
    /** Runtime-checked native writes for heterogeneous editors whose entries have unknown generic types. */
    public CvarSetResult setNativeResult(String name, Object value) {
        cvar_t<?> var = find(name);
        if (var == null) return rejected("Unknown cvar: " + name);
        return var.setNative(value, false);
    }

    /** Supports immediate lifecycle updates while retaining validation for constrained entries. */
    public CvarSetResult forceSetResult(String name, String value) { return setResult(name, value, true); }

    /** Resolves text input to its owner or stages it until declaration. */
    private CvarSetResult setResult(String name, String value, boolean force) {
        cvar_t<?> var = lookup(name);
        if (var == null) return stageEarly(resolveCanonicalName(name), value, null);
        return var.setText(value, force);
    }

    /** Keeps rejection feedback consistent across write paths. */
    private static CvarSetResult rejected(String diagnostic) {
        return new CvarSetResult(CvarSetResult.Status.REJECTED, diagnostic);
    }

    /** Prints command feedback from validation and latch results. */
    private static void report(CvarSetResult result) {
        if (result.getDiagnostic() != null) Com.Printf(result.getDiagnostic() + "\n");
    }

    /** Clears entries and aliases for tests; discard acquired entries and their consumers before reuse. */
    public void clear() {
        cvarMap.clear();
        cvarAliases.clear();
        earlyValues.clear();
    }
}
