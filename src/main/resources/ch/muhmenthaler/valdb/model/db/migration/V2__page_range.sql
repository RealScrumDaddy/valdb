ALTER TABLE snippets RENAME COLUMN page TO page_start;
ALTER TABLE snippets ADD COLUMN page_end INTEGER;

DROP TRIGGER IF EXISTS snippets_ai;
CREATE TRIGGER snippets_ai AFTER INSERT ON snippets BEGIN
    INSERT INTO snippets_fts(rowid, original_content, translation_content, custom_fields_text, source_fields_text,
                             verse_text, page_text, chapters_text, tags_text)
    VALUES (
               new.id,
               new.original_content,
               new.translation_content,
               '',
               (SELECT s.title || ' ' || COALESCE(s.author, '') || ' ' || COALESCE(s.genre, '') || ' '
                           || COALESCE(group_concat(v.value, ' '), '')
                FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
                WHERE s.id = new.source_id),
               CASE
                   WHEN new.verse_start IS NULL THEN ''
                   WHEN new.verse_end IS NULL THEN CAST(new.verse_start AS TEXT)
                   ELSE CAST(new.verse_start AS TEXT) || '-' || CAST(new.verse_end AS TEXT)
                   END,
               CASE
                   WHEN new.page_start IS NULL THEN ''
                   WHEN new.page_end IS NULL THEN CAST(new.page_start AS TEXT)
                   ELSE CAST(new.page_start AS TEXT) || '-' || CAST(new.page_end AS TEXT)
                   END,
               '',
               ''
           );
END;

DROP TRIGGER IF EXISTS snippets_au;
CREATE TRIGGER snippets_au AFTER UPDATE ON snippets BEGIN
    UPDATE snippets_fts
    SET original_content = new.original_content,
        translation_content = new.translation_content,
        source_fields_text = (
            SELECT s.title || ' ' || COALESCE(s.author, '') || ' ' || COALESCE(s.genre, '') || ' '
                       || COALESCE(group_concat(v.value, ' '), '')
            FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
            WHERE s.id = new.source_id
        ),
        verse_text = CASE
                         WHEN new.verse_start IS NULL THEN ''
                         WHEN new.verse_end IS NULL THEN CAST(new.verse_start AS TEXT)
                         ELSE CAST(new.verse_start AS TEXT) || '-' || CAST(new.verse_end AS TEXT)
            END,
        page_text = CASE
                        WHEN new.page_start IS NULL THEN ''
                        WHEN new.page_end IS NULL THEN CAST(new.page_start AS TEXT)
                        ELSE CAST(new.page_start AS TEXT) || '-' || CAST(new.page_end AS TEXT)
            END
    WHERE rowid = new.id;
END;