package code.commands;

import code.config.ExecutorsConfig;
import code.handler.MessageHandle;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

@Slf4j
public class HelpCommand extends BotCommand {

    public static String HelpText = "2";

    public HelpCommand() {
        super("help", "");
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {
        String chatId = String.valueOf(chat.getId());
        MessageHandle.sendMessage(chatId, HelpText, false);
    }
}
