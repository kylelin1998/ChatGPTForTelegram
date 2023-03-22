package code.util.gpt.parameter;

import code.util.gpt.GPTRole;
import lombok.Data;

@Data
public class GPTMessage {

    private String role;
    private String content;

}
