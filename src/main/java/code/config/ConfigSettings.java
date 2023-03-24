package code.config;

import lombok.Data;

@Data
public class ConfigSettings {

    private Boolean debug;

    private Boolean onProxy;
    private String proxyHost;
    private Integer proxyPort;

    private String botAdminId;
    private String[] permissionChatIdArray;

    private String botName;
    private String botToken;

    private String gptToken;

}
