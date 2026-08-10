package com.example.salesmgmt.domain;

public record ImportIssue(
        IssueLevel level,
        String location,
        String message
) {
    public enum IssueLevel {
        WARNING,
        ERROR
    }
}
