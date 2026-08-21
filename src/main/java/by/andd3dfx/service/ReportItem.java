package by.andd3dfx.service;

import by.andd3dfx.model.Tag;

import java.util.Map;

public class ReportItem {
    private final Long pullRequestId;

    private final String createdDate;


    private final String mergedDate;

    private final String pullRequestLink;

    private final Map<Tag, Integer> prStatistic;

    private final int daysCountWithoutAnyActivity;

    private final int commitCount;

    public ReportItem(Long pullRequestId, String createdDate, String mergedDate, String pullRequestLink, Map<Tag, Integer> prStatistic,
                      int daysCountWithoutAnyActivity, int commitCount) {
        this.pullRequestId = pullRequestId;
        this.createdDate = createdDate;
        this.mergedDate = mergedDate;
        this.pullRequestLink = pullRequestLink;
        this.prStatistic = prStatistic;
        this.daysCountWithoutAnyActivity = daysCountWithoutAnyActivity;
        this.commitCount = commitCount;
    }

    public Long getPullRequestId() {
        return pullRequestId;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getMergedDate() {
        return mergedDate;
    }

    public String getPullRequestLink() {
        return pullRequestLink;
    }

    public Map<Tag, Integer> getPrStatistic() {
        return prStatistic;
    }

    public int getDaysCountWithoutAnyActivity() {
        return daysCountWithoutAnyActivity;
    }

    public int getCommitCount() {
        return commitCount;
    }
}
