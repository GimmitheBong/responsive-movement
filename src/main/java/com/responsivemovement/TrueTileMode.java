package com.responsivemovement;

/** Distinguishes server authority from the native client's between-tick interpolation. */
public enum TrueTileMode
{
    SERVER_TRUE_TILE("Server true tile"),
    NATIVE_MOVEMENT_TILES("Native movement tiles");

    private final String label;

    TrueTileMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
