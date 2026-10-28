package com.jhcomp.prospect.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jhcomp.prospect.shared.error.InvalidRequestException;

/** Leitura e limites do CSV (RF-015, D14). Unitario, sem Spring. */
@DisplayName("CsvTable")
class CsvTableTest {

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("le ponto e virgula, como exporta o Excel em portugues")
    void semicolon() {
        CsvTable table = CsvTable.parse(utf8("nome;cidade;uf\nPadaria Ficticia, Ltda;Cidade Exemplo;MG\n"));

        assertThat(table.headers()).containsExactly("nome", "cidade", "uf");
        assertThat(table.records().getFirst().cells()).containsExactly("Padaria Ficticia, Ltda", "Cidade Exemplo", "MG");
    }

    @Test
    @DisplayName("le virgula e tabulacao")
    void commaAndTab() {
        assertThat(CsvTable.parse(utf8("nome,cidade\nA,B\n")).headers()).containsExactly("nome", "cidade");
        assertThat(CsvTable.parse(utf8("nome\tcidade\nA\tB\n")).headers()).containsExactly("nome", "cidade");
    }

    @Test
    @DisplayName("remove o BOM do Excel e preserva acentos")
    void bomAndAccents() {
        CsvTable table = CsvTable.parse(utf8("﻿nome;cidade\nSalão Estética;Poços\n"));

        assertThat(table.headers().getFirst()).isEqualTo("nome");
        assertThat(table.records().getFirst().cells()).containsExactly("Salão Estética", "Poços");
    }

    /** ISO-8859-1 decodificado como UTF-8 viraria lixo gravado como nome de empresa. */
    @Test
    @DisplayName("recusa arquivo que nao e UTF-8")
    void rejectsNonUtf8() {
        byte[] latin1 = "nome;cidade\nSalão;Poços\n".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> CsvTable.parse(latin1))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    @DisplayName("aceita aspas, aspas escapadas e quebra de linha dentro da celula")
    void quotedCells() {
        CsvTable table = CsvTable.parse(utf8("nome;obs;uf\n\"Loja \"\"Central\"\"\";\"linha 1\nlinha 2; com ponto e virgula\";MG\nOutra;x;SP\n"));

        assertThat(table.records()).hasSize(2);
        assertThat(table.records().getFirst().cells())
                .containsExactly("Loja \"Central\"", "linha 1\nlinha 2; com ponto e virgula", "MG");
        // A quebra de linha dentro da celula nao desloca a numeracao.
        assertThat(table.records().get(1).rowNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("ignora linhas vazias e conta celulas alem do cabecalho")
    void blankRowsAndExtraCells() {
        CsvTable table = CsvTable.parse(utf8("nome;uf\nA;MG\n;\n\nB;SP;sobra;\n"));

        assertThat(table.records()).extracting(CsvTable.Row::cells)
                .containsExactly(java.util.List.of("A", "MG"), java.util.List.of("B", "SP"));
        assertThat(table.records().get(1).extraCells()).isEqualTo(1);
    }

    @Test
    @DisplayName("linha mais curta que o cabecalho tem celulas vazias")
    void shortRow() {
        CsvTable table = CsvTable.parse(utf8("nome;cidade;uf\nA\n"));

        assertThat(table.records().getFirst().cell(2)).isEmpty();
    }

    @Test
    @DisplayName("recusa cabecalho repetido ou vazio")
    void invalidHeader() {
        assertThatThrownBy(() -> CsvTable.parse(utf8("nome;Nome\nA;B\n")))
                .hasMessageContaining("duplicated");
        assertThatThrownBy(() -> CsvTable.parse(utf8("nome;;uf\nA;B;C\n")))
                .hasMessageContaining("empty column");
    }

    @Test
    @DisplayName("recusa arquivo vazio, sem dados ou com aspas sem fechamento")
    void invalidContent() {
        assertThatThrownBy(() -> CsvTable.parse(new byte[0])).hasMessageContaining("empty");
        assertThatThrownBy(() -> CsvTable.parse(utf8("nome;uf\n"))).hasMessageContaining("no data rows");
        assertThatThrownBy(() -> CsvTable.parse(utf8("nome;uf\n\"aberta;MG\n")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("File is not a valid CSV");
    }

    @Test
    @DisplayName("recusa mais de 2000 linhas inteiro, sem importar pela metade")
    void rowLimit() {
        StringBuilder csv = new StringBuilder("nome;uf\n");
        for (int i = 0; i < CsvTable.MAX_ROWS; i++) {
            csv.append("Empresa ").append(i).append(";MG\n");
        }
        assertThat(CsvTable.parse(utf8(csv.toString())).records()).hasSize(CsvTable.MAX_ROWS);

        csv.append("Uma a mais;MG\n");
        assertThatThrownBy(() -> CsvTable.parse(utf8(csv.toString()))).hasMessageContaining("2000 rows");
    }

    @Test
    @DisplayName("recusa mais de 2 MB")
    void sizeLimit() {
        byte[] content = new byte[CsvTable.MAX_BYTES + 1];
        Arrays.fill(content, (byte) 'a');

        assertThatThrownBy(() -> CsvTable.parse(content)).hasMessageContaining("2 MB");
    }

    @Test
    @DisplayName("reconhece cabecalhos usuais sem mapeamento manual")
    void guessesHeaders() {
        assertThat(ImportField.guess("Razão Social")).contains(ImportField.NAME);
        assertThat(ImportField.guess(" E-mail ")).contains(ImportField.BUSINESS_EMAIL);
        assertThat(ImportField.guess("Município")).contains(ImportField.CITY);
        assertThat(ImportField.guess("WhatsApp")).contains(ImportField.PHONE);
        assertThat(ImportField.guess("Faturamento")).isEmpty();
    }
}
