package code.repository.mapper;

import code.util.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.StringJoiner;

@Slf4j
public class SqlBuilder {

    public static String getTableName(TableEntity tableEntity) {
        return getTableName(tableEntity.getClass());
    }
    public static String getTableName(Class<? extends TableEntity> tableClass) {
        return tableClass.getAnnotation(TableName.class).name();
    }

    public static String buildCreateTableSql(Class<? extends TableEntity> tableClass) {
        String tableName = getTableName(tableClass);
        StringJoiner joiner = new StringJoiner(", ", "(", ")");

        for (Field field : tableClass.getDeclaredFields()) {
            TableField tableField = field.getAnnotation(TableField.class);
            if (null == tableField) {
                continue;
            }
            joiner.add(tableField.sql());
        }

        String sql = "create table if not exists " + tableName + " " + joiner;
        return sql;
    }

    private static String buildFieldValueSql(TableEntity tableEntity, String delimiter, String prefix, String suffix) {
        Class<? extends TableEntity> entityClass = tableEntity.getClass();
        StringJoiner joiner = new StringJoiner(delimiter, prefix, suffix);
        for (Field field : entityClass.getDeclaredFields()) {
            try {
                TableField tableField = field.getAnnotation(TableField.class);
                if (null == tableField) {
                    continue;
                }
                field.setAccessible(true);
                Object o = field.get(tableEntity);
                if (null == o) {
                    continue;
                }

                if (o instanceof String) {
                    joiner.add(tableField.name() + "=" + "'" + o + "'");
                } else {
                    joiner.add(tableField.name() + "=" + o);
                }
            } catch (IllegalAccessException e) {
                log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
            }
        }
        return joiner.toString();
    }

    public static String buildFieldSql(Class<? extends TableEntity> tableClass, String prefix, String suffix) {
        StringJoiner joiner = new StringJoiner(", ", prefix, suffix);

        for (Field field : tableClass.getDeclaredFields()) {
            TableField tableField = field.getAnnotation(TableField.class);
            if (null == tableField) {
                continue;
            }
            joiner.add(tableField.name());
        }
        return joiner.toString();
    }
    public static String buildFieldSql(TableEntity tableEntity, String prefix, String suffix) {
        StringJoiner joiner = new StringJoiner(", ", prefix, suffix);

        Class<? extends TableEntity> entityClass = tableEntity.getClass();
        for (Field field : entityClass.getDeclaredFields()) {
            TableField tableField = field.getAnnotation(TableField.class);
            if (null == tableField) {
                continue;
            }
            joiner.add(tableField.name());
        }
        return joiner.toString();
    }

    public static String buildWhereSql(TableEntity tableEntity) {
        return buildFieldValueSql(tableEntity, " and ", " where ", "");
    }

    public static String buildSelectSql(Class<? extends TableEntity> tableClass) {
        String tableName = getTableName(tableClass);

        String sql = "select" + buildFieldSql(tableClass, " ", " ") + "from " + tableName;
        return sql;
    }
    public static String buildSelectSql(TableEntity tableEntity) {
        String tableName = getTableName(tableEntity);

        String sql = "select" + buildFieldSql(tableEntity, " ", " ") + "from " + tableName + buildWhereSql(tableEntity);
        return sql;
    }

    public static String buildDeleteSql(TableEntity where) {
        String tableName = getTableName(where);

        String sql = "delete from " + tableName + buildWhereSql(where);
        return sql;
    }

    public static String buildSelectCountSql(Class<? extends TableEntity> tableClass) {
        String tableName = getTableName(tableClass);

        String sql = "select count(*) as total from " + tableName;
        return sql;
    }

    public static String buildSelectCountSql(TableEntity where) {
        String tableName = getTableName(where);

        String sql = "select count(*) as total from " + tableName + buildWhereSql(where);
        return sql;
    }

    public static String buildInsertSql(TableEntity tableEntity, boolean isForceInsertNullValue) {
        String tableName = getTableName(tableEntity);

        Class<? extends TableEntity> entityClass = tableEntity.getClass();
        StringJoiner prefixJoiner = new StringJoiner(", ", "(", ")");
        StringJoiner suffixJoiner = new StringJoiner(", ", "values (", ")");
        for (Field field : entityClass.getDeclaredFields()) {
            try {
                TableField tableField = field.getAnnotation(TableField.class);
                if (null == tableField) {
                    continue;
                }
                field.setAccessible(true);
                Object o = field.get(tableEntity);
                if (null == o && !isForceInsertNullValue) {
                    continue;
                }
                prefixJoiner.add(tableField.name());
                if (null == o) {
                    suffixJoiner.add(null);
                }
                else if (o instanceof String) {
                    suffixJoiner.add("'" + o + "'");
                } else {
                    suffixJoiner.add(String.valueOf(o));
                }
            } catch (IllegalAccessException e) {
                log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
            }
        }

        String sql = "insert into " + tableName + " " + prefixJoiner + " " + suffixJoiner;
        return sql;
    }

    public static String buildUpdateSql(TableEntity tableEntity, TableEntity where) {
        String tableName = getTableName(tableEntity);

        String sql = "update " + tableName + buildFieldValueSql(tableEntity, ", ", " set ", "") + buildWhereSql(where);
        return sql;
    }

}
