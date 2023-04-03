package code.commands;

import code.handler.StepsCenter;
import code.handler.Command;
import code.handler.steps.StepsChatSessionBuilder;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ChatCommand extends BotCommand {
    public ChatCommand() {
        super(Command.Chat.getCmd(), "");
    }

    @Override
    public void processMessage(AbsSender absSender, Message message, String[] arguments) {
        execute(absSender, message, arguments);
    }

    public void execute(AbsSender absSender, Message message, String[] arguments) {
        StepsCenter.cmdHandle(Command.Chat, StepsChatSessionBuilder.create(message).setText(arguments).build());
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {

    }
}
