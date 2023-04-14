package code.eneity;

import code.repository.mapper.TableEntity;
import code.repository.mapper.TableField;
import code.repository.mapper.TableName;
import lombok.Data;

@TableName(name = "record_table")
@Data
public class RecordTableEntity implements TableEntity {

    @TableField(name = "id", sql = "id varchar(55) primary key")
    private String id;

    @TableField(name = "record_alias", sql = "record_alias varchar(50)")
    private String recordAlias;

    @TableField(name = "record_explains", sql = "record_explains varchar(255)")
    private String recordExplains;

    @TableField(name = "create_time", sql = "create_time timestamp")
    private Long createTime;
    @TableField(name = "update_time", sql = "update_time timestamp")
    private Long updateTime;

    @TableField(name = "chat_id", sql = "chat_id varchar(88)")
    private String chatId;

    @TableField(name = "chat_template_json", sql = "chat_template_json text")
    private String chatTemplateJson;

}
