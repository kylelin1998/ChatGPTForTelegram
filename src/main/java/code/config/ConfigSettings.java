package code.config;

import lombok.Data;

@Data
public class ConfigSettings {

    @ConfigField(isNotNull = true)
    private Boolean debug;

    @ConfigField(isNotNull = true)
    private Boolean onProxy;
    private String proxyHost;
    private Integer proxyPort;

    @ConfigField(isNotNull = true)
    private String botAdminId;
    private String[] permissionChatIdArray;

    @ConfigField(isNotNull = true)
    private String botName;
    @ConfigField(isNotNull = true)
    private String botToken;

    @ConfigField(isNotNull = true)
    private String gptToken;

}
