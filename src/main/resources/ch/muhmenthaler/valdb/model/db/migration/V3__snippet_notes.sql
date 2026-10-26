ALTER TABLE snippets ADD COLUMN notes_content TEXT;

-- Empty or whitespace-only text means "no value": store it as NULL
UPDATE snippets SET translation_content = NULL
WHERE TRIM(translation_content, ' ' || char(9) || char(10) || char(13)) = '';

-- FTS5 tables can't be altered: rebuild with the new column
DROP TABLE IF EXISTS snippets_fts;
CREATE VIRTUAL TABLE snippets_fts USING fts5(
    original_content,
    translation_content,
    notes_content,
    custom_fields_text,
    source_fields_text,
    verse_text,
    page_text,
    tags_text
);

DROP TRIGGER IF EXISTS snippet_chapters_ai;
DROP TRIGGER IF EXISTS snippet_chapters_ad;
DROP TRIGGER IF EXISTS chapters_au;

-- Refill the index from the existing data
INSERT INTO snippets_fts(rowid, original_content, translation_content, notes_content, custom_fields_text,
                         source_fields_text, verse_text, page_text, tags_text)
SELECT
    sn.id,
    sn.original_content,
    sn.translation_content,
    sn.notes_content,
    COALESCE((SELECT group_concat(v.value, ' ')
              FROM snippet_field_values v WHERE v.snippet_id = sn.id), ''),
    COALESCE((SELECT s.title || ' ' || COALESCE(s.author, '') || ' ' || COALESCE(s.genre, '') || ' '
                         || COALESCE(group_concat(v.value, ' '), '')
              FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
              WHERE s.id = sn.source_id), ''),
    CASE
        WHEN sn.verse_start IS NULL THEN ''
        WHEN sn.verse_end IS NULL THEN CAST(sn.verse_start AS TEXT)
        ELSE CAST(sn.verse_start AS TEXT) || '-' || CAST(sn.verse_end AS TEXT)
    END,
    CASE
        WHEN sn.page_start IS NULL THEN ''
        WHEN sn.page_end IS NULL THEN CAST(sn.page_start AS TEXT)
        ELSE CAST(sn.page_start AS TEXT) || '-' || CAST(sn.page_end AS TEXT)
    END,
    COALESCE((SELECT group_concat(t.name, ' ')
              FROM tags t JOIN snippet_tags st ON st.tag_id = t.id
              WHERE st.snippet_id = sn.id), '')
FROM snippets sn;

DROP TRIGGER IF EXISTS snippets_ai;
CREATE TRIGGER snippets_ai AFTER INSERT ON snippets BEGIN
    INSERT INTO snippets_fts(rowid, original_content, translation_content, notes_content, custom_fields_text,
                             source_fields_text, verse_text, page_text, tags_text)
    VALUES (
        new.id,
        new.original_content,
        new.translation_content,
        new.notes_content,
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
        ''
    );
END;

DROP TRIGGER IF EXISTS snippets_au;
CREATE TRIGGER snippets_au AFTER UPDATE ON snippets BEGIN
    UPDATE snippets_fts
    SET original_content = new.original_content,
        translation_content = new.translation_content,
        notes_content = new.notes_content,
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