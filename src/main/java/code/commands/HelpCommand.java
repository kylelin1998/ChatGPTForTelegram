package code.commands;

import code.config.I18nEnum;
import code.handler.I18nHandle;
import code.handler.message.MessageHandle;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import static code.Main.GlobalConfig;

@Slf4j
public class HelpCommand extends BotCommand {

    public HelpCommand() {
        super("help", "");
    }

    @Override
    public void processMessage(AbsSender absSender, Message message, String[] arguments) {
        execute(absSender, message, arguments);
    }

    public void execute(AbsSender absSender, Message message, String[] arguments) {
        String chatId = message.getChat().getId().toString();
        String fromId = String.valueOf(message.getFrom().getId());

        if (StringUtils.isBlank(GlobalConfig.getHelpText())) {
            MessageHandle.sendMessage(chatId, I18nHandle.getText(fromId, I18nEnum.HelpText), false);
        } else {
            MessageHandle.sendMessage(chatId, GlobalConfig.getHelpText(), false);
        }
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {
    }
}
