/**
 * A CSV's headers, read in the browser for manual column mapping.
 *
 * Only the first line is interpreted, with the same delimiter detection as the
 * backend (`;`, `,` or tab). File validation stays on the server; here it is
 * enough to offer the columns for the operator to choose from.
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

/** Reads only the start of the file: a header fits comfortably in 64 KB. */
export async function readHeaders(file: File): Promise<readonly string[]> {
  return parseHeaderLine(await file.slice(0, 64 * 1024).text());
}
