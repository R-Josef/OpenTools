package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import org.junit.jupiter.api.Test;

import javax.swing.event.TableModelEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JobsTableModelTest {
    @Test
    void statusRefreshUpdatesRowsWithoutClearingTableSelection() {
        LanguageManager language = new LanguageManager(
                Path.of("target", "test-language"),
                "en_US"
        );
        JobsTableModel model = new JobsTableModel(language);
        int[] fullDataChanges = {0};
        model.addTableModelListener(event -> {
            if (event.getType() == TableModelEvent.UPDATE
                    && event.getFirstRow() == 0
                    && event.getLastRow() == 1) {
                fullDataChanges[0]++;
            }
        });
        model.setJobs(List.of(job("run-a", JobStatus.RUNNING), job("run-b", JobStatus.SUCCEEDED)));

        model.setJobs(List.of(job("run-a", JobStatus.FAILED), job("run-b", JobStatus.SUCCEEDED)));

        assertEquals(1, fullDataChanges[0]);
        assertEquals(JobStatus.FAILED, model.row(0).status());
    }

    private static JobRecord job(String runId, JobStatus status) {
        return new JobRecord(
                runId,
                "test.tool",
                "1.0.0",
                status,
                Instant.now(),
                Instant.now(),
                null,
                "{}",
                null,
                null,
                null,
                Path.of("runs", runId)
        );
    }
}
