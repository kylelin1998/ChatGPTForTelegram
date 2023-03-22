package code.repository;

import code.config.Config;
import code.config.TableEnum;
import code.util.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;

@Slf4j
public class I18nTableRepository extends TableRepository {

    public I18nTableRepository() {
        super(Config.DBPath, TableEnum.I18nTable.getName());
    }

    @Override
    public String getCreateTableSql() {
        return String.format("create table if not exists %s (chat_id varchar(88), i18n_alias varchar(20))", super.getTableName());
    }

    public String selectI18nAlias(String chatId) {
        try {
            String i18nAlias = (String) execute((statement) -> {
                String sql = String.format("select i18n_alias from %s where chat_id = '%s'", super.getTableName(), chatId);
                ResultSet query = statement.executeQuery(sql);
                return query.getString("i18n_alias");
            });
            return i18nAlias;
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public synchronized boolean save(String chatId, String i18nAlias) {
        String sql = String.format("insert into %s values('%s', '%s')", super.getTableName(), chatId, i18nAlias);
        try {
            execute((statement) -> {
                statement.executeUpdate(String.format("delete from %s where chat_id = '%s'", super.getTableName(), chatId));
                statement.executeUpdate(sql);
                return null;
            });
            return true;
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
            return false;
        }
    }

}
