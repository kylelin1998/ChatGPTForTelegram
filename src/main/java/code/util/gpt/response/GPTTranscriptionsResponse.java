package code.util.gpt.response;

import lombok.Data;

import java.util.List;

@Data
public class GPTTranscriptionsResponse {

    private boolean ok;

    private String text;

}
