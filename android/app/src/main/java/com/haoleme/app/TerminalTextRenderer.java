package com.haoleme.app;

import java.util.ArrayList;
import java.util.List;

final class TerminalTextRenderer {
    private static final int MAX_COLUMNS = 512;
    private static final int MAX_ROWS = 5000;

    private final List<StringBuilder> lines = new ArrayList<>();
    private int row;
    private int column;
    private int savedRow;
    private int savedColumn;

    private TerminalTextRenderer() {
        lines.add(new StringBuilder());
    }

    static String render(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        TerminalTextRenderer renderer = new TerminalTextRenderer();
        renderer.consume(raw);
        return renderer.text();
    }

    private void consume(String raw) {
        for (int index = 0; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (ch == '\u001b') {
                index = consumeEscape(raw, index);
            } else if (ch == '\r') {
                column = 0;
            } else if (ch == '\n') {
                lineFeed();
            } else if (ch == '\b') {
                column = Math.max(0, column - 1);
            } else if (ch == '\t') {
                column = Math.min(MAX_COLUMNS - 1, ((column / 8) + 1) * 8);
            } else if (ch >= 0x20 && ch != 0x7f) {
                write(ch);
            }
        }
    }

    private int consumeEscape(String raw, int escapeIndex) {
        if (escapeIndex + 1 >= raw.length()) {
            return escapeIndex;
        }
        char kind = raw.charAt(escapeIndex + 1);
        if (kind == '[') {
            return consumeCsi(raw, escapeIndex + 2);
        }
        if (kind == ']' || kind == 'P' || kind == '^' || kind == '_') {
            return consumeStringControl(raw, escapeIndex + 2);
        }
        if (kind == '7') {
            saveCursor();
        } else if (kind == '8') {
            restoreCursor();
        } else if (kind == 'c') {
            resetScreen();
        } else if (kind == 'D') {
            lineFeedKeepingColumn();
        } else if (kind == 'E') {
            lineFeed();
        } else if (kind == 'M') {
            row = Math.max(0, row - 1);
            ensureRow(row);
        } else if (kind == '(' || kind == ')' || kind == '*' || kind == '+') {
            return Math.min(raw.length() - 1, escapeIndex + 2);
        }
        return escapeIndex + 1;
    }

    private int consumeStringControl(String raw, int start) {
        for (int index = start; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (ch == '\u0007') {
                return index;
            }
            if (ch == '\u001b' && index + 1 < raw.length() && raw.charAt(index + 1) == '\\') {
                return index + 1;
            }
        }
        return raw.length() - 1;
    }

    private int consumeCsi(String raw, int start) {
        for (int index = start; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (ch >= 0x40 && ch <= 0x7e) {
                applyCsi(raw.substring(start, index), ch);
                return index;
            }
        }
        return raw.length() - 1;
    }

    private void applyCsi(String parameterText, char command) {
        boolean privateMode = parameterText.startsWith("?");
        String clean = parameterText.replaceFirst("^[?!>]", "");
        int[] parameters = parseParameters(clean);
        switch (command) {
            case 'A':
                row = Math.max(0, row - parameter(parameters, 0, 1));
                break;
            case 'B':
            case 'e':
                row = Math.min(MAX_ROWS - 1, row + parameter(parameters, 0, 1));
                ensureRow(row);
                break;
            case 'C':
            case 'a':
                column = Math.min(MAX_COLUMNS - 1, column + parameter(parameters, 0, 1));
                break;
            case 'D':
                column = Math.max(0, column - parameter(parameters, 0, 1));
                break;
            case 'E':
                row = Math.min(MAX_ROWS - 1, row + parameter(parameters, 0, 1));
                column = 0;
                ensureRow(row);
                break;
            case 'F':
                row = Math.max(0, row - parameter(parameters, 0, 1));
                column = 0;
                break;
            case 'G':
            case '`':
                column = clampColumn(parameter(parameters, 0, 1) - 1);
                break;
            case 'H':
            case 'f':
                row = clampRow(parameter(parameters, 0, 1) - 1);
                column = clampColumn(parameter(parameters, 1, 1) - 1);
                ensureRow(row);
                break;
            case 'd':
                row = clampRow(parameter(parameters, 0, 1) - 1);
                ensureRow(row);
                break;
            case 'J':
                eraseDisplay(parameter(parameters, 0, 0));
                break;
            case 'K':
                eraseLine(parameter(parameters, 0, 0));
                break;
            case 'P':
                deleteCharacters(parameter(parameters, 0, 1));
                break;
            case '@':
                insertCharacters(parameter(parameters, 0, 1));
                break;
            case 'X':
                eraseCharacters(parameter(parameters, 0, 1));
                break;
            case 'L':
                insertLines(parameter(parameters, 0, 1));
                break;
            case 'M':
                deleteLines(parameter(parameters, 0, 1));
                break;
            case 's':
                saveCursor();
                break;
            case 'u':
                restoreCursor();
                break;
            case 'h':
                if (privateMode && contains(parameters, 1049)) {
                    resetScreen();
                }
                break;
            default:
                break;
        }
    }

