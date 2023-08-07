package code.handler.store;

import code.config.I18nEnum;
import code.eneity.GptTokenTableEntity;
import code.eneity.cons.GptTokenStatusEnum;
import code.handler.I18nHandle;
import code.util.gpt.response.GPTChatResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static code.Main.GlobalConfig;
import static code.Main.GptTokenTableRepository;

public class GptTokenStore {
    public static String getRandomToken() {
        GptTokenTableEntity entity = GptTokenTableRepository.selectOneByRand(GptTokenStatusEnum.Alive);
        return null == entity ? GlobalConfig.getGptToken() : entity.getToken();
    }
    public static String getListText(String chatId) {
        StringBuilder builder = new StringBuilder();
        List<GptTokenTableEntity> gptTokenTableEntities = GptTokenTableRepository.selectListByStatus(GptTokenStatusEnum.Alive);
        builder.append(I18nHandle.getText(chatId, I18nEnum.Alive) + ": " + gptTokenTableEntities.size());
        builder.append("\n");
        for (GptTokenTableEntity entity : gptTokenTableEntities) {
            builder.append(entity.getToken());
            builder.append("\n");
        }
        builder.append("\n");
        List<GptTokenTableEntity> gptTokenTableEntities2 = GptTokenTableRepository.selectListByStatus(GptTokenStatusEnum.Die);
        builder.append(I18nHandle.getText(chatId, I18nEnum.Die) + ": " + gptTokenTableEntities2.size());
        builder.append("\n");
        for (GptTokenTableEntity entity : gptTokenTableEntities2) {
            builder.append(entity.getToken());
            builder.append("\n");
        }

        return builder.toString();
    }
    public static void forceSave(List<String> tokens) {
        GptTokenTableRepository.forceSave(tokens);
    }
    public static void deleteAll() {
        GptTokenTableRepository.deleteAll();
    }
    public static void handle(String token, GPTChatResponse response) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        if (null == response || response.isOk()) {
            return;
        }
        if (response.getStatusCode() == 429) {
            if (response.getResponse().contains("You exceeded your current quota, please check your plan and billing details")) {
                GptTokenTableRepository.die(token);
            }
        } else if (response.getStatusCode() == 401) {
            GptTokenTableRepository.die(token);
        }
    }

}
