package com.pragbits.stash.components;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.event.pull.PullRequestActivityEvent;
import com.atlassian.stash.event.pull.PullRequestCommentActivityEvent;
import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.repository.Repository;
import com.google.gson.Gson;
import com.pragbits.stash.SlackGlobalSettingsService;
import com.pragbits.stash.SlackSettings;
import com.pragbits.stash.SlackSettingsService;
import com.pragbits.stash.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PullRequestActivityListener {
    static final String KEY_GLOBAL_SETTING_HOOK_URL = "stash2slack.globalsettings.hookurl";
    private static final Logger log = LoggerFactory.getLogger(PullRequestActivityListener.class);

    private final SlackGlobalSettingsService slackGlobalSettingsService;
    private final SlackSettingsService slackSettingsService;
    private final NavBuilder navBuilder;
    private final SlackNotifier slackNotifier;
    private final Gson gson = new Gson();


    public PullRequestActivityListener(SlackGlobalSettingsService slackGlobalSettingsService,
                                             SlackSettingsService slackSettingsService,
                                             NavBuilder navBuilder,
                                             SlackNotifier slackNotifier) {
        this.slackGlobalSettingsService = slackGlobalSettingsService;
        this.slackSettingsService = slackSettingsService;
        this.navBuilder = navBuilder;
        this.slackNotifier = slackNotifier;
    }

    @EventListener
    public void NotifySlackChannel(PullRequestActivityEvent event) {
        // find out if notification is enabled for this repo
        Repository repository = event.getPullRequest().getToRef().getRepository();
        SlackSettings slackSettings = slackSettingsService.getSlackSettings(repository);
        String globalHookUrl = slackGlobalSettingsService.getWebHookUrl(KEY_GLOBAL_SETTING_HOOK_URL);

        if (slackSettings.isSlackNotificationsEnabled()) {

            String localHookUrl = slackSettings.getSlackWebHookUrl();
            WebHookSelector hookSelector = new WebHookSelector(globalHookUrl, localHookUrl);

            if (!hookSelector.isHookValid()) {
                log.error("There is no valid configured Web hook url! Reason: " + hookSelector.getProblem());
                return;
            }

            String repoName = repository.getSlug();
            String projectName = repository.getProject().getKey();
            long pullRequestId = event.getPullRequest().getId();
            String userName = event.getUser() != null ? event.getUser().getDisplayName() : "unknown user";
            String activity = event.getActivity().getAction().name();

            // Ignore RESCOPED PR events
            if (activity.equalsIgnoreCase("RESCOPED")) {
                return;
            }

            String url = navBuilder
                    .project(projectName)
                    .repo(repoName)
                    .pullRequest(pullRequestId)
                    .overview()
                    .buildAbsolute();

            String text = String.format("Pull request event: `%s`, activity: `%s` by `%s`. <%s|See details>",
                    event.getPullRequest().getTitle(),
                    activity,
                    userName,
                    url);

            SlackPayload payload = new SlackPayload();
            if (!slackSettings.getSlackChannelName().isEmpty()) {
                payload.setChannel(slackSettings.getSlackChannelName());
            }
            payload.setText(text);
            payload.setMrkdwn(true);

            SlackAttachment attachment = new SlackAttachment();
            attachment.setFallback(text);
            //attachment.setPretext(String.format(""));
            attachment.setColor("#aabbcc");

            SlackAttachmentField field = new SlackAttachmentField();
            field.setTitle("Event details");

            switch (event.getActivity().getAction()) {
                case OPENED:
                    field.setValue(String.format("*%s* OPENED the pull request: `%s`", userName,  event.getPullRequest().getTitle()));
                    attachment.setColor("#2267c4"); // blue
                    break;
                case DECLINED:
                    field.setValue(String.format("*%s* DECLINED the pull request", userName));
                    attachment.setColor("#ff0024"); // red
                    break;
                case APPROVED:
                    field.setValue(String.format("*%s* APPROVED the pull request", userName));
                    attachment.setColor("#2dc422"); // green
                    break;
                case MERGED:
                    field.setValue(String.format("*%s* MERGED the pull request", userName));
                    attachment.setColor("#2dc422"); // green
                    break;
                case REOPENED:
                    field.setValue(String.format("*%s* REOPENED the pull request", userName));
                    attachment.setColor("#2267c4"); // blue
                    break;
                case RESCOPED:
                    field.setValue(String.format("*%s* RESCOPED the pull request", userName));
                    attachment.setColor("#9055fc"); // purple
                    break;
                case UNAPPROVED:
                    field.setValue(String.format("*%s* UNAPPROVED the pull request", userName));
                    attachment.setColor("#ff0024"); // red
                    break;
            }

            if (event instanceof PullRequestCommentActivityEvent) {
                field.setValue(String.format("*%s* commented the pull request: `%s`",
                        userName,
                        ((PullRequestCommentActivityEvent)event).getActivity().getComment().getText()));
            }

            field.setShort(false);
            attachment.addField(field);
            payload.addAttachment(attachment);
            String jsonPayload = gson.toJson(payload);

            slackNotifier.SendSlackNotification(hookSelector.getSelectedHook(), jsonPayload);
        }

    }
}