    private int[] parseParameters(String value) {
        if (value == null || value.isEmpty()) {
            return new int[0];
        }
        String[] pieces = value.split(";", -1);
        int[] values = new int[pieces.length];
        for (int index = 0; index < pieces.length; index++) {
            try {
                values[index] = pieces[index].isEmpty() ? 0 : Integer.parseInt(pieces[index]);
            } catch (NumberFormatException ignored) {
                values[index] = 0;
            }
        }
        return values;
    }

    private int parameter(int[] parameters, int index, int defaultValue) {
        if (index >= parameters.length || parameters[index] == 0) {
            return defaultValue;
        }
        return parameters[index];
    }

    private boolean contains(int[] values, int expected) {
        for (int value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private void write(char ch) {
        if (column >= MAX_COLUMNS) {
            lineFeed();
        }
        ensureRow(row);
        StringBuilder line = lines.get(row);
        while (line.length() < column) {
            line.append(' ');
        }
        if (column < line.length()) {
            line.setCharAt(column, ch);
        } else {
            line.append(ch);
        }
        column++;
    }

    private void lineFeed() {
        column = 0;
        lineFeedKeepingColumn();
    }

    private void lineFeedKeepingColumn() {
        row++;
        if (row >= MAX_ROWS) {
            lines.remove(0);
            row = MAX_ROWS - 1;
            savedRow = Math.max(0, savedRow - 1);
        }
        ensureRow(row);
    }

    private void ensureRow(int target) {
        while (lines.size() <= target && lines.size() < MAX_ROWS) {
            lines.add(new StringBuilder());
        }
    }

    private void resetScreen() {
        lines.clear();
        lines.add(new StringBuilder());
        row = 0;
        column = 0;
        savedRow = 0;
        savedColumn = 0;
    }

    private void saveCursor() {
        savedRow = row;
        savedColumn = column;
    }

    private void restoreCursor() {
        row = clampRow(savedRow);
        column = clampColumn(savedColumn);
        ensureRow(row);
    }

    private void eraseDisplay(int mode) {
        ensureRow(row);
        if (mode == 2 || mode == 3) {
            for (StringBuilder line : lines) {
                line.setLength(0);
            }
            return;
        }
        if (mode == 1) {
            for (int index = 0; index < row; index++) {
                lines.get(index).setLength(0);
            }
            eraseLineBeforeCursor();
            return;
        }
        eraseLineAfterCursor();
        while (lines.size() > row + 1) {
            lines.remove(lines.size() - 1);
        }
    }

    private void eraseLine(int mode) {
        ensureRow(row);
        if (mode == 2) {
            lines.get(row).setLength(0);
        } else if (mode == 1) {
            eraseLineBeforeCursor();
        } else {
            eraseLineAfterCursor();
        }
    }

    private void eraseLineAfterCursor() {
        StringBuilder line = lines.get(row);
        if (column < line.length()) {
            line.setLength(column);
        }
    }

    private void eraseLineBeforeCursor() {
        StringBuilder line = lines.get(row);
        int end = Math.min(column, line.length() - 1);
        for (int index = 0; index <= end; index++) {
            line.setCharAt(index, ' ');
        }
    }

    private void deleteCharacters(int count) {
        StringBuilder line = lines.get(row);
        int end = Math.min(line.length(), column + Math.max(1, count));
        if (column < end) {
            line.delete(column, end);
        }
    }

    private void insertCharacters(int count) {
        StringBuilder line = lines.get(row);
        int amount = Math.min(Math.max(1, count), MAX_COLUMNS - column);
        while (line.length() < column) {
            line.append(' ');
        }
        for (int index = 0; index < amount; index++) {
            line.insert(column, ' ');
        }
        if (line.length() > MAX_COLUMNS) {
            line.setLength(MAX_COLUMNS);
        }
    }

    private void eraseCharacters(int count) {
        StringBuilder line = lines.get(row);
        int end = Math.min(MAX_COLUMNS, column + Math.max(1, count));
        while (line.length() < end) {
            line.append(' ');
        }
        for (int index = column; index < end; index++) {
            line.setCharAt(index, ' ');
        }
    }

    private void insertLines(int count) {
        int amount = Math.min(Math.max(1, count), MAX_ROWS - lines.size());
        for (int index = 0; index < amount; index++) {
            lines.add(Math.min(row, lines.size()), new StringBuilder());
        }
    }

    private void deleteLines(int count) {
        int amount = Math.min(Math.max(1, count), lines.size() - row);
        for (int index = 0; index < amount; index++) {
            lines.remove(row);
        }
        ensureRow(row);
    }

    private int clampRow(int value) {
        return Math.max(0, Math.min(MAX_ROWS - 1, value));
    }

    private int clampColumn(int value) {
        return Math.max(0, Math.min(MAX_COLUMNS - 1, value));
    }

    private String text() {
        int last = lines.size() - 1;
        while (last >= 0 && trimmedLength(lines.get(last)) == 0) {
            last--;
        }
        if (last < 0) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        for (int index = 0; index <= last; index++) {
            StringBuilder line = lines.get(index);
            output.append(line, 0, trimmedLength(line));
            if (index < last) {
                output.append('\n');
            }
        }
        return output.toString();
    }

    private int trimmedLength(StringBuilder line) {
        int length = line.length();
        while (length > 0 && line.charAt(length - 1) == ' ') {
            length--;
        }
        return length;
    }
}
