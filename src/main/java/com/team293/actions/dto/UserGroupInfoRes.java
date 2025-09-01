package com.team293.actions.dto;

public record UserGroupInfoRes(
        int priority,
        String slackId,
        int maxMembers
) { }
