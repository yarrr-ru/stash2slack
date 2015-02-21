package com.pragbits.stash;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.pragbits.stash.SlackSettings;
import com.pragbits.stash.SlackSettingsService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionValidationService;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultSlackSettingsService implements SlackSettingsService {

    static final ImmutableSlackSettings DEFAULT_CONFIG = new ImmutableSlackSettings(false, false, "", "");

    static final String KEY_SLACK_NOTIFICATION = "slackNotificationsEnabled";
    static final String KEY_SLACK_NOTIFICATION_PUSH = "slackNotificationsEnabledForPush";
    static final String KEY_SLACK_CHANNEL_NAME = "slackChannelName";
    static final String KEY_SLACK_WEBHOOK_URL = "slackWebHookUrl";

    private final PluginSettings pluginSettings;
    private final PermissionValidationService validationService;

    private final Cache<Integer, SlackSettings> cache = CacheBuilder.newBuilder().build(
            new CacheLoader<Integer, SlackSettings>() {
                @Override
                public SlackSettings load(@Nonnull Integer repositoryId) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> data = (Map) pluginSettings.get(repositoryId.toString());
                    return data == null ? DEFAULT_CONFIG : deserialize(data);
                }
            }
    );

    public DefaultSlackSettingsService(PluginSettingsFactory pluginSettingsFactory, PermissionValidationService validationService) {
        this.validationService = validationService;
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey(PluginMetadata.getPluginKey());
    }

    @Nonnull
    @Override
    public SlackSettings getSlackSettings(@Nonnull Repository repository) {
        validationService.validateForRepository(checkNotNull(repository, "repository"), Permission.REPO_READ);

        try {
            //noinspection ConstantConditions
            return cache.get(repository.getId());
        } catch (ExecutionException e) {
            throw Throwables.propagate(e.getCause());
        }
    }

    @Nonnull
    @Override
    public SlackSettings setSlackSettings(@Nonnull Repository repository, @Nonnull SlackSettings settings) {
        validationService.validateForRepository(checkNotNull(repository, "repository"), Permission.REPO_ADMIN);
        Map<String, String> data = serialize(checkNotNull(settings, "settings"));
        pluginSettings.put(Integer.toString(repository.getId()), data);
        cache.invalidate(repository.getId());

        return deserialize(data);
    }

    // note: for unknown reason, pluginSettngs.get() is not getting back the key for an empty string value
    // probably I don't know someyhing here. Applying a hack
    private Map<String, String> serialize(SlackSettings settings) {
        return ImmutableMap.of(
                KEY_SLACK_NOTIFICATION, Boolean.toString(settings.isSlackNotificationsEnabled()),
                KEY_SLACK_NOTIFICATION_PUSH, Boolean.toString(settings.isSlackNotificationsEnabledForPush()),
                KEY_SLACK_CHANNEL_NAME, settings.getSlackChannelName().isEmpty() ? " " : settings.getSlackChannelName(),
                KEY_SLACK_WEBHOOK_URL, settings.getSlackWebHookUrl().isEmpty() ? " " : settings.getSlackWebHookUrl()
        );
    }

    // note: for unknown reason, pluginSettngs.get() is not getting back the key for an empty string value
    // probably I don't know someyhing here. Applying a hack
    private SlackSettings deserialize(Map<String, String> settings) {
        return new ImmutableSlackSettings(
                Boolean.parseBoolean(settings.get(KEY_SLACK_NOTIFICATION)),
                Boolean.parseBoolean(settings.get(KEY_SLACK_NOTIFICATION_PUSH)),
                settings.get(KEY_SLACK_CHANNEL_NAME).toString().equals(" ") ? "" : settings.get(KEY_SLACK_CHANNEL_NAME).toString(),
                settings.get(KEY_SLACK_WEBHOOK_URL).toString().equals(" ") ? "" : settings.get(KEY_SLACK_WEBHOOK_URL).toString()
        );
    }

}
