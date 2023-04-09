package code.util.gpt.response;

import lombok.Data;

@Data
public class GPTChatResponse {
    private boolean ok;

    private int statusCode;

    private String content;

    private String response;

}
