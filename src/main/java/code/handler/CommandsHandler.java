package code.handler;

import code.commands.*;
import code.handler.steps.StepsSession;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static code.Main.GlobalConfig;

@Slf4j
public class CommandsHandler extends TelegramLongPollingCommandBot {

    public CommandsHandler() {
        super();
        start();
    }

    public CommandsHandler(DefaultBotOptions botOptions) {
        super(botOptions);
        start();
    }

    public void start() {
        register(new ChatCommand());
        register(new ChatShorterCommand());
        register(new ImageCommand());
        register(new ExitCommand());
        register(new LanguageCommand());
        register(new HelpCommand());
        register(new StartCommand());
    }

    @Override
    public String getBotUsername() {
        return GlobalConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return GlobalConfig.getBotToken();
    }

    @Override
    public void processNonCommandUpdate(Update update) {
        if (GlobalConfig.getDebug()) {
            log.info(JSON.toJSONString(update));
        }

        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (null != callbackQuery) {
            String chatId = String.valueOf(callbackQuery.getMessage().getChat().getId());
            String fromId = String.valueOf(callbackQuery.getFrom().getId());
            String data = callbackQuery.getData();
            StepsCentre.CallbackData callbackData = StepsCentre.parseCallbackData(data);

            if (!(StepsSession.buildNewChatId(chatId, fromId)).equals(String.valueOf(callbackData.getId()))) {
                return;
            }

            if (StringUtils.isNotBlank(data)) {
                StepsCentre.cmdHandle(callbackData.getCommand(), true, chatId, fromId, callbackQuery.getMessage(), callbackData.getText());
                return;
            }
        }

        Message message = update.getMessage();
        if (null == message) {
            return;
        }
        String chatId = message.getChat().getId().toString();
        String fromId = String.valueOf(message.getFrom().getId());

        String text = message.getText();
        if (StringUtils.isNotEmpty(text)) {
            boolean handle = StepsCentre.cmdHandle(chatId, fromId, message, text);
            if (!handle) {
                StepsCentre.textHandle(chatId, fromId, message, text);
            }
        }
    }

}
