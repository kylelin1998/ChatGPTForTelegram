package code.handler.steps;

public class StepsSession {

    public static String buildNewChatId(String chatId, String fromId) {
        return chatId + "_" + fromId;
    }

}
