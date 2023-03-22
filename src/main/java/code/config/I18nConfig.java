package code.config;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class I18nConfig {

    private static Map<String, Map<String, String>> cacheMap = new LinkedHashMap<>();

    static {
        for (I18nLocaleEnum value : I18nLocaleEnum.values()) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/i18n", value.getLocale());
            HashMap<String, String> hashMap = new HashMap<>();
            for (String s : bundle.keySet()) {
                hashMap.put(s, bundle.getString(s));
            }

            cacheMap.put(value.getAlias(), hashMap);
        }
    }

    public static String getText(String i18nAlias, String key) {
        Map<String, String> map = cacheMap.get(StringUtils.isNotBlank(i18nAlias) ? i18nAlias : I18nLocaleEnum.ZH_CN.getAlias());
        return map.get(key);
    }

    public static String getText(String i18nAlias, I18nEnum i18nEnum) {
        Map<String, String> map = cacheMap.get(StringUtils.isNotBlank(i18nAlias) ? i18nAlias : I18nLocaleEnum.ZH_CN.getAlias());
        return map.get(i18nEnum.getKey());
    }

}
