package com.pragbits.stash;

public class ImmutableSlackSettings implements SlackSettings {

    private final boolean slackNotificationsEnabled;
    private final boolean slackNotificationsOpenedEnabled;
    private final boolean slackNotificationsReopenedEnabled;
    private final boolean slackNotificationsUpdatedEnabled;
    private final boolean slackNotificationsApprovedEnabled;
    private final boolean slackNotificationsUnapprovedEnabled;
    private final boolean slackNotificationsDeclinedEnabled;
    private final boolean slackNotificationsMergedEnabled;
    private final boolean slackNotificationsCommentedEnabled;
    private final boolean slackNotificationsEnabledForPush;
    private final String slackChannelName;
    private final String slackWebHookUrl;

    public ImmutableSlackSettings(boolean slackNotificationsEnabled,
                                  boolean slackNotificationsOpenedEnabled,
                                  boolean slackNotificationsReopenedEnabled,
                                  boolean slackNotificationsUpdatedEnabled,
                                  boolean slackNotificationsApprovedEnabled,
                                  boolean slackNotificationsUnapprovedEnabled,
                                  boolean slackNotificationsDeclinedEnabled,
                                  boolean slackNotificationsMergedEnabled,
                                  boolean slackNotificationsCommentedEnabled,
                                  boolean slackNotificationsEnabledForPush,
                                  String slackChannelName,
                                  String slackWebHookUrl) {
        this.slackNotificationsEnabled = slackNotificationsEnabled;
        this.slackNotificationsOpenedEnabled = slackNotificationsOpenedEnabled;
        this.slackNotificationsReopenedEnabled = slackNotificationsReopenedEnabled;
        this.slackNotificationsUpdatedEnabled = slackNotificationsUpdatedEnabled;
        this.slackNotificationsApprovedEnabled = slackNotificationsApprovedEnabled;
        this.slackNotificationsUnapprovedEnabled = slackNotificationsUnapprovedEnabled;
        this.slackNotificationsDeclinedEnabled = slackNotificationsDeclinedEnabled;
        this.slackNotificationsMergedEnabled = slackNotificationsMergedEnabled;
        this.slackNotificationsCommentedEnabled = slackNotificationsCommentedEnabled;
        this.slackNotificationsEnabledForPush = slackNotificationsEnabledForPush;
        this.slackChannelName = slackChannelName;
        this.slackWebHookUrl = slackWebHookUrl;
    }

    public boolean isSlackNotificationsEnabled() {
        return slackNotificationsEnabled;
    }

    public boolean isSlackNotificationsOpenedEnabled() {
        return slackNotificationsOpenedEnabled;
    }

    public boolean isSlackNotificationsReopenedEnabled() {
        return slackNotificationsReopenedEnabled;
    }

    public boolean isSlackNotificationsUpdatedEnabled() {
        return slackNotificationsUpdatedEnabled;
    }

    public boolean isSlackNotificationsApprovedEnabled() {
        return slackNotificationsApprovedEnabled;
    }

    public boolean isSlackNotificationsUnapprovedEnabled() {
        return slackNotificationsUnapprovedEnabled;
    }

    public boolean isSlackNotificationsDeclinedEnabled() {
        return slackNotificationsDeclinedEnabled;
    }

    public boolean isSlackNotificationsMergedEnabled() {
        return slackNotificationsMergedEnabled;
    }

    public boolean isSlackNotificationsCommentedEnabled() {
        return slackNotificationsCommentedEnabled;
    }

    public boolean isSlackNotificationsEnabledForPush() {
        return slackNotificationsEnabledForPush;
    }

    public String getSlackChannelName() {
        return slackChannelName;
    }

    public String getSlackWebHookUrl() {
        return slackWebHookUrl;
    }

    @Override
    public String toString() {
        return "ImmutableSlackSettings {" + "slackNotificationsEnabled=" + slackNotificationsEnabled +
                ", slackNotificationsOpenedEnabled=" + slackNotificationsOpenedEnabled +
                ", slackNotificationsReopenedEnabled=" + slackNotificationsReopenedEnabled +
                ", slackNotificationsUpdatedEnabled=" + slackNotificationsUpdatedEnabled +
                ", slackNotificationsApprovedEnabled=" + slackNotificationsApprovedEnabled +
                ", slackNotificationsUnapprovedEnabled=" + slackNotificationsUnapprovedEnabled +
                ", slackNotificationsDeclinedEnabled=" + slackNotificationsDeclinedEnabled +
                ", slackNotificationsMergedEnabled=" + slackNotificationsMergedEnabled +
                ", slackNotificationsCommentedEnabled=" + slackNotificationsCommentedEnabled +
                ", slackNotificationsEnabledForPush=" + slackNotificationsEnabledForPush +
                ", slackChannelName=" + slackChannelName +
                ", slackWebHookUrl=" + slackWebHookUrl + "}";
    }

}
