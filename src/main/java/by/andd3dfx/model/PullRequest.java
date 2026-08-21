package by.andd3dfx.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PullRequest(Long id, @JsonAlias("comment_count") Long commentCount, String created_on, String updated_on, Links links,
                          PullRequestState state) {

    public record Links(Html html) {
    }

    public record Html(String href) {
    }

}
