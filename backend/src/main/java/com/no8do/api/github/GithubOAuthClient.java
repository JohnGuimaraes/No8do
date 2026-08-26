package com.no8do.api.github;

public interface GithubOAuthClient {

    String authorizationUrl(String state);

    GithubOAuthUser exchangeCode(String code);
}
