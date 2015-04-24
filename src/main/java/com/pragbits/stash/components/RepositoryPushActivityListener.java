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
import com.pragbits.stash.SlackGlobalSettingsService;
import com.pragbits.stash.SlackSettings;
import com.pragbits.stash.SlackSettingsService;
import com.pragbits.stash.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class RepositoryPushActivityListener {
    static final String KEY_GLOBAL_SETTING_HOOK_URL = "stash2slack.globalsettings.hookurl";
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

        if (slackSettings.isSlackNotificationsEnabledForPush()) {
            String localHookUrl = slackSettings.getSlackWebHookUrl();
            WebHookSelector hookSelector = new WebHookSelector(globalHookUrl, localHookUrl);

            if (!hookSelector.isHookValid()) {
                log.error("There is no valid configured Web hook url! Reason: " + hookSelector.getProblem());
                return;
            }

            String repoName = repository.getSlug();
            String projectName = repository.getProject().getKey();

            for (RefChange refChange : event.getRefChanges()) {
                SlackPayload payload = new SlackPayload();
                if (!slackSettings.getSlackChannelName().isEmpty()) {
                    payload.setChannel(slackSettings.getSlackChannelName());
                }


                String url = navBuilder
                        .project(projectName)
                        .repo(repoName)
                        .commits()
                        .buildAbsolute();

                String text = String.format("Push on `%s` by `%s <%s>`. See <%s|commit list>.",
                        event.getRepository().getName(),
                        event.getUser() != null ? event.getUser().getDisplayName() : "unknown user",
                        event.getUser() != null ? event.getUser().getEmailAddress() : "unknown email",
                        url);

                if (refChange.getToHash().equalsIgnoreCase("0000000000000000000000000000000000000000") && refChange.getType() == RefChangeType.DELETE) {
                    // issue#4: if type is "DELETE" and toHash is all zero then this is a branch delete
                    text = String.format("`%s` deleted by `%s <%s>`.",
                            refChange.getRefId(),
                            event.getUser() != null ? event.getUser().getDisplayName() : "unknown user",
                            event.getUser() != null ? event.getUser().getEmailAddress() : "unknown email");
                }

                payload.setText(text);
                payload.setMrkdwn(true);

                List<Changeset> myChanges = new LinkedList<Changeset>();
                if (refChange.getFromHash().equalsIgnoreCase("0000000000000000000000000000000000000000")) {
                    // issue#3 if fromHash is all zero (meaning the beginning of everything, probably), then this push is probably
                    // a new branch, and we want only to display the latest commit, not the entire history
                    Changeset latestChangeSet = commitService.getChangeset(repository, refChange.getToHash());
                    myChanges.add(latestChangeSet);
                } else {
                    ChangesetsBetweenRequest request = new ChangesetsBetweenRequest.Builder(repository)
                            .exclude(refChange.getFromHash())
                            .include(refChange.getToHash())
                            .build();

                    Page<Changeset> changeSets = commitService.getChangesetsBetween(
                            request, PageUtils.newRequest(0, PageRequest.MAX_PAGE_LIMIT));

                    myChanges.addAll(Lists.newArrayList(changeSets.getValues()));
                }

                for (Changeset ch : myChanges) {
                    SlackAttachment attachment = new SlackAttachment();
                    attachment.setFallback(text);
                    attachment.setColor("#aabbcc");
                    SlackAttachmentField field = new SlackAttachmentField();

                    attachment.setTitle(String.format("[%s:%s] - %s", event.getRepository().getName(), refChange.getRefId(), ch.getId()));
                    attachment.setTitle_link(url.concat(String.format("/%s", ch.getId())));

                    field.setTitle(String.format("comment: %s", ch.getMessage()));
                    field.setShort(false);
                    attachment.addField(field);
                    payload.addAttachment(attachment);
                }

                slackNotifier.SendSlackNotification(hookSelector.getSelectedHook(), gson.toJson(payload));
            }

        }

    }
}
