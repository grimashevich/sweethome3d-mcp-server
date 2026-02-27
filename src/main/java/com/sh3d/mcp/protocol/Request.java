package com.sh3d.mcp.protocol;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Value object: distributed JSON-request (action + params).
 * Tracks which parameter keys have been accessed by the handler,
 * allowing detection of unrecognized parameters after execution.
 */
public class Request {

    private final String action;
    private final Map<String, Object> params;
    private final Set<String> accessedKeys = new HashSet<>();
    private Map<String, Object> trackingParams;

    public Request(String action, Map<String, Object> params) {
        this.action = action;
        this.params = params != null ? params : Collections.emptyMap();
    }

    public String getAction() {
        return action;
    }

    /**
     * Returns an unmodifiable view of parameters that tracks key access.
     * Every {@code get()}, {@code containsKey()}, {@code entrySet()},
     * and {@code keySet()} call marks keys as accessed.
     */
    public Map<String, Object> getParams() {
        if (trackingParams == null) {
            trackingParams = new TrackingMap(params, accessedKeys);
        }
        return trackingParams;
    }

    /**
     * Returns string parameter or null if absent.
     */
    public String getString(String key) {
        accessedKeys.add(key);
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * Returns required string parameter. Throws IllegalArgumentException if absent.
     */
    public String getRequiredString(String key) {
        String val = getString(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return val;
    }

    /**
     * Returns required float parameter. Throws IllegalArgumentException if absent.
     */
    public float getFloat(String key) {
        accessedKeys.add(key);
        Object val = params.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        try {
            return Float.parseFloat(val.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid parameter '" + key + "': expected number, got '" + val + "'");
        }
    }

    /**
     * Returns Boolean parameter or null if absent.
     */
    public Boolean getBoolean(String key) {
        accessedKeys.add(key);
        Object val = params.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        throw new IllegalArgumentException(
                "Invalid parameter '" + key + "': expected boolean, got '" + val + "'");
    }

    /**
     * Returns float parameter or defaultValue if absent.
     */
    public float getFloat(String key, float defaultValue) {
        accessedKeys.add(key);
        Object val = params.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        try {
            return Float.parseFloat(val.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid parameter '" + key + "': expected number, got '" + val + "'");
        }
    }

    /**
     * Returns the set of parameter keys that were present in the request
     * but never accessed by any getter or via {@link #getParams()}.
     */
    public Set<String> getUnrecognizedKeys() {
        Set<String> unknown = new LinkedHashSet<>(params.keySet());
        unknown.removeAll(accessedKeys);
        return unknown;
    }

    /**
     * Explicitly marks a key as accessed. Useful when a handler reads
     * parameters via custom logic not covered by the standard getters.
     */
    public void markAccessed(String key) {
        accessedKeys.add(key);
    }

    /**
     * A Map wrapper that tracks key access for unrecognized-parameter detection.
     */
    private static class TrackingMap extends AbstractMap<String, Object> {
        private final Map<String, Object> delegate;
        private final Set<String> accessedKeys;

        TrackingMap(Map<String, Object> delegate, Set<String> accessedKeys) {
            this.delegate = delegate;
            this.accessedKeys = accessedKeys;
        }

        @Override
        public Object get(Object key) {
            if (key instanceof String) accessedKeys.add((String) key);
            return delegate.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            if (key instanceof String) accessedKeys.add((String) key);
            return delegate.containsKey(key);
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            accessedKeys.addAll(delegate.keySet());
            return Collections.unmodifiableSet(delegate.entrySet());
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public Set<String> keySet() {
            accessedKeys.addAll(delegate.keySet());
            return Collections.unmodifiableSet(delegate.keySet());
        }

        @Override
        public Object put(String key, Object value) {
            throw new UnsupportedOperationException("Request params are read-only");
        }
    }
}
