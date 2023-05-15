package code.config;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ConfigSettings {

    @ConfigField(isNotNull = true)
    private Boolean debug;

    @JSONField(name = "on_proxy")
    @ConfigField(isNotNull = true)
    private Boolean onProxy;
    @JSONField(name = "proxy_host")
    private String proxyHost;
    @JSONField(name = "proxy_port")
    private Integer proxyPort;

    @JSONField(name = "bot_admin_id")
    @ConfigField(isNotNull = true)
    private String botAdminId;

    @ConfigField(isNotNull = true)
    private Boolean voice;

    @ConfigField(isNotNull = true)
    private Boolean open;
    @JSONField(name = "permission_chat_id_array")
    private String[] permissionChatIdArray;
    @JSONField(name = "block_chat_id_array")
    private String[] blockChatIdArray;

    @JSONField(name = "bot_name")
    @ConfigField(isNotNull = true)
    private String botName;
    @JSONField(name = "bot_token")
    @ConfigField(isNotNull = true)
    private String botToken;

    @JSONField(name = "gpt_token")
    @ConfigField(isNotNull = true)
    private String gptToken;
    @JSONField(name = "gpt_model")
    @ConfigField(isNotNull = true)
    private String gptModel;

    @JSONField(name = "chat_buttons")
    @ConfigField(isNotNull = false)
    private String chatButtons;

}
