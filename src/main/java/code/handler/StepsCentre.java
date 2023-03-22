package code.handler;

import code.config.ExecutorsConfig;
import code.handler.steps.StepsHandler;
import code.handler.steps.StepsRegisterCenter;
import code.handler.steps.StepsSession;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static code.Main.GlobalConfig;

public class StepsCentre {

    @Data
    public static class CallbackData {
        private String id;
        private Command command;
        private String text;
    }

    public static String buildCallbackData(String chatId, String fromId, Command command, String text) {
        StringBuilder builder = new StringBuilder();
        builder.append("f[" + StepsSession.buildNewChatId(chatId, fromId) + "]");
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

    public static boolean cmdHandle(String chatId, String fromId, Message message, String text) {
        if (StringUtils.isNotBlank(text) && text.startsWith("/")) {
            String s = StringUtils.remove(text, "/");
            String[] split = s.split(" ");
            if (split.length > 0) {
                String cmd = split[0];
                cmd = StringUtils.replace(cmd, "@" + GlobalConfig.getBotName(), "");
                if (Command.exist(cmd)) {
                    split[0] = cmd;
                    cmdHandle(Command.toCmd(cmd), false, chatId, fromId, message, Stream.of(split).skip(1).collect(Collectors.joining(" ")));
                    return true;
                }
            }
        }
        return false;
    }

    public static void cmdHandle(Command command, boolean isCall, String chatId, String fromId, Message message, String text) {
        boolean permission = false;
        for (String s : GlobalConfig.getPermissionChatIdArray()) {
            if (s.equals(chatId) || s.equals(fromId)) {
                permission = true;
                break;
            }
        }
        if (!permission) {
            MessageHandle.sendMessage(chatId, message.getMessageId(), "程序目前内测中， 不进行开放！", false);
            return;
        }

        ExecutorsConfig.submit(() -> {
            StepsHandler handler = StepsRegisterCenter.getRegister(command.getCmd());
            if (null != handler.getInitStep() && !handler.hasInit(chatId, fromId) && !isCall) {
                handler.init(chatId, fromId, message.getMessageId(), text);
            } else {
                handler.step(chatId, fromId, message.getMessageId(), text);
            }
        });
    }

    public static void textHandle(String chatId, String fromId, Message message, String text) {
        StepsHandler handler = StepsRegisterCenter.getPriority(chatId, fromId);
        if (null == handler) {
            return;
        }
        ExecutorsConfig.submit(() -> {
            handler.step(chatId, fromId, message.getMessageId(), text);
        });
    }

    public static void exit(String chatId, String fromId) {
        Collection<StepsHandler> list = StepsRegisterCenter.getRegisterList();
        for (StepsHandler handler : list) {
            handler.exit(chatId, fromId);
        }
    }

}
