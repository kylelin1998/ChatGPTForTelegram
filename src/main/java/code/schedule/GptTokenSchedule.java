package code.schedule;

import code.config.I18nEnum;
import code.eneity.GptTokenTableEntity;
import code.eneity.cons.GptTokenStatusEnum;
import code.eneity.cons.YesOrNoEnum;
import code.handler.I18nHandle;
import code.handler.message.MessageHandle;
import code.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static code.Main.GlobalConfig;
import static code.Main.GptTokenTableRepository;

@Slf4j
public class GptTokenSchedule {
    public static void init() {
        log.info("Gpt token schedule init...");

        ThreadUtil.newIntervalWithTryCatch(() -> {
            List<GptTokenTableEntity> entities = GptTokenTableRepository.selectListByStatus(GptTokenStatusEnum.Die);
            for (GptTokenTableEntity entity : entities) {
                if (null == entity.getSend() || !YesOrNoEnum.get(entity.getSend()).get().isBool()) {
                    String text = entity.getToken() + " " + I18nHandle.getText(GlobalConfig.getBotAdminId(), I18nEnum.Tip429);
                    Message message = MessageHandle.sendMessage(GlobalConfig.getBotAdminId(), text, false);
                    if (null != message) {
                        GptTokenTableRepository.dieAndSend(entity.getToken());
                    }
                }
            }
        }, 1, TimeUnit.MINUTES);
    }

}
