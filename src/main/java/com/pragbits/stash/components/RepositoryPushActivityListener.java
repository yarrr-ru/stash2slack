package com.pragbits.stash.components;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.event.RepositoryPushEvent;
import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageUtils;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.pragbits.stash.ColorCode;
import com.pragbits.stash.SlackGlobalSettingsService;
import com.pragbits.stash.SlackSettings;
import com.pragbits.stash.SlackSettingsService;
import com.pragbits.stash.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RepositoryPushActivityListener {
    static final String KEY_GLOBAL_SETTING_HOOK_URL = "stash2slack.globalsettings.hookurl";
    static final String KEY_GLOBAL_SLACK_CHANNEL_NAME = "stash2slack.globalsettings.channelname";
    private static final Logger log = LoggerFactory.getLogger(RepositoryPushActivityListener.class);

    private final SlackGlobalSettingsService slackGlobalSettingsService;
    private final SlackSettingsService slackSettingsService;
    private final CommitService commitService;
    private final NavBuilder navBuilder;
    private final SlackNotifier slackNotifier;
    private final Gson gson = new Gson();

    public RepositoryPushActivityListener(SlackGlobalSettingsService slackGlobalSettingsService,
                                          SlackSettingsService slackSettingsService,
                                          CommitService commitService,
                                          NavBuilder navBuilder,
                                          SlackNotifier slackNotifier) {
        this.slackGlobalSettingsService = slackGlobalSettingsService;
        this.slackSettingsService = slackSettingsService;
        this.commitService = commitService;
        this.navBuilder = navBuilder;
        this.slackNotifier = slackNotifier;
    }

    @EventListener
    public void NotifySlackChannel(RepositoryPushEvent event) {
        // find out if notification is enabled for this repo
        Repository repository = event.getRepository();
        SlackSettings slackSettings = slackSettingsService.getSlackSettings(repository);
        String globalHookUrl = slackGlobalSettingsService.getWebHookUrl(KEY_GLOBAL_SETTING_HOOK_URL);

        SettingsSelector settingsSelector = new SettingsSelector(slackSettingsService,  slackGlobalSettingsService, repository);
        SlackSettings resolvedSlackSettings = settingsSelector.getResolvedSlackSettings();

        if (resolvedSlackSettings.isSlackNotificationsEnabledForPush()) {
            String localHookUrl = slackSettings.getSlackWebHookUrl();
            WebHookSelector hookSelector = new WebHookSelector(globalHookUrl, localHookUrl);
            ChannelSelector channelSelector = new ChannelSelector(slackGlobalSettingsService.getChannelName(KEY_GLOBAL_SLACK_CHANNEL_NAME), slackSettings.getSlackChannelName());

            if (!hookSelector.isHookValid()) {
                log.error("There is no valid configured Web hook url! Reason: " + hookSelector.getProblem());
                return;
            }

            if (repository.isFork() && !resolvedSlackSettings.isSlackNotificationsEnabledForPersonal()) {
                // simply return silently when we don't want forks to get notifications unless they're explicitly enabled
                return;
            }

            String repoName = repository.getSlug();
            String projectName = repository.getProject().getKey();

            String repoPath = projectName + "/" + event.getRepository().getName();

            for (RefChange refChange : event.getRefChanges()) {
                String text;
                String ref = refChange.getRefId();
                NavBuilder.Repo repoUrlBuilder = navBuilder
                        .project(projectName)
                        .repo(repoName);
                String url = repoUrlBuilder
                        .commits()
                        .until(refChange.getRefId())
                        .buildAbsolute();

                List<Changeset> myChanges = new LinkedList<Changeset>();
                boolean isNewRef = refChange.getFromHash().equalsIgnoreCase("0000000000000000000000000000000000000000");
                boolean isDeleted = refChange.getToHash().equalsIgnoreCase("0000000000000000000000000000000000000000")
                    && refChange.getType() == RefChangeType.DELETE;
                if (isDeleted) {
                    // issue#4: if type is "DELETE" and toHash is all zero then this is a branch delete
                    if (ref.indexOf("refs/tags") >= 0) {
                        text = String.format("Tag `%s` deleted from repository <%s|`%s`>.",
                                ref.replace("refs/tags/", ""),
                                repoUrlBuilder.buildAbsolute(),
                                repoPath);
                    } else {
                        text = String.format("Branch `%s` deleted from repository <%s|`%s`>.",
                                ref.replace("refs/heads/", ""),
                                repoUrlBuilder.buildAbsolute(),
                                repoPath);
                    }
                } else if (isNewRef) {
                    // issue#3 if fromHash is all zero (meaning the beginning of everything, probably), then this push is probably
                    // a new branch or tag, and we want only to display the latest commit, not the entire history
                    Changeset latestChangeSet = commitService.getChangeset(repository, refChange.getToHash());
                    myChanges.add(latestChangeSet);
                    if (ref.indexOf("refs/tags") >= 0) {
                        text = String.format("Tag <%s|`%s`> pushed on <%s|`%s`>. See <%s|commit list>.",
                                url,
                                ref.replace("refs/tags/", ""),
                                repoUrlBuilder.buildAbsolute(),
                                repoPath,
                                url
                                );
                    } else {
                        text = String.format("Branch <%s|`%s`> pushed on <%s|`%s`>. See <%s|commit list>.",
                                url,
                                ref.replace("refs/heads/", ""),
                                repoUrlBuilder.buildAbsolute(),
                                repoPath,
                                url);
                    }
                } else {
                    ChangesetsBetweenRequest request = new ChangesetsBetweenRequest.Builder(repository)
                            .exclude(refChange.getFromHash())
                            .include(refChange.getToHash())
                            .build();

                    Page<Changeset> changeSets = commitService.getChangesetsBetween(
                            request, PageUtils.newRequest(0, PageRequest.MAX_PAGE_LIMIT));

                    myChanges.addAll(Lists.newArrayList(changeSets.getValues()));

                    int commitCount = myChanges.size();
                    String commitStr = commitCount == 1 ? "commit" : "commits";

                    String branch = ref.replace("refs/heads/", "");
                    text = String.format("Push on <%s|`%s`> branch <%s|`%s`> by `%s <%s>` (%d %s). See <%s|commit list>.",
                            repoUrlBuilder.buildAbsolute(),
                            repoPath,
                            url,
                            branch,
                            event.getUser() != null ? event.getUser().getDisplayName() : "unknown user",
                            event.getUser() != null ? event.getUser().getEmailAddress() : "unknown email",
                            commitCount, commitStr,
                            url);
                }

                // Figure out what type of change this is:


                SlackPayload payload = new SlackPayload();

                payload.setText(text);
                payload.setMrkdwn(true);

                switch (resolvedSlackSettings.getNotificationLevel()) {
                    case COMPACT:
                        compactCommitLog(event, refChange, payload, repoUrlBuilder, myChanges);
                        break;
                    case VERBOSE:
                        verboseCommitLog(event, refChange, payload, repoUrlBuilder, text, myChanges);
                        break;
                    case MINIMAL:
                    default:
                        break;
                }

                // slackSettings.getSlackChannelName might be:
                // - empty
                // - comma separated list of channel names, eg: #mych1, #mych2, #mych3

                if (channelSelector.getSelectedChannel().isEmpty()) {
                    slackNotifier.SendSlackNotification(hookSelector.getSelectedHook(), gson.toJson(payload));
                } else {
                    // send message to multiple channels
                    List<String> channels = Arrays.asList(channelSelector.getSelectedChannel().split("\\s*,\\s*"));
                    for (String channel: channels) {
                        payload.setChannel(channel.trim());
                        slackNotifier.SendSlackNotification(hookSelector.getSelectedHook(), gson.toJson(payload));
                    }
                }
            }
        }
    }

    private void compactCommitLog(RepositoryPushEvent event, RefChange refChange, SlackPayload payload, NavBuilder.Repo urlBuilder, List<Changeset> myChanges) {
        if (myChanges.size() == 0) {
            // If there are no commits, no reason to add anything
        }
        SlackAttachment commits = new SlackAttachment();
        commits.setColor(ColorCode.GRAY.getCode());
        // Since the branch is now in the main commit line, title is not needed
        //commits.setTitle(String.format("[%s:%s]", event.getRepository().getName(), refChange.getRefId().replace("refs/heads", "")));
        StringBuilder attachmentFallback = new StringBuilder();
        StringBuilder commitListBlock = new StringBuilder();
        for (Changeset ch : myChanges) {
            String commitUrl = urlBuilder.changeset(ch.getId()).buildAbsolute();
            String firstCommitMessageLine = ch.getMessage().split("\n")[0];

            // Note that we changed this to put everything in one attachment because otherwise it
            // doesn't get collapsed in slack (the see more... doesn't appear)
            commitListBlock.append(String.format("<%s|`%s`>: %s - _%s_\n",
                    commitUrl, ch.getDisplayId(), firstCommitMessageLine, ch.getAuthor().getName()));

            attachmentFallback.append(String.format("%s: %s\n", ch.getDisplayId(), firstCommitMessageLine));
        }
        commits.setText(commitListBlock.toString());
        commits.setFallback(attachmentFallback.toString());

        payload.addAttachment(commits);
    }

    private void verboseCommitLog(RepositoryPushEvent event, RefChange refChange, SlackPayload payload, NavBuilder.Repo urlBuilder, String text, List<Changeset> myChanges) {
        for (Changeset ch : myChanges) {
            SlackAttachment attachment = new SlackAttachment();
            attachment.setFallback(text);
            attachment.setColor(ColorCode.GRAY.getCode());
            SlackAttachmentField field = new SlackAttachmentField();

            attachment.setTitle(String.format("[%s:%s] - %s", event.getRepository().getName(), refChange.getRefId().replace("refs/heads", ""), ch.getId()));
            attachment.setTitle_link(urlBuilder.changeset(ch.getId()).buildAbsolute());

            field.setTitle("comment");
            field.setValue(ch.getMessage());
            field.setShort(false);
            attachment.addField(field);
            payload.addAttachment(attachment);
        }
    }

}
