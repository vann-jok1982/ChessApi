package com.chessapi.model;

public enum Color {
    WHITE, BLACK;

    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    public static Color fromString(String color) {
        if (color == null) return null;
        return color.equalsIgnoreCase("WHITE") ? WHITE : BLACK;
    }
}