package com.jhcomp.prospect.discovery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.jhcomp.prospect.shared.error.InvalidRequestException;

/**
 * The contents of a CSV, already read and validated in shape (D14).
 *
 * <p>The limits are checked before any per-row processing: a file that is too
 * large, or has too many rows, is refused whole with a clear message rather
 * than imported halfway.
 *
 * @param headers headers in file order, with surrounding whitespace removed
 * @param records data rows; none of them entirely empty
 */
record CsvTable(List<String> headers, List<Row> records) {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 2_000;
    static final int MAX_COLUMNS = 50;

    /**
     * One data row.
     *
     * @param rowNumber the record number, counting the header as 1
     * @param cells cells in header order; missing ones become empty strings
     * @param extraCells how many filled cells there are beyond the header
     */
    record Row(int rowNumber, List<String> cells, int extraCells) {

        String cell(int column) {
            return column < cells.size() ? cells.get(column) : "";
        }
    }

    static CsvTable parse(byte[] content) {
        if (content.length == 0) {
            throw new InvalidRequestException("File is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidRequestException("File exceeds the 2 MB limit");
        }

        String text = decodeUtf8(content);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(detectDelimiter(text))
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        List<String> headers = null;
        List<Row> rows = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(text, format)) {
            for (CSVRecord record : parser) {
                if (headers == null) {
                    headers = readHeaders(record);
                    continue;
                }
                if (isBlank(record)) {
                    // Empty rows at the end of a spreadsheet are common and are
                    // not records: counting them as rejected would only confuse
                    // the report.
                    continue;
                }
                if (rows.size() == MAX_ROWS) {
                    throw new InvalidRequestException("File exceeds the limit of " + MAX_ROWS + " rows");
                }
                rows.add(toRow(record, headers.size()));
            }
        } catch (IOException | UncheckedIOException exception) {
            // An unclosed quote is the typical case. The library's message
            // does not reach the client: it cites internal parser positions.
            throw new InvalidRequestException("File is not a valid CSV");
        }

        if (headers == null) {
            throw new InvalidRequestException("File has no header row");
        }
        if (rows.isEmpty()) {
            throw new InvalidRequestException("File has no data rows");
        }
        return new CsvTable(headers, List.copyOf(rows));
    }

    /**
     * Requires UTF-8. Decoding in the wrong charset does not fail: it silently
     * replaces accents with rubbish, and that rubbish would be stored as a
     * company name.
     */
    private static String decodeUtf8(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            // The BOM Excel writes in "CSV UTF-8": without removing it, the
            // first header would not be recognised.
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException exception) {
            throw new InvalidRequestException("File must be UTF-8 encoded");
        }
    }

    /**
     * The delimiter, detected from the header. Excel in Portuguese exports
     * with semicolons, because the comma is the decimal separator.
     */
    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        char best = ',';
        long bestCount = firstLine.chars().filter(c -> c == ',').count();
        for (char candidate : new char[] {';', '\t'}) {
            long count = firstLine.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    private static List<String> readHeaders(CSVRecord record) {
        if (record.size() > MAX_COLUMNS) {
            throw new InvalidRequestException("File exceeds the limit of " + MAX_COLUMNS + " columns");
        }
        List<String> headers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String header : record) {
            String value = header.strip();
            if (value.isEmpty()) {
                throw new InvalidRequestException("Header has an empty column name");
            }
            if (!seen.add(value.toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Header has duplicated column: " + value);
            }
            headers.add(value);
        }
        return List.copyOf(headers);
    }

    private static Row toRow(CSVRecord record, int headerCount) {
        List<String> cells = new ArrayList<>(headerCount);
        int extra = 0;
        for (int i = 0; i < record.size(); i++) {
            String value = record.get(i);
            if (i < headerCount) {
                cells.add(value);
            } else if (!value.isBlank()) {
                extra++;
            }
        }
        // The record number, with the header as 1. It does not shift on a line
        // break inside a cell, unlike a count of text lines; it matches the row
        // of the exported spreadsheet, which has no blank text line in the
        // middle. A row of delimiters alone still counts.
        return new Row((int) record.getRecordNumber(), List.copyOf(cells), extra);
    }

    private static boolean isBlank(CSVRecord record) {
        for (String value : record) {
            if (!value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
