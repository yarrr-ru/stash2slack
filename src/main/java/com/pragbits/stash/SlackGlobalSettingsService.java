package com.pragbits.stash;

public interface SlackGlobalSettingsService {
    String getWebHookUrl(String key);
    void setWebHookUrl(String key, String value);
}
