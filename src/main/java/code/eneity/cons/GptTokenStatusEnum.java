package code.eneity.cons;

import lombok.Getter;

import java.util.Optional;

@Getter
public enum GptTokenStatusEnum {

    Alive(1, "正常"),
    Die(2, "死亡"),

    ;

    private int num;
    private String name;
    GptTokenStatusEnum(int num, String name) {
        this.num = num;
        this.name = name;
    }

    public static Optional<GptTokenStatusEnum> get(int num) {
        for (GptTokenStatusEnum value : values()) {
            if (value.getNum() == num) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

}
