/**
 * Cabeçalhos de um CSV, lidos no navegador para o mapeamento manual de
 * colunas.
 *
 * Só a primeira linha é interpretada, com a mesma detecção de delimitador do
 * backend (`;`, `,` ou tabulação). A validação do arquivo continua no
 * servidor; aqui basta oferecer as colunas para o operador escolher.
 */
export function parseHeaderLine(text: string): readonly string[] {
  const withoutBom = text.startsWith('﻿') ? text.slice(1) : text;
  const end = withoutBom.search(/\r?\n/);
  const line = end < 0 ? withoutBom : withoutBom.slice(0, end);

  const delimiter = detectDelimiter(line);
  const cells: string[] = [];
  let current = '';
  let quoted = false;

  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (quoted) {
      if (char === '"' && line[i + 1] === '"') {
        current += '"';
        i++;
      } else if (char === '"') {
        quoted = false;
      } else {
        current += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === delimiter) {
      cells.push(current.trim());
      current = '';
    } else {
      current += char;
    }
  }
  cells.push(current.trim());

  return cells.filter((cell) => cell !== '');
}

function detectDelimiter(line: string): string {
  const count = (char: string): number => line.split(char).length - 1;
  let best = ',';
  for (const candidate of [';', '\t']) {
    if (count(candidate) > count(best)) {
      best = candidate;
    }
  }
  return best;
}

/** Lê só o começo do arquivo: o cabeçalho cabe com folga em 64 KB. */
export async function readHeaders(file: File): Promise<readonly string[]> {
  return parseHeaderLine(await file.slice(0, 64 * 1024).text());
}
