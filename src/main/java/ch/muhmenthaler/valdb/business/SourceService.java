package ch.muhmenthaler.valdb.business;

import ch.muhmenthaler.valdb.gui.dialog.AddSourceDialog;
import ch.muhmenthaler.valdb.repository.FieldDefinitionRepository;
import ch.muhmenthaler.valdb.repository.SourceRepository;

import java.sql.SQLException;
import java.util.List;

public class SourceService {
    private final SourceRepository sourceRepo = new SourceRepository();
    private final FieldDefinitionRepository fieldDefinitionRepo = new FieldDefinitionRepository();

    /** Creates a source from the dialog's input, including its custom field values and any new field definitions. */
    public int create(int projectId, AddSourceDialog.Input input, int nextSortOrder) throws SQLException {
        int sourceId = sourceRepo.insert(List.of(projectId), input.title(), input.author(), input.genre());
        for (var entry : input.customFieldValues().entrySet()) {
            sourceRepo.setFieldValue(sourceId, entry.getKey(), entry.getValue());
        }
        for (AddSourceDialog.NewCustomField field : input.newCustomFields()) {
            int fieldDefId = fieldDefinitionRepo.insert(projectId, "source", field.name(), nextSortOrder++);
            sourceRepo.setFieldValue(sourceId, fieldDefId, field.value());
        }
        return sourceId;
    }
}