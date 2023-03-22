package code.util.gpt;

import lombok.Getter;
import lombok.ToString;

@Getter
public enum GPTRole {

    User("user"),
    System("system"),
    Assistant("assistant"),

    ;

    private String role;

    GPTRole(String role) {
        this.role = role;
    }

}
