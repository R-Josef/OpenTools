package io.opentools.ui;

import io.opentools.core.job.JobRecord;
import io.opentools.core.i18n.ErrorText;
import io.opentools.core.i18n.LanguageManager;

import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class JobsTableModel extends AbstractTableModel {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private List<JobRecord> jobs = new ArrayList<>();
    private final LanguageManager language;

    JobsTableModel(LanguageManager language) {
        this.language = language;
    }

    public void setJobs(List<JobRecord> jobs) {
        List<JobRecord> updated = new ArrayList<>(jobs);
        boolean sameRows = this.jobs.size() == updated.size();
        if (sameRows) {
            for (int index = 0; index < updated.size(); index++) {
                if (!this.jobs.get(index).runId().equals(updated.get(index).runId())) {
                    sameRows = false;
                    break;
                }
            }
        }
        this.jobs = updated;
        if (sameRows) {
            if (!updated.isEmpty()) {
                fireTableRowsUpdated(0, updated.size() - 1);
            }
        } else {
            fireTableDataChanged();
        }
    }

    public JobRecord row(int index) {
        return index < 0 || index >= jobs.size() ? null : jobs.get(index);
    }

    @Override
    public int getRowCount() {
        return jobs.size();
    }

    @Override
    public int getColumnCount() {
        return 7;
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> language.text("jobs.column.run_id", "Run ID");
            case 1 -> language.text("jobs.column.tool", "Tool");
            case 2 -> language.text("jobs.column.status", "Status");
            case 3 -> language.text("jobs.column.progress", "Progress");
            case 4 -> language.text("jobs.column.started", "Started");
            case 5 -> language.text("jobs.column.finished", "Finished");
            case 6 -> language.text("jobs.column.diagnostic", "Diagnostic");
            default -> "";
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        JobRecord job = jobs.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> shortened(job.runId());
            case 1 -> job.toolId();
            case 2 -> language.text("job.status." + job.status().name(), job.status().name());
            case 3 -> progress(job);
            case 4 -> job.startedAt() == null ? "" : TIME_FORMAT.format(job.startedAt());
            case 5 -> job.finishedAt() == null ? "" : TIME_FORMAT.format(job.finishedAt());
            case 6 -> ErrorText.localize(language, job.errorCode(), job.errorMessage());
            default -> "";
        };
    }

    private static String progress(JobRecord job) {
        if (job.status() == io.opentools.core.job.JobStatus.QUEUED) {
            return job.progressMessage() == null ? "" : job.progressMessage();
        }
        String percent = Math.round(job.progress() * 100) + "%";
        return job.progressMessage() == null || job.progressMessage().isBlank()
                ? percent
                : percent + " · " + job.progressMessage();
    }

    private static String shortened(String value) {
        return value.length() <= 12 ? value : value.substring(0, 8);
    }
}
