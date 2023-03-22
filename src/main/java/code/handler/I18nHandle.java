package code.handler;

import code.config.I18nConfig;
import code.config.I18nEnum;
import code.config.I18nLocaleEnum;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static code.Main.I18nTableRepository;

public class I18nHandle {

    private static Map<String, String> cacheMap = new HashMap<>();

    public static String getText(String chatId, String key) {
        return getText(chatId, key, null);
    }

    public static String getText(String chatId, String key, Object... args) {
        String alias = cacheMap.get(chatId);
        if (StringUtils.isBlank(alias)) {
            alias = I18nTableRepository.selectI18nAlias(chatId);
            cacheMap.put(chatId, alias);
        }

        String text = I18nConfig.getText(alias, key);
        if (null != args && args.length > 0) {
            return String.format(text, args);
        }
        return text;
    }

    public static String getText(String chatId, I18nEnum i18nEnum) {
        return getText(chatId, i18nEnum, null);
    }
    public static String getText(String chatId, I18nEnum i18nEnum, Object... args) {
        String alias = cacheMap.get(chatId);
        if (StringUtils.isBlank(alias)) {
            alias = I18nTableRepository.selectI18nAlias(chatId);
            cacheMap.put(chatId, alias);
        }

        String text = I18nConfig.getText(alias, i18nEnum);
        if (null != args && args.length > 0) {
            return String.format(text, args);
        }
        return text;
    }

    public static void save(String chatId, I18nLocaleEnum i18nLocaleEnum) {
        I18nTableRepository.save(chatId, i18nLocaleEnum.getAlias());
        cacheMap.put(chatId, i18nLocaleEnum.getAlias());
    }

}
