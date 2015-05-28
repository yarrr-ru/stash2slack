package com.pragbits.stash;

public interface SlackSettings {

    boolean isSlackNotificationsEnabled();
    boolean isSlackNotificationsOpenedEnabled();
    boolean isSlackNotificationsReopenedEnabled();
    boolean isSlackNotificationsUpdatedEnabled();
    boolean isSlackNotificationsApprovedEnabled();
    boolean isSlackNotificationsUnapprovedEnabled();
    boolean isSlackNotificationsDeclinedEnabled();
    boolean isSlackNotificationsMergedEnabled();
    boolean isSlackNotificationsCommentedEnabled();
    boolean isSlackNotificationsEnabledForPush();
    String getSlackChannelName();
    String getSlackWebHookUrl();

}
