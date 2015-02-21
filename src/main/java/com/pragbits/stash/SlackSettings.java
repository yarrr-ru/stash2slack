package com.pragbits.stash;

public interface SlackSettings {

    boolean isSlackNotificationsEnabled();
    boolean isSlackNotificationsEnabledForPush();
    String getSlackChannelName();
    String getSlackWebHookUrl();

}
