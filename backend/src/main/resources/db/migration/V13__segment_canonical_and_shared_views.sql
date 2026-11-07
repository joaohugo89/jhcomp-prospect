-- V13 — One spelling per segment, and shared views (D31, approved).
--
-- Segment: the same word in another case or with different spacing became
-- another segment ("Barbearia", "barbearia ", "Barbearia  Centro"). The
-- application now stores the spelling that already exists in the
-- organisation; here the current spellings are unified once, adopting the
-- most used one (ties: alphabetical order). Nothing is deleted: only the
-- spelling changes. updated_at is left as it is: it was not the operator who
-- edited.
--
-- Views: an administrator can publish a view to the whole team.
--
-- Immutable once applied (doc 04 §6). Correct in V14, never here.

-- 1. A blank segment is the same as no segment.
UPDATE company SET segment = NULL WHERE segment IS NOT NULL AND btrim(segment) = '';

-- 2. No surrounding spaces, and no repeated ones in the middle.
UPDATE company
   SET segment = regexp_replace(btrim(segment), '\s+', ' ', 'g')
 WHERE segment IS NOT NULL AND segment <> regexp_replace(btrim(segment), '\s+', ' ', 'g');

-- 3. One spelling per segment and organisation: the most used one.
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

-- 4. A shared view: owned by whoever published it, visible to the organisation.
ALTER TABLE lead_view ADD COLUMN shared boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN lead_view.shared IS
  'Publicada para a equipe pelo administrador. Privada (false): so o dono ve.';

-- One name among the organisation's shared views, case-insensitively.
CREATE UNIQUE INDEX lead_view_shared_name_per_org ON lead_view (organization_id, lower(name)) WHERE shared;
