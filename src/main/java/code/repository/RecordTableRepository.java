package code.repository;

import code.config.Config;
import code.eneity.RecordTableEntity;
import code.repository.mapper.TableRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RecordTableRepository extends TableRepository<RecordTableEntity> {

    public RecordTableRepository() {
        super(Config.DBPath);
    }

    public List<RecordTableEntity> selectListByChatId(String chatId) {
        RecordTableEntity where = new RecordTableEntity();
        where.setChatId(chatId);
        return super.selectList(where);
    }

    public RecordTableEntity selectOne(String id, String chatId) {
        RecordTableEntity where = new RecordTableEntity();
        where.setId(id);
        where.setChatId(chatId);
        return super.selectOne(where);
    }

    public RecordTableEntity selectOneByAlias(String alias, String chatId) {
        RecordTableEntity where = new RecordTableEntity();
        where.setRecordAlias(alias);
        where.setChatId(chatId);
        return super.selectOne(where);
    }

    public Boolean delete(String id, String chatId) {
        RecordTableEntity where = new RecordTableEntity();
        where.setId(id);
        where.setChatId(chatId);
        return super.delete(where);
    }

    public Integer selectCount(String chatId, String alias) {
        RecordTableEntity where = new RecordTableEntity();
        where.setChatId(chatId);
        where.setRecordAlias(alias);
        return super.selectCount(where);
    }

}
