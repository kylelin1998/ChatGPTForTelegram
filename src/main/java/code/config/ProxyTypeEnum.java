package code.config;

import lombok.Getter;

@Getter
public enum ProxyTypeEnum {

    NotOpen(0, "Not Open"),
    HttpProxy(1, "Http Proxy"),

    ;

    private int type;
    private String alias;

    ProxyTypeEnum(int type, String alias) {
        this.type = type;
        this.alias = alias;
    }

    public static ProxyTypeEnum getDefault() {
        return NotOpen;
    }

}
