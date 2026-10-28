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
 * Conteudo de um CSV ja lido e validado na forma (D14).
 *
 * <p>Os limites sao verificados antes de qualquer processamento por linha:
 * arquivo grande demais ou com linhas demais e recusado inteiro, com mensagem
 * clara, em vez de ser importado pela metade.
 *
 * @param headers cabecalhos na ordem do arquivo, sem espaco em volta
 * @param records linhas de dados; nenhuma totalmente vazia
 */
record CsvTable(List<String> headers, List<Row> records) {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 2_000;
    static final int MAX_COLUMNS = 50;

    /**
     * Linha de dados.
     *
     * @param rowNumber numero do registro, contando o cabecalho como 1
     * @param cells celulas na ordem dos cabecalhos; ausentes viram texto vazio
     * @param extraCells quantidade de celulas preenchidas alem do cabecalho
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
                    // Linhas vazias no fim da planilha sao comuns e nao sao
                    // registros: contar como rejeitadas so confundiria o relatorio.
                    continue;
                }
                if (rows.size() == MAX_ROWS) {
                    throw new InvalidRequestException("File exceeds the limit of " + MAX_ROWS + " rows");
                }
                rows.add(toRow(record, headers.size()));
            }
        } catch (IOException | UncheckedIOException exception) {
            // Aspas sem fechamento e o caso tipico. A mensagem da biblioteca
            // nao vai ao cliente: cita posicoes internas do parser.
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
     * Exige UTF-8. Decodificar com o charset errado nao falha: troca acentos
     * por lixo em silencio, e o lixo seria gravado como nome de empresa.
     */
    private static String decodeUtf8(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            // BOM que o Excel grava em "CSV UTF-8": sem remover, o primeiro
            // cabecalho nao seria reconhecido.
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException exception) {
            throw new InvalidRequestException("File must be UTF-8 encoded");
        }
    }

    /**
     * Delimitador pelo cabecalho. O Excel em portugues exporta com ponto e
     * virgula, porque a virgula e o separador decimal.
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
        // Numero do registro, com o cabecalho como 1. Nao se desloca com quebra
        // de linha dentro de celula, ao contrario de uma contagem por linha de
        // texto; coincide com a linha da planilha exportada, que nao tem linha
        // de texto vazia no meio. Linha so com delimitadores conta normalmente.
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
