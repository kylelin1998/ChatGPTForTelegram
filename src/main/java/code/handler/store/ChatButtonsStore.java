package code.handler.store;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;

public class ChatButtonsStore {

    private volatile static ChatButtonsToInlineKeyboardButtons buttons;

    public static void set(String chatButtons) {
        Optional<ChatButtonsToInlineKeyboardButtons> keyboardButtons = chatButtonsToInlineKeyboardButtons(chatButtons);
        buttons = keyboardButtons.orElse(null);
    }

    public static Optional<ChatButtonsToInlineKeyboardButtons> verify(String chatButtons) {
        return chatButtonsToInlineKeyboardButtons(chatButtons);
    }

    public static Optional<ChatButtonsToInlineKeyboardButtons> get() {
        return Optional.ofNullable(buttons);
    }

    @Data
    public static class ChatButtonsToInlineKeyboardButtons {
        private boolean isAll;

        private Map<String, List<InlineKeyboardButton>> map;

        public Optional<List<InlineKeyboardButton>> getButtons(String chatId) {
            if (isAll) {
                return Optional.ofNullable(map.get("all"));
            }
            for (Map.Entry<String, List<InlineKeyboardButton>> entry : map.entrySet()) {
                if (entry.getKey().equals(chatId)) {
                    return Optional.ofNullable(entry.getValue());
                }
            }
            return Optional.empty();
        }
    }
    private static Optional<ChatButtonsToInlineKeyboardButtons> chatButtonsToInlineKeyboardButtons(String chatButtons) {
        if (StringUtils.isBlank(chatButtons)) {
            return Optional.empty();
        }

        String[] split = chatButtons.split("---");
        if (split.length == 0) {
            return Optional.empty();
        }
        ChatButtonsToInlineKeyboardButtons chatButtonsToInlineKeyboardButtons = new ChatButtonsToInlineKeyboardButtons();
        Map<String, List<InlineKeyboardButton>> map = new LinkedHashMap<>();
        for (String s : split) {
            s = StringUtils.removeStart(s, "\n");
            List<InlineKeyboardButton> buttons = new ArrayList<>();
            String[] dataSplit = s.split("\n");
            if (dataSplit.length < 2) {
                return Optional.empty();
            }
            for (int i = 1; i < dataSplit.length; i++) {
                String data = dataSplit[i];
                if (StringUtils.isBlank(data)) {
                    return Optional.empty();
                }
                String[] buttonSplit = data.split(" ");
                if (buttonSplit.length != 2) {
                    return Optional.empty();
                }
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(buttonSplit[0]);
                button.setUrl(buttonSplit[1]);
                buttons.add(button);
            }

            String chatId = dataSplit[0];
            chatButtonsToInlineKeyboardButtons.setAll(chatId.equals("all"));
            map.put(chatId, buttons);
            if (chatButtonsToInlineKeyboardButtons.isAll()) {
                break;
            }
        }
        if (chatButtonsToInlineKeyboardButtons.isAll()) {
            List<String> deleteKeys = new ArrayList<>();
            for (Map.Entry<String, List<InlineKeyboardButton>> entry : map.entrySet()) {
                if (!entry.getKey().equals("all")) {
                    deleteKeys.add(entry.getKey());
                }
            }
            for (String key : deleteKeys) {
                map.remove(key);
            }
        }
        chatButtonsToInlineKeyboardButtons.setMap(map);

        return Optional.of(chatButtonsToInlineKeyboardButtons);
    }

}
