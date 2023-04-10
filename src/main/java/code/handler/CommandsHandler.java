package code.handler;

import code.commands.*;
import code.handler.steps.StepsChatSession;
import code.handler.steps.StepsChatSessionBuilder;
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

        register(new AskCommand());
        register(new AskShorterCommand());

        register(new ChatMsgLimitCommand());
        register(new NoneContextChatCommand());

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
            String data = callbackQuery.getData();
            StepsCenter.CallbackData callbackData = StepsCenter.parseCallbackData(data);
            if (null == callbackData) {
                MessageHandle.sendMessage(String.valueOf(callbackQuery.getMessage().getChatId()), "Error...", false);
                return;
            }

            StepsChatSession session = StepsChatSessionBuilder
                    .create(callbackQuery)
                    .setText(callbackData.getText())
                    .build();

            if (!session.getFromId().equals(String.valueOf(callbackData.getFromId()))) {
                return;
            }

            if (StringUtils.isNotBlank(data)) {
                StepsCenter.cmdHandle(callbackData, session);
                return;
            }
        }

        Message message = update.getMessage();
        if (null == message) {
            return;
        }
        if (StringUtils.isNotEmpty(message.getText()) || null != message.getVoice()) {
            boolean handle = StepsCenter.cmdHandle(StepsChatSessionBuilder.create(message).build());
            if (!handle) {
                StepsCenter.messageHandle(StepsChatSessionBuilder.create(message).build());
            }
        }
    }

}
