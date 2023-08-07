package code.eneity.cons;

import lombok.Getter;

import java.util.Optional;

@Getter
public enum YesOrNoEnum {

    Yes(2, true),
    No(1, false),

    ;

    private int num;
    private boolean bool;

    YesOrNoEnum(int num, boolean bool) {
        this.num = num;
        this.bool = bool;
    }

    public static int toInt(boolean bool) {
        return bool ? Yes.getNum() : No.getNum();
    }

    public static Optional<Boolean> toBoolean(int num) {
        for (YesOrNoEnum value : values()) {
            if (num == value.getNum()) {
                return Optional.of(value.isBool());
            }
        }
        return Optional.empty();
    }

    public static Optional<YesOrNoEnum> get(int num) {
        for (YesOrNoEnum value : values()) {
            if (value.getNum() == num) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

}
