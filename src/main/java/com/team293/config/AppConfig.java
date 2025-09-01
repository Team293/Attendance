package com.team293.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    List<Groups> groups();
    List<String> singleMessageGroups();

    interface Groups {
        String slackId();
        int priority();
    }
}
