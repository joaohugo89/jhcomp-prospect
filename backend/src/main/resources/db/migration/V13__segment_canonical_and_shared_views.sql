-- V13 — Segmento com grafia unica e visoes compartilhadas (D31, aprovada).
--
-- Segmento: a mesma palavra com outra caixa ou espaco virava outro segmento
-- ("Barbearia", "barbearia ", "Barbearia  Centro"). A aplicacao passa a gravar
-- a grafia que ja existe na organizacao; aqui as grafias atuais sao unificadas
-- uma vez, adotando a mais usada (empate: ordem alfabetica). Nada e apagado:
-- so a escrita muda. updated_at fica como esta: nao foi o operador que editou.
--
-- Visoes: o administrador pode publicar uma visao para a equipe toda.
--
-- Migration imutavel apos aplicada (doc 04 §6). Corrigir em V14, nunca aqui.

-- 1. Segmento em branco e o mesmo que sem segmento.
UPDATE company SET segment = NULL WHERE segment IS NOT NULL AND btrim(segment) = '';

-- 2. Sem espaco nas pontas nem repetido no meio.
UPDATE company
   SET segment = regexp_replace(btrim(segment), '\s+', ' ', 'g')
 WHERE segment IS NOT NULL AND segment <> regexp_replace(btrim(segment), '\s+', ' ', 'g');

-- 3. Uma grafia por segmento e organizacao: a mais usada.
WITH spellings AS (
    SELECT organization_id, lower(segment) AS segment_key, segment,
           row_number() OVER (PARTITION BY organization_id, lower(segment)
                              ORDER BY count(*) DESC, segment) AS rank
      FROM company
     WHERE segment IS NOT NULL
     GROUP BY organization_id, lower(segment), segment
)
UPDATE company c
   SET segment = s.segment
  FROM spellings s
 WHERE s.rank = 1
   AND c.organization_id = s.organization_id
   AND lower(c.segment) = s.segment_key
   AND c.segment <> s.segment;

-- 4. Visao compartilhada: de quem publicou, visivel para a organizacao.
ALTER TABLE lead_view ADD COLUMN shared boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN lead_view.shared IS
  'Publicada para a equipe pelo administrador. Privada (false): so o dono ve.';

-- Nome unico entre as compartilhadas da organizacao, sem distinguir caixa.
CREATE UNIQUE INDEX lead_view_shared_name_per_org ON lead_view (organization_id, lower(name)) WHERE shared;
