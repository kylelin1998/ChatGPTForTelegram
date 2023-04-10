package code.handler.steps;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

public class StepsChatSessionBuilder {
    private StepsChatSession session;

    private StepsChatSessionBuilder(StepsChatSession session) {
        this.session = session;
    }

    public static StepsChatSessionBuilder clone(StepsChatSession session) {
        return create(session.getMessage());
    }
    public static StepsChatSessionBuilder create(CallbackQuery callbackQuery) {
        String chatId = String.valueOf(callbackQuery.getMessage().getChat().getId());
        String fromId = String.valueOf(callbackQuery.getFrom().getId());

        StepsChatSession session = new StepsChatSession();
        session.setChatId(chatId);
        session.setFromId(fromId);
        session.setSessionId(chatId + "_" + fromId);
        session.setCallbackQuery(callbackQuery);
        return new StepsChatSessionBuilder(session);
    }
    public static StepsChatSessionBuilder create(Message message) {
        String chatId = message.getChat().getId().toString();
        String fromId = String.valueOf(message.getFrom().getId());
        String text = message.getText();

        StepsChatSession session = new StepsChatSession();
        session.setChatId(chatId);
        session.setFromId(fromId);
        session.setSessionId(chatId + "_" + fromId);
        session.setText(text);
        session.setReplyToMessageId(message.getMessageId());
        session.setMessage(message);
        session.setVoice(message.getVoice());
        return new StepsChatSessionBuilder(session);
    }
    public StepsChatSessionBuilder setText(String text) {
        session.setText(text);
        return this;
    }

    public StepsChatSessionBuilder setText(String[] arguments) {
        session.setText(String.join(" ", arguments));
        return this;
    }

    public StepsChatSession build() {
        return session;
    }

}
