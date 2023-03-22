package code.util.gpt.response;

import lombok.Data;

import java.util.List;

@Data
public class GPTCreateImageResponse {

    private boolean ok;

    private Long created;

    private List<GPTCreateImage> data;

}
