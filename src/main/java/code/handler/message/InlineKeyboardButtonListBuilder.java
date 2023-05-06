package code.handler.message;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class InlineKeyboardButtonListBuilder {
    private List<List<InlineKeyboardButton>> keyboard;
    private InlineKeyboardButtonListBuilder() {}

    public static InlineKeyboardButtonListBuilder create() {
        InlineKeyboardButtonListBuilder builder = new InlineKeyboardButtonListBuilder();
        builder.keyboard = new ArrayList<>();
        return builder;
    }

    public InlineKeyboardButtonListBuilder add(List<InlineKeyboardButton> inlineKeyboardButtonList) {
        this.keyboard.add(inlineKeyboardButtonList);
        return this;
    }

    public List<List<InlineKeyboardButton>> build() {
        return keyboard;
    }

}
