package by.andd3dfx.service;

import com.opencsv.CSVWriter;
import by.andd3dfx.model.Tag;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Report {

    private final List<ReportItem> items;

    public Report(List<ReportItem> items) {
        this.items = items;
    }

    public List<ReportItem> getItems() {
        return items;
    }

    public void save(String filePath) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            List<String> header = new ArrayList<>();
            header.add("pullRequestId");
            header.add("createdDate");
            header.add("mergedDate");
            header.add("pullRequestLink");
            header.add("daysCountWithoutAnyActivity");

            header.add("reason");
            header.add("count");
            header.add("commitsCount");

            writer.writeNext(header.toArray(new String[]{}));

            for (ReportItem reportItem : items) {
                for (Tag tag : Tag.values()) {
                    Integer tagValue = reportItem.getPrStatistic().getOrDefault(tag, 0);
                    if (tagValue > 0) {
                        List<String> entry = new ArrayList<>();
                        entry.add(reportItem.getPullRequestId().toString());
                        entry.add(reportItem.getCreatedDate());
                        entry.add(reportItem.getMergedDate());
                        entry.add(reportItem.getPullRequestLink());
                        entry.add(String.valueOf(reportItem.getDaysCountWithoutAnyActivity()));
                        entry.add(tag.name());
                        entry.add(tagValue.toString());
                        entry.add(String.valueOf(reportItem.getCommitCount()));
                        writer.writeNext(entry.toArray(new String[]{}));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
