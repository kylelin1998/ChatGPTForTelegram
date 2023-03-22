package code.commands;

import code.config.ExecutorsConfig;
import code.handler.StepsCentre;
import code.handler.Command;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ExitCommand extends BotCommand {
    public ExitCommand() {
        super("exit", "");
    }

    @Override
    public void processMessage(AbsSender absSender, Message message, String[] arguments) {
        execute(absSender, message, arguments);
    }

    public void execute(AbsSender absSender, Message message, String[] arguments) {
        ExecutorsConfig.submit(() -> {
            String chatId = String.valueOf(message.getChat().getId());
            String fromId = String.valueOf(message.getFrom().getId());

            StepsCentre.cmdHandle(Command.Exit, false, chatId, fromId, message, Stream.of(arguments).collect(Collectors.joining(" ")));
        });
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {

    }
}
