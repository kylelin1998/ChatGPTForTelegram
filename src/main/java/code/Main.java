package code;

import code.config.Config;
import code.config.ConfigSettings;
import code.config.I18nEnum;
import code.config.RequestProxyConfig;
import code.handler.CommandsHandler;
import code.handler.Handler;
import code.handler.I18nHandle;
import code.handler.message.MessageHandle;
import code.handler.store.Store;
import code.repository.I18nTableRepository;
import code.repository.RecordTableRepository;
import code.util.ExceptionUtil;
import code.util.GPTUtil;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
public class Main {
    public static volatile CommandsHandler Bot = null;
    public static volatile ConfigSettings GlobalConfig = Config.readConfig();
    public static volatile code.repository.I18nTableRepository I18nTableRepository = new I18nTableRepository();
    public static volatile code.repository.RecordTableRepository RecordTableRepository = new RecordTableRepository();

    public static void main(String[] args) throws InterruptedException {
        Unirest
                .config()
                .enableCookieManagement(false)
        ;

        Store.init();
        new Thread(() -> {
            while (true) {
                try {
                    GlobalConfig = Config.readConfig();
                    GPTUtil.setToken(GlobalConfig.getGptToken());
                    Thread.sleep(5000);
                } catch (InterruptedException e) {}
            }
        }).start();

        log.info("Program is running");

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            new Thread(() -> {
                while (true) {
                    try {
                        if (null != Bot) {
                            MessageHandle.sendMessage(GlobalConfig.getBotAdminId(), I18nHandle.getText(GlobalConfig.getBotAdminId(), I18nEnum.BotStartSucceed) + I18nHandle.getText(GlobalConfig.getBotAdminId(), I18nEnum.CurrentVersion) + ": " + Config.MetaData.CurrentVersion, false);
                            break;
                        }

                        Thread.sleep(100);
                    } catch (InterruptedException e) {}
                }
                Handler.init();
            }).start();

            if (GlobalConfig.getOnProxy()) {
                Bot = new CommandsHandler(RequestProxyConfig.create().buildDefaultBotOptions());
            } else {
                Bot = new CommandsHandler();
            }

            botsApi.registerBot(Bot);
        } catch (TelegramApiException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
    }
}
