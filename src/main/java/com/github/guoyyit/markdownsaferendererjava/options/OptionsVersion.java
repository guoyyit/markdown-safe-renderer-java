package com.github.guoyyit.markdownsaferendererjava.options;

/**
 * Versioned options schema. New options must default to values that preserve
 * previous output, so rendering with an older version keeps its behaviour.
 */
public enum OptionsVersion {
    V1(1);

    private final int number;

    OptionsVersion(int number) { this.number = number; }

    public int getNumber() { return number; }

    public static OptionsVersion latest() { return V1; }

    public static OptionsVersion fromNumber(int number) {
        for (OptionsVersion v : values()) {
            if (v.number == number) return v;
        }
        throw new IllegalArgumentException("Unsupported options version: " + number);
    }
}
