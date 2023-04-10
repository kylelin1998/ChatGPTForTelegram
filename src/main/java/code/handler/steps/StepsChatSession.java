package code.handler.steps;

import lombok.Data;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Voice;

@Data
public class StepsChatSession {

    private String sessionId;
    private String chatId;
    private String fromId;

    private Integer replyToMessageId;

    private Message message;
    private CallbackQuery callbackQuery;
    private String text;
    private Voice voice;

}
