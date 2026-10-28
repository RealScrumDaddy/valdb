-- A tag disappears as soon as no snippet uses it any more
CREATE TRIGGER IF NOT EXISTS snippet_tags_ad_cleanup AFTER DELETE ON snippet_tags
BEGIN
DELETE FROM tags
WHERE id = old.tag_id
  AND NOT EXISTS (SELECT 1 FROM snippet_tags WHERE tag_id = old.tag_id);
END;

-- One-time cleanup of tags that are already unused
DELETE FROM tags
WHERE NOT EXISTS (SELECT 1 FROM snippet_tags WHERE snippet_tags.tag_id = tags.id);