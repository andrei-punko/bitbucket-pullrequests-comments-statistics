package by.andd3dfx.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record Comment(Long id, String created_on, String updated_on, Content content, Parent parent, @JsonAlias("pullrequest") PullRequest pullRequest) {

    public record Content(String raw) {
    }

    public record Parent(Long id) {
    }

    public record PullRequest(Long id) {
    }
}
