package code.handler;

import lombok.Getter;

@Getter
public enum Command {

    Chat("chat"),
    ChatShorter("c"),
    Ask("ask"),
    AskShorter("a"),
    ChatMsgLimit("cml"),
    NoneOfContextChatMessage("nccm"),
    Exit("exit"),
    Admin("admin"),
    Image("image"),

    Record("record"),
    Playback("p"),
    RecordList("record_list"),
    GetRecord("get_record"),
    DeleteRecord("delete_record"),

    SetChatButtons("set_chat_buttons"),
    SetVoiceStatus("set_voice_status"),
    ChangeModel("change_model"),
    SetOpenStatus("set_open_status"),
    UpdateConfig("uc"),
    Language("language"),
    Restart("restart"),
    Upgrade("upgrade"),

    ;

    private String cmd;

    Command(String cmd) {
        this.cmd = cmd;
    }

    public static Command toCmd(String cmd) {
        for (Command value : Command.values()) {
            if (value.getCmd().equals(cmd)) {
                return value;
            }
        }
        return null;
    }

    public static boolean exist(String cmd) {
        return null != toCmd(cmd);
    }

}
