package code.handler.message;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class InlineKeyboardButtonBuilder {

    private List<InlineKeyboardButton> inlineKeyboardButtonList;

    private String callbackData;

    private InlineKeyboardButtonBuilder() {}

    public static InlineKeyboardButtonBuilder create() {
        InlineKeyboardButtonBuilder builder = new InlineKeyboardButtonBuilder();
        builder.inlineKeyboardButtonList = new ArrayList<>();
        return builder;
    }

    public InlineKeyboardButtonBuilder setCallbackData(String callbackData) {
        this.callbackData = callbackData;
        return this;
    }

    public InlineKeyboardButtonBuilder add(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        inlineKeyboardButtonList.add(button);
        return this;
    }

    public InlineKeyboardButtonBuilder add(String text) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(this.callbackData);
        inlineKeyboardButtonList.add(button);
        return this;
    }

    public InlineKeyboardButtonBuilder add(InlineKeyboardButton button) {
        inlineKeyboardButtonList.add(button);
        return this;
    }

    public List<InlineKeyboardButton> build() {
        return inlineKeyboardButtonList;
    }

}
