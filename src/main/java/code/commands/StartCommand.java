package code.commands;

import code.config.I18nEnum;
import code.handler.I18nHandle;
import code.handler.MessageHandle;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

@Slf4j
public class StartCommand extends BotCommand {

    public StartCommand() {
        super("start", "");
    }

    @Override
    public void processMessage(AbsSender absSender, Message message, String[] arguments) {
        execute(absSender, message, arguments);
    }

    public void execute(AbsSender absSender, Message message, String[] arguments) {
        String chatId = message.getChat().getId().toString();
        String fromId = String.valueOf(message.getFrom().getId());
        MessageHandle.sendMessage(chatId, I18nHandle.getText(fromId, I18nEnum.HelpText), false);
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {

    }
}
