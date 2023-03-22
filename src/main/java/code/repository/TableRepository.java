package code.repository;

import code.util.ExceptionUtil;
import code.util.SqliteUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class TableRepository {

    private String tableName;
    private String dbPath;

    public TableRepository(String dbPath, String tableName) {
        this.tableName = tableName;
        this.dbPath = dbPath;
        try {
            SqliteUtil.execute(dbPath, (statement) -> {
                String sql = getCreateTableSql();
                if (StringUtils.isNotBlank(sql)) {
                    statement.execute(sql);
                }
                return null;
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
    }

    public String getTableName() {
        return this.tableName;
    }

    public Object execute(SqliteUtil.SqliteInterface sqliteInterface) throws Exception {
        return SqliteUtil.execute(this.dbPath, sqliteInterface);
    }
    public Object executeWithTryCatch(SqliteUtil.SqliteInterface sqliteInterface) {
        try {
            return SqliteUtil.execute(this.dbPath, sqliteInterface);
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public Integer selectCount() {
        Integer total = null;
        try {
            total = (int) execute((statement) -> {
                String sql = String.format("select count(*) as total from %s", this.tableName);
                ResultSet query = statement.executeQuery(sql);
                return query.getInt("total");
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return total;
    }

    public Integer selectCount(String field, String value) {
        Integer total = null;
        try {
            total = (int) execute((statement) -> {
                String sql = String.format("select count(*) as total from %s where %s = '%s'", this.tableName, field, value);
                ResultSet query = statement.executeQuery(sql);
                return query.getInt("total");
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return total;
    }

    public Boolean exist(String field, String value) {
        Integer total = null;
        try {
            total = (int) execute((statement) -> {
                String sql = String.format("select count(*) as total from %s where %s = '%s'", this.tableName, field, value);
                ResultSet query = statement.executeQuery(sql);
                return query.getInt("total");
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        if (null == total) {
            return null;
        }
        return total > 0;
    }

    public String sql(String sql, Object... args) {
        sql = StringUtils.replace(sql, "#{table}", getTableName());

        List<Object> list = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof String) {
                list.add(sqliteEscape((String) arg));
                continue;
            }
            list.add(arg);
        }
        String format = String.format(sql, list.toArray());
        return format;
    }

    private String sqliteEscape(String keyWord) {
        keyWord = keyWord.replace("'", "''");
        return keyWord;
    }

    public abstract String getCreateTableSql();

}
