package code.config;

import lombok.Getter;

import java.util.Locale;

@Getter
public enum I18nLocaleEnum {
    ZH_CN(Locale.SIMPLIFIED_CHINESE, "zh-cn", "简体中文"),
    EN(Locale.US, "en", "English"),

    ;

    private Locale locale;
    private String alias;
    private String displayText;

    I18nLocaleEnum(Locale locale, String alias, String displayText) {
        this.locale = locale;
        this.alias = alias;
        this.displayText = displayText;
    }

    public static I18nLocaleEnum getI18nLocaleEnumByAlias(String alias) {
        for (I18nLocaleEnum value : I18nLocaleEnum.values()) {
            if (value.getAlias().equals(alias)) {
                return value;
            }
        }
        return null;
    }

}
