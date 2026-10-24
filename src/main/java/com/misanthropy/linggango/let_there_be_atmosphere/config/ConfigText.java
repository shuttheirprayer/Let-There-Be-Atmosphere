package com.misanthropy.linggango.let_there_be_atmosphere.config;

public final class ConfigText {
    private final StringBuilder out = new StringBuilder();

    public ConfigText header(String... lines) {
        for (String line : lines) out.append("# ").append(line).append('\n');
        return this;
    }

    public ConfigText section(String title) {
        out.append('\n').append("# ").append(title).append('\n');
        return this;
    }

    public ConfigText note(String... lines) {
        out.append('\n');
        for (String line : lines) out.append("# ").append(line).append('\n');
        return this;
    }

    public ConfigText put(String key, Object value) {
        out.append(key).append('=').append(value).append('\n');
        return this;
    }

    public ConfigText putHex(String key, int rgb) {
        return put(key, String.format("%06X", rgb & 0xFFFFFF));
    }

    @Override
    public String toString() { return out.toString(); }
}
