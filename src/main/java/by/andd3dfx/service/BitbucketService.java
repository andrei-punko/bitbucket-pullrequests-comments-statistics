package by.andd3dfx.service;

import by.andd3dfx.model.Activity;
import by.andd3dfx.model.Comment;
import by.andd3dfx.model.PullRequest;
import by.andd3dfx.model.PullRequestState;
import by.andd3dfx.model.Response;
import by.andd3dfx.model.SimpleResponse;
import by.andd3dfx.model.Tag;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BitbucketService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private static final String PAGINATION = ",size,page,next";

    private static final String PULL_REQUESTS_Q = "&q=comment_count>0";

    private static final String PAGE_LEN_PR = "&pagelen=50";

    private static final String PAGE_LEN_COMMENTS = "&pagelen=100";

    private static final int MAX_RATE_LIMIT_RETRIES = 8;

    private static final Duration DEFAULT_RETRY_WAIT = Duration.ofSeconds(45);

    private static final Duration MAX_RETRY_WAIT = Duration.ofHours(1);

    private static final String PULL_REQUESTS = "pullrequests?state=OPEN,state=MERGED,state=DECLINED,state=SUPERSEDED&fields=values.id," +
            "values.comment_count,values.created_on,values.updated_on,values.links.html.href,values.state" + PAGINATION + PULL_REQUESTS_Q + PAGE_LEN_PR;

    private static final String COMMENT_FIELDS = "values.id,values.created_on,values.updated_on,values.parent,values.content,values.pullrequest";

    private static final String COMMENTS = "pullrequests/%d/comments?" + "fields=%s".formatted(COMMENT_FIELDS) + PAGINATION + PAGE_LEN_COMMENTS;

    private static final String ACTIVITY_FIELDS = "values.update,values.approval,values.comment";

    private static final String ACTIVITY = "pullrequests/%d/activity?" + "fields=%s".formatted(ACTIVITY_FIELDS) + PAGINATION + PAGE_LEN_PR;

    private static final String SINGLE_COMMENT_FIELDS = "id,created_on,updated_on,parent,content,pullrequest";

    private static final String SINGLE_COMMENT = "pullrequests/%d/comments/%d?" + "fields=%s".formatted(SINGLE_COMMENT_FIELDS);

    private final RestTemplate restTemplate;

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    @Value("${REPOSITORY_URL}")
    private String serviceUrl;

    @Value("${CSV_PATH_TO_EXPORT}")
    private String pathToExport;

    public BitbucketService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void export() {
        Instant started = Instant.now();
        log("Repository API: " + serviceUrl);
        log("Downloading pull request list...");

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(serviceUrl + PULL_REQUESTS);
        String uriString = builder
                .build(false)
                .toString();

        ParameterizedTypeReference<Response<PullRequest>> pullRequestType = new ParameterizedTypeReference<>() {
        };
        int page = 1;
        Response<PullRequest> pullRequestsResponse = get(uriString, pullRequestType);
        List<PullRequest> pullRequests = new ArrayList<>(pullRequestsResponse.values().stream().filter(a -> a.commentCount() > 0).toList());
        log("PR list page " + page + ": total " + pullRequests.size());
        while (pullRequestsResponse.next() != null) {
            page++;
            pullRequestsResponse = get(decodeNext(pullRequestsResponse.next()), pullRequestType);
            pullRequests.addAll(pullRequestsResponse.values().stream().filter(a -> a.commentCount() > 0).toList());
            log("PR list page " + page + ": total " + pullRequests.size());
        }

        List<PullRequest> closedPullRequests = pullRequests.stream()
                .filter(pullRequest -> !PullRequestState.OPEN.equals(pullRequest.state()))
                .toList();
        log("Processing " + closedPullRequests.size() + " closed PRs ("
                + (pullRequests.size() - closedPullRequests.size()) + " OPEN skipped)");

        List<Comment> commentsTotal = new ArrayList<>();
        List<ReportItem> reportItems = new ArrayList<>();
        int index = 0;
        for (PullRequest pullRequest : closedPullRequests) {
            index++;
            Long id = pullRequest.id();
            String progress = "[" + index + "/" + closedPullRequests.size() + "]";
            log(progress + " PR " + id + " (" + pullRequest.state() + "): fetching comments and activity");
            try {
                List<Comment> comments = getCommentsForPullRequest(id);
                commentsTotal.addAll(comments);

                List<Activity> activityListForPullRequest =
                        getActivityListForPullRequest(id).stream().filter(activity -> ObjectUtils.anyNotNull(activity.approval(),
                                activity.update())).toList();
                Set<LocalDate> activityDates = getActivityDatesForPullRequest(activityListForPullRequest);

                Set<String> commitHashes = activityListForPullRequest.stream()
                        .map(Activity::update)
                        .filter(Objects::nonNull)
                        .map(Activity.Update::source)
                        .filter(Objects::nonNull)
                        .map(Activity.Source::commit)
                        .filter(Objects::nonNull)
                        .map(Activity.Commit::hash)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                Map<Long, List<Comment>> chains = buildChains(comments);
                int chainsCount = (int) comments.stream().filter(comment -> comment.parent() == null).count();
                Map<Tag, Integer> prStatistic = calculatePRStatistic(chains);
                String mergeDate = pullRequest.updated_on();
                int daysCountWithoutAnyActivity = calculateDaysCountWithoutAnyActivity(comments, activityDates);

                reportItems.add(new ReportItem(id,
                        convertDateSafe(pullRequest.created_on()),
                        convertDateSafe(mergeDate),
                        pullRequest.links().html().href(),
                        prStatistic,
                        daysCountWithoutAnyActivity,
                        commitHashes.size(),
                        comments.size(),
                        chainsCount));
                log(progress + " PR " + id + ": comments=" + comments.size() + ", commits=" + commitHashes.size());
            } catch (Exception e) {
                log(progress + " Failed on PR " + id + ": " + e.getMessage());
                throw e;
            }
        }

        log("Writing report to " + pathToExport);
        Report report = new Report(reportItems);
        report.save(pathToExport);
        log("Done. Report PRs: " + reportItems.size() + ", elapsed " + Duration.between(started, Instant.now()).toSeconds() + "s");
    }

    private Set<LocalDate> getActivityDatesForPullRequest(List<Activity> activityListForPullRequest) {
        return activityListForPullRequest.stream()
                .map(Activity::getDate)
                .map(this::convertDateInternal)
                .collect(Collectors.toSet());
    }

    private int calculateChainsCountWithoutTags(Map<Long, List<Comment>> chains) {
        int result = 0;
        for (List<Comment> chain : chains.values()) {
            int counter = 0;
            for (Comment comment : chain) {
                for (Tag tag : Tag.TAGS) {
                    if (isCommentContainsTag(comment, tag)) {
                        counter++;
                    }
                }
            }
            if (counter == 0) {
                result++;
            }
        }
        return result;
    }

    private int calculateDaysCountWithoutAnyActivity(List<Comment> comments, Set<LocalDate> activityDates) {
        Set<LocalDate> commentDates = comments.stream()
                .map(Comment::created_on)
                .map(this::convertDateInternal)
                .collect(Collectors.toSet());
        Set<LocalDate> localDates = Stream.concat(commentDates.stream(), activityDates.stream()).collect(Collectors.toSet());
        if (localDates.size() < 2) {
            return 0;
        }
        LocalDate minDate = localDates.stream().min(LocalDate::compareTo).orElse(null);
        LocalDate maxDate = localDates.stream().max(LocalDate::compareTo).orElse(null);
        long daysBetween = ChronoUnit.DAYS.between(minDate, maxDate);
        return (int) daysBetween - localDates.size() + 1;
    }

    private LocalDate convertDateInternal(String value) {
        ZonedDateTime origin = ZonedDateTime.parse(value);
        return origin.withZoneSameInstant(ZONE).toLocalDate();
    }

    private String convertDateSafe(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return convertDateInternal(value).format(dateTimeFormatter);
    }

    private Map<Tag, Integer> calculatePRStatistic(Map<Long, List<Comment>> commentChains) {
        Map<Tag, Integer> result = new EnumMap<>(Tag.class);
        commentChains.values().forEach(comments -> {
            boolean isEmpty = true;
            for (Tag tag : Tag.TAGS) {
                for (Comment comment : comments) {
                    if (isCommentContainsTag(comment, tag)) {
                        Integer previousValue = result.computeIfAbsent(tag, tag1 -> 0);
                        result.put(tag, previousValue + 1);
                        isEmpty = false;
                        break;
                    }
                }
            };
            if (isEmpty) {
                Integer previousValue = result.computeIfAbsent(Tag.DEFAULT, tag1 -> 0);
                result.put(Tag.DEFAULT, previousValue + 1);
            }
        });
        return result;
    }

    private boolean isCommentContainsTag(Comment comment, Tag tag) {
        return comment.content().raw().toLowerCase().contains("#" + tag.name().toLowerCase());
    }

    private List<Comment> getCommentsForPullRequest(Long pullRequestId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(serviceUrl + COMMENTS.formatted(pullRequestId));
        String uriString = builder
                .build(false)
                .toString();

        ParameterizedTypeReference<Response<Comment>> commentType = new ParameterizedTypeReference<>() {
        };
        Response<Comment> commentsResponse = get(uriString, commentType);
        List<Comment> comments = new ArrayList<>(commentsResponse.values());
        while (commentsResponse.next() != null) {
            commentsResponse = get(decodeNext(commentsResponse.next()), commentType);
            comments.addAll(commentsResponse.values());
        }
        return comments;
    }

    private List<Activity> getActivityListForPullRequest(Long pullRequestId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(serviceUrl + ACTIVITY.formatted(pullRequestId));
        String uriString = builder
                .build(false)
                .toString();

        ParameterizedTypeReference<SimpleResponse<Activity>> activityType = new ParameterizedTypeReference<>() {
        };
        SimpleResponse<Activity> activityResponse = get(uriString, activityType);
        List<Activity> activityList = new ArrayList<>(activityResponse.values());
        while (activityResponse.next() != null) {
            activityResponse = get(decodeNext(activityResponse.next()), activityType);
            activityList.addAll(activityResponse.values());
        }
        return activityList;
    }

    private Comment getSingleComment(Long pullRequestId, Long commentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(serviceUrl + SINGLE_COMMENT.formatted(pullRequestId, commentId));
        String uriString = builder
                .build(false)
                .toString();

        return get(uriString, new ParameterizedTypeReference<>() {
        });
    }

    private String decodeNext(String next) {
        return URLDecoder.decode(next, Charset.defaultCharset());
    }

    private <T> T get(String uri, ParameterizedTypeReference<T> type) {
        for (int attempt = 1; ; attempt++) {
            try {
                ResponseEntity<T> response = restTemplate.exchange(uri, HttpMethod.GET, null, type);
                return response.getBody();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= MAX_RATE_LIMIT_RETRIES) {
                    throw e;
                }
                Duration wait = resolveRetryAfter(e);
                log("429 Too Many Requests, waiting " + wait.toSeconds() + "s (attempt " + attempt
                        + "/" + MAX_RATE_LIMIT_RETRIES + ")");
                sleep(wait);
            }
        }
    }

    private Duration resolveRetryAfter(HttpClientErrorException.TooManyRequests e) {
        HttpHeaders headers = e.getResponseHeaders();
        if (headers == null) {
            return DEFAULT_RETRY_WAIT;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return DEFAULT_RETRY_WAIT;
        }
        retryAfter = retryAfter.trim();
        try {
            return capWait(Duration.ofSeconds(Long.parseLong(retryAfter)));
        } catch (NumberFormatException ignored) {
            try {
                Instant until = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration wait = Duration.between(Instant.now(), until);
                if (wait.isNegative() || wait.isZero()) {
                    return DEFAULT_RETRY_WAIT;
                }
                return capWait(wait);
            } catch (DateTimeParseException ignoredDate) {
                return DEFAULT_RETRY_WAIT;
            }
        }
    }

    private Duration capWait(Duration wait) {
        if (wait.compareTo(MAX_RETRY_WAIT) > 0) {
            return MAX_RETRY_WAIT;
        }
        return wait;
    }

    private void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting after 429", e);
        }
    }

    private Map<Long, List<Comment>> buildChains(List<Comment> comments) {
        List<Comment> sortedComments = comments.stream().sorted(Comparator.comparingLong(o -> Optional.ofNullable(o.parent())
                .map(Comment.Parent::id).orElse(0L))).toList();

        Map<Long, Long> idToParentId = new HashMap<>();
        for (Comment comment : comments) {
            Comment.Parent parent = comment.parent();
            idToParentId.put(comment.id(), parent != null ? parent.id() : null);
        }

        Map<Long, List<Comment>> result = new HashMap<>();
        sortedComments.forEach(comment -> {
            if (comment.parent() == null) {
                List<Comment> list = new ArrayList<>();
                list.add(comment);
                result.put(comment.id(), list);
            } else {
                Long level1ParentId = getLevel1ParentId(comment.id(), idToParentId);
                List<Comment> list = result.computeIfAbsent(level1ParentId, k -> new ArrayList<>());
                list.addLast(comment);
            }
        });
        return result;
    }

    private Long getLevel1ParentId(Long id, Map<Long, Long> idToParentId) {
        Long result = id;
        while (idToParentId.get(result) != null) {
            result = idToParentId.get(result);
        }
        return result;
    }

    private static void log(String message) {
        System.out.println(LocalTime.now().format(LOG_TIME) + " " + message);
    }
}
