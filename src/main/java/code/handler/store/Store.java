package code.handler.store;

import code.schedule.GptTokenSchedule;

import static code.Main.GlobalConfig;

public class Store {

    public static void init() {
        ChatButtonsStore.set(GlobalConfig.getChatButtons());
        GptTokenSchedule.init();
    }

}
