package code.commands;

import code.handler.Command;
import code.handler.StepsCentre;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class LanguageCommand extends BotCommand {
    public LanguageCommand() {
        super("language", "");
    }

    @Override
    public void processMessage(AbsSender absSender, Message message, String[] arguments) {
        execute(absSender, message, arguments);
    }

    public void execute(AbsSender absSender, Message message, String[] arguments) {
        String chatId = String.valueOf(message.getChat().getId());
        String fromId = String.valueOf(message.getFrom().getId());

        StepsCentre.cmdHandle(Command.Language, false, chatId, fromId, message, Stream.of(arguments).collect(Collectors.joining(" ")));
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {

    }
}
