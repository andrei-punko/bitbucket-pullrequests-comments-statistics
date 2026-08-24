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

            header.add("commitsCount");
            header.add("commentsCount");
            header.add("chainsCount");
            header.add("totalTagsCount");
            for (Tag tag : Tag.values()) {
                header.add(tag.name());
            }

            writer.writeNext(header.toArray(new String[]{}));

            for (ReportItem reportItem : items) {
                List<String> entry = new ArrayList<>();
                entry.add(reportItem.getPullRequestId().toString());
                entry.add(reportItem.getCreatedDate());
                entry.add(reportItem.getMergedDate());
                entry.add(reportItem.getPullRequestLink());
                entry.add(String.valueOf(reportItem.getDaysCountWithoutAnyActivity()));

                entry.add(String.valueOf(reportItem.getCommitCount()));
                entry.add(String.valueOf(reportItem.getCommentsCount()));
                entry.add(String.valueOf(reportItem.getChainsCount()));
                entry.add(String.valueOf(reportItem.getTotalTagsCount()));

                for (Tag tag : Tag.values()) {
                    Integer tagValue = reportItem.getPrStatistic().getOrDefault(tag, 0);
                    entry.add(String.valueOf(tagValue));
                }

                writer.writeNext(entry.toArray(new String[]{}));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
