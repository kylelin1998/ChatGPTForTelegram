package code.eneity;

import code.repository.mapper.TableEntity;
import code.repository.mapper.TableField;
import code.repository.mapper.TableName;
import lombok.Data;

@TableName(name = "gpt_token_table")
@Data
public class GptTokenTableEntity implements TableEntity {
    @TableField(name = "token", sql = "token varchar(100) primary key")
    private String token;

    @TableField(name = "status", sql = "status int comment '1.正常 2.死亡'")
    private Integer status;

    @TableField(name = "send", sql = "send int comment '1.未发送 2.已发送'")
    private Integer send;

}
