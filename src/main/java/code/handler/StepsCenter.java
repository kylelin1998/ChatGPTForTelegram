package code.handler;

import code.config.ExecutorsConfig;
import code.handler.steps.StepsChatSession;
import code.handler.steps.StepsHandler;
import code.handler.steps.StepsRegisterCenter;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static code.Main.GlobalConfig;

public class StepsCenter {

    @Data
    public static class CallbackData {
        private String id;
        private Command command;
        private String text;
    }

    public static String buildCallbackData(StepsChatSession session, Command command, String text) {
        StringBuilder builder = new StringBuilder();
        builder.append("f[" + session.getSessionId() + "]");
        builder.append(command.getCmd());
        builder.append(" ");
        builder.append(text);
        return builder.toString();
    }
    public static CallbackData parseCallbackData(String callbackData) {
        CallbackData data = new CallbackData();
        data.setId(StringUtils.substringBetween(callbackData, "f[", "]"));

        String s = StringUtils.replace(callbackData, "f[" + data.getId() + "]", "");
        String[] arguments = s.split(" ");

        data.setCommand(Command.toCmd(arguments[0]));
        data.setText(arguments[1]);

        return data;
    }

    public static boolean cmdHandle(StepsChatSession session) {
        if (StringUtils.isNotBlank(session.getText()) && session.getText().startsWith("/")) {
            String s = StringUtils.remove(session.getText(), "/");
            String[] split = s.split(" ");
            if (split.length > 0) {
                String cmd = split[0];
                cmd = StringUtils.replace(cmd, "@" + GlobalConfig.getBotName(), "");
                if (Command.exist(cmd)) {
                    split[0] = cmd;
                    session.setText(Stream.of(split).skip(1).collect(Collectors.joining(" ")));
                    cmdHandle(
                            Command.toCmd(cmd),
                            false,
                            session
                    );
                    return true;
                }
            }
        }
        return false;
    }

    public static void cmdHandle(Command command, boolean isCall, StepsChatSession stepsChatSession) {
        boolean permission = false;
        for (String s : GlobalConfig.getPermissionChatIdArray()) {
            if (s.equals(stepsChatSession.getChatId()) || s.equals(stepsChatSession.getFromId())) {
                permission = true;
                break;
            }
        }
        if (!permission) {
            MessageHandle.sendMessage(stepsChatSession.getChatId(), stepsChatSession.getReplyToMessageId(), "你没有使用权限， 不过你可以自己搭建一个\nhttps://github.com/kylelin1998/ChatGPTForTelegram", false);
            return;
        }

        ExecutorsConfig.submit(() -> {
            StepsHandler handler = StepsRegisterCenter.getRegister(command.getCmd());
            if (null != handler.getInitStep() && !handler.hasInit(stepsChatSession) && !isCall) {
                handler.init(stepsChatSession);
            } else {
                handler.step(stepsChatSession);
            }
        });
    }

    public static void textHandle(StepsChatSession stepsChatSession) {
        StepsHandler handler = StepsRegisterCenter.getPriority(stepsChatSession);
        if (null == handler) {
            return;
        }
        ExecutorsConfig.submit(() -> {
            handler.step(stepsChatSession);
        });
    }

    public static void exit(StepsChatSession stepsChatSession) {
        Collection<StepsHandler> list = StepsRegisterCenter.getRegisterList();
        for (StepsHandler handler : list) {
            handler.exit(stepsChatSession);
        }
    }

}
