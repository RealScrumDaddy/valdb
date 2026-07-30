PRAGMA foreign_keys = ON;

-- Core structure
CREATE TABLE IF NOT EXISTS projects (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        name TEXT NOT NULL,
                                        description TEXT,
                                        created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

CREATE TABLE IF NOT EXISTS chapters (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
    );

CREATE TABLE IF NOT EXISTS tags (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    color TEXT
    );

CREATE TABLE IF NOT EXISTS field_definitions (
                                                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                                                 project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    entity_type TEXT NOT NULL CHECK (entity_type IN ('source','snippet')),
    name TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
    );

-- Sources
CREATE TABLE IF NOT EXISTS sources (
                                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                                       title TEXT NOT NULL,
                                       author TEXT,
                                       genre TEXT
);

CREATE TABLE IF NOT EXISTS source_projects (
                                               source_id INTEGER NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    PRIMARY KEY (source_id, project_id)
    );

CREATE TABLE IF NOT EXISTS source_field_values (
                                                   id INTEGER PRIMARY KEY AUTOINCREMENT,
                                                   source_id INTEGER NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    field_definition_id INTEGER NOT NULL REFERENCES field_definitions(id) ON DELETE CASCADE,
    value TEXT,
    UNIQUE(source_id, field_definition_id)
    );

-- Snippets
CREATE TABLE IF NOT EXISTS snippets (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        source_id INTEGER REFERENCES sources(id) ON DELETE SET NULL,
    original_content TEXT NOT NULL,
    translation_content TEXT,
    verse_start INTEGER,
    verse_end INTEGER,
    page INTEGER,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

CREATE TABLE IF NOT EXISTS snippet_projects (
                                                snippet_id INTEGER NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    PRIMARY KEY (snippet_id, project_id)
    );

CREATE TABLE IF NOT EXISTS snippet_field_values (
                                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                                    snippet_id INTEGER NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    field_definition_id INTEGER NOT NULL REFERENCES field_definitions(id) ON DELETE CASCADE,
    value TEXT,
    UNIQUE(snippet_id, field_definition_id)
    );

-- Many-to-many links
CREATE TABLE IF NOT EXISTS snippet_chapters (
                                                snippet_id INTEGER NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    chapter_id INTEGER NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    PRIMARY KEY (snippet_id, chapter_id)
    );

CREATE TABLE IF NOT EXISTS snippet_tags (
                                            snippet_id INTEGER NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (snippet_id, tag_id)
    );

-- Full-text search
CREATE VIRTUAL TABLE IF NOT EXISTS snippets_fts USING fts5(
    original_content,
    translation_content,
    custom_fields_text,
    source_fields_text
);

CREATE TRIGGER IF NOT EXISTS snippets_ai AFTER INSERT ON snippets BEGIN
    INSERT INTO snippets_fts(rowid, original_content, translation_content, custom_fields_text, source_fields_text)
    VALUES (
        new.id,
        new.original_content,
        new.translation_content,
        '',
        (SELECT s.title || ' ' || COALESCE(group_concat(v.value, ' '), '')
         FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
         WHERE s.id = new.source_id)
    );
END;

CREATE TRIGGER IF NOT EXISTS snippets_au AFTER UPDATE ON snippets BEGIN
UPDATE snippets_fts
SET original_content = new.original_content,
    translation_content = new.translation_content,
    source_fields_text = (
        SELECT s.title || ' ' || COALESCE(group_concat(v.value, ' '), '')
        FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
        WHERE s.id = new.source_id
    )
WHERE rowid = new.id;
END;

CREATE TRIGGER IF NOT EXISTS snippets_ad AFTER DELETE ON snippets BEGIN
DELETE FROM snippets_fts WHERE rowid = old.id;
END;

CREATE TRIGGER IF NOT EXISTS snippet_field_values_ai AFTER INSERT ON snippet_field_values BEGIN
UPDATE snippets_fts
SET custom_fields_text = (
    SELECT group_concat(value, ' ') FROM snippet_field_values WHERE snippet_id = new.snippet_id
)
WHERE rowid = new.snippet_id;
END;

CREATE TRIGGER IF NOT EXISTS snippet_field_values_au AFTER UPDATE ON snippet_field_values BEGIN
UPDATE snippets_fts
SET custom_fields_text = (
    SELECT group_concat(value, ' ') FROM snippet_field_values WHERE snippet_id = new.snippet_id
)
WHERE rowid = new.snippet_id;
END;

CREATE TRIGGER IF NOT EXISTS snippet_field_values_ad AFTER DELETE ON snippet_field_values BEGIN
UPDATE snippets_fts
SET custom_fields_text = (
    SELECT group_concat(value, ' ') FROM snippet_field_values WHERE snippet_id = old.snippet_id
)
WHERE rowid = old.snippet_id;
END;

CREATE TRIGGER IF NOT EXISTS sources_au AFTER UPDATE ON sources BEGIN
UPDATE snippets_fts
SET source_fields_text = (
    SELECT new.title || ' ' || COALESCE(group_concat(v.value, ' '), '')
    FROM source_field_values v WHERE v.source_id = new.id
)
WHERE rowid IN (SELECT id FROM snippets WHERE source_id = new.id);
END;

CREATE TRIGGER IF NOT EXISTS source_field_values_ai AFTER INSERT ON source_field_values BEGIN
UPDATE snippets_fts
SET source_fields_text = (
    SELECT s.title || ' ' || COALESCE(group_concat(v.value, ' '), '')
    FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
    WHERE s.id = new.source_id
)
WHERE rowid IN (SELECT id FROM snippets WHERE source_id = new.source_id);
END;

CREATE TRIGGER IF NOT EXISTS source_field_values_au AFTER UPDATE ON source_field_values BEGIN
UPDATE snippets_fts
SET source_fields_text = (
    SELECT s.title || ' ' || COALESCE(group_concat(v.value, ' '), '')
    FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
    WHERE s.id = new.source_id
)
WHERE rowid IN (SELECT id FROM snippets WHERE source_id = new.source_id);
END;

CREATE TRIGGER IF NOT EXISTS source_field_values_ad AFTER DELETE ON source_field_values BEGIN
UPDATE snippets_fts
SET source_fields_text = (
    SELECT s.title || ' ' || COALESCE(group_concat(v.value, ' '), '')
    FROM sources s LEFT JOIN source_field_values v ON v.source_id = s.id
    WHERE s.id = old.source_id
)
WHERE rowid IN (SELECT id FROM snippets WHERE source_id = old.source_id);
END;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_chapters_project ON chapters(project_id);
CREATE INDEX IF NOT EXISTS idx_tags_project ON tags(project_id);
CREATE INDEX IF NOT EXISTS idx_field_defs_project ON field_definitions(project_id);
CREATE INDEX IF NOT EXISTS idx_snippets_source ON snippets(source_id);
CREATE INDEX IF NOT EXISTS idx_source_projects_project ON source_projects(project_id);
CREATE INDEX IF NOT EXISTS idx_snippet_projects_project ON snippet_projects(project_id);
CREATE INDEX IF NOT EXISTS idx_source_field_values_source ON source_field_values(source_id);
CREATE INDEX IF NOT EXISTS idx_snippet_field_values_snippet ON snippet_field_values(snippet_id);