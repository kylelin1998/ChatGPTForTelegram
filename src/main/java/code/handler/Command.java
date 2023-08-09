package code.handler;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public enum Command {

    Chat("chat", false),
    ChatShorter("c", false),
    Ask("ask", false),
    AskShorter("a", false),
    ChatMsgLimit("cml", false),
    NoneOfContextChatMessage("nccm", false),
    Exit("exit", false),
    Admin("admin", false),
    Image("image", false),

    Record("record", false),
    Playback("p", false),
    PlaybackRegex("^[,，]", true),
    RecordList("record_list", false),
    GetRecord("get_record", false),
    DeleteRecord("delete_record", false),

    SetChatButtons("set_chat_buttons", false),
    SetVoiceStatus("set_voice_status", false),
    ChangeModel("change_model", false),
    SetOpenStatus("set_open_status", false),
    SetGptToken("set_gpt_token", false),
    SetConciseReplies("set_concise_replies", false),
    UpdateConfig("uc", false),
    Language("language", false),
    Restart("restart", false),
    Upgrade("upgrade", false),

    ;

    private String cmd;
    private boolean regex;

    Command(String cmd, boolean regex) {
        this.cmd = cmd;
        this.regex = regex;
    }

    public static Command toCmd(String cmd) {
        for (Command value : Command.values()) {
            if (value.getCmd().equals(cmd)) {
                return value;
            }
        }
        return null;
    }
    public static Command regexMatch(String cmd) {
        if (StringUtils.isBlank(cmd)) {
            return null;
        }

        for (Command value : values()) {
            if (value.isRegex()) {
                Pattern pattern = Pattern.compile(value.getCmd());
                Matcher matcher = pattern.matcher(cmd);
                if (matcher.find()) {
                    return value;
                }
            }
        }
        return null;
    }

    public static boolean exist(String cmd) {
        return null != toCmd(cmd);
    }

}
