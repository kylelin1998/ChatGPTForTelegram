package code.repository.mapper;

import code.util.ExceptionUtil;
import code.util.SqliteUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.sqlite.jdbc4.JDBC4ResultSet;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class TableRepository<T extends TableEntity> {

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

    public TableRepository(String dbPath) {
        this.tableName = getT().getAnnotation(TableName.class).name();
        this.dbPath = dbPath;
        try {
            SqliteUtil.execute(dbPath, (statement) -> {
                String sql = SqlBuilder.buildCreateTableSql(getT());
                if (StringUtils.isNotBlank(sql)) {
                    statement.execute(sql);
                }
                return null;
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
    }

    private Class<T> getT() {
        ParameterizedType parameterizedType = (ParameterizedType) this.getClass().getGenericSuperclass();
        Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
        String typeName = actualTypeArgument.getTypeName();
        try {
            return (Class<T>) Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTableName() {
        return this.tableName;
    }
    public String sql(String sql) {
        return sql(sql, null);
    }
    public String sql(String sql, String[] args) {
        String table = StringUtils.replace(sql, "$table", this.getTableName());
        return String.format(table, args);
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

    public Boolean delete(T where) {
        try {
            return (boolean) execute((statement) -> {
                int update = statement.executeUpdate(SqlBuilder.buildDeleteSql(where));
                return update > 0;
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public Boolean update(T entity, T where) {
        try {
            return (boolean) execute((statement) -> {
                int update = statement.executeUpdate(SqlBuilder.buildUpdateSql(entity, where));
                return update > 0;
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public Boolean insert(T entity) {
        try {
            return (boolean) execute((statement) -> {
                int update = statement.executeUpdate(SqlBuilder.buildInsertSql(entity, false));
                return update > 0;
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public T getOne(ResultSet resultSet) throws SQLException, IllegalAccessException, InstantiationException {
        JDBC4ResultSet jdbc4ResultSet = (JDBC4ResultSet) resultSet;
        if (jdbc4ResultSet.emptyResultSet) {
            return null;
        }

        Class<T> t = getT();
        T instance = t.newInstance();
        for (Field field : t.getDeclaredFields()) {
            TableField tableField = field.getAnnotation(TableField.class);
            if (null == tableField) {
                continue;
            }
            field.setAccessible(true);
            field.set(instance, resultSet.getObject(tableField.name()));
        }
        return instance;
    }

    public List<T> getList(ResultSet resultSet) throws SQLException, IllegalAccessException, InstantiationException {
        List<T> list = new ArrayList<>();

        JDBC4ResultSet jdbc4ResultSet = (JDBC4ResultSet) resultSet;
        if (jdbc4ResultSet.emptyResultSet) {
            return list;
        }

        Class<T> t = getT();
        while (resultSet.next()) {
            T instance = t.newInstance();
            for (Field field : t.getDeclaredFields()) {
                TableField tableField = field.getAnnotation(TableField.class);
                if (null == tableField) {
                    continue;
                }
                field.setAccessible(true);
                field.set(instance, resultSet.getObject(tableField.name()));
            }
            list.add(instance);
        }
        return list;
    }

    public T selectOne(T where) {
        try {
            return (T) execute((statement) -> {
                ResultSet resultSet = statement.executeQuery(SqlBuilder.buildSelectSql(where));

                return getOne(resultSet);
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public List<T> selectList() {
        try {
            return (List<T>) execute((statement) -> {
                ResultSet resultSet = statement.executeQuery(SqlBuilder.buildSelectSql(getT()));

                return getList(resultSet);
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return new ArrayList<>();
    }

    public List<T> selectList(T where) {
        try {
            return (List<T>) execute((statement) -> {
                ResultSet resultSet = statement.executeQuery(SqlBuilder.buildSelectSql(where));

                return getList(resultSet);
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return new ArrayList<>();
    }

    public Integer selectCount() {
        Integer total = null;
        try {
            total = (int) execute((statement) -> {
                ResultSet query = statement.executeQuery(SqlBuilder.buildSelectCountSql(getT()));
                return query.getInt("total");
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return total;
    }

    public Integer selectCount(T where) {
        Integer total = null;
        try {
            total = (int) execute((statement) -> {
                ResultSet query = statement.executeQuery(SqlBuilder.buildSelectCountSql(where));
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

    public String getCreateTableSql() {
        return null;
    }

}
