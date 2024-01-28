package com.hyd.ayiyaha.interceptor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

@Intercepts({@Signature(
  type = Executor.class,
  method = "query",
  args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
), @Signature(
  type = Executor.class,
  method = "query",
  args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}
), @Signature(
  type = Executor.class,
  method = "update",
  args = {MappedStatement.class, Object.class}
)})
public class PerformanceInterceptor implements Interceptor {
  private static final Logger log = LoggerFactory.getLogger(PerformanceInterceptor.class);
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public PerformanceInterceptor() {
  }

  public Object intercept(Invocation invocation) throws Throwable {
    MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
    Object parameterObject = null;
    if (invocation.getArgs().length > 1) {
      parameterObject = invocation.getArgs()[1];
    }

    String statementId = mappedStatement.getId();
    BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
    Configuration configuration = mappedStatement.getConfiguration();
    String sql = this.getSql(boundSql, parameterObject, configuration);
    long start = System.currentTimeMillis();
    Object result = invocation.proceed();
    long end = System.currentTimeMillis();
    long timing = end - start;
    log.info("耗时:{}ms - {}: {}", timing, statementId, sql);
    return result;
  }

  public Object plugin(Object target) {
    return target instanceof Executor ? Plugin.wrap(target, this) : target;
  }

  public void setProperties(Properties properties) {
  }

  private String getSql(BoundSql boundSql, Object parameterObject, Configuration configuration) {
    String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    if (parameterMappings != null) {
      for (ParameterMapping mapping : parameterMappings) {
        if (mapping.getMode() != ParameterMode.OUT) {
          String propertyName = mapping.getProperty();
          Object value;
          if (boundSql.hasAdditionalParameter(propertyName)) {
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (parameterObject == null) {
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            value = parameterObject;
          } else {
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }

          sql = this.replacePlaceholder(sql, value);
        }
      }
    }

    return sql;
  }

  private String replacePlaceholder(String sql, Object propertyValue) {
    String result;
    if (propertyValue != null) {
      if (propertyValue instanceof String) {
        result = "'" + propertyValue + "'";
      } else if (propertyValue instanceof Date) {
        result = "'" + ((Date)propertyValue).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_FORMAT) + "'";
      } else {
        result = propertyValue.toString();
      }
    } else {
      result = "null";
    }

    result = Matcher.quoteReplacement(result);
    return sql.replaceFirst("\\?", result);
  }
}
