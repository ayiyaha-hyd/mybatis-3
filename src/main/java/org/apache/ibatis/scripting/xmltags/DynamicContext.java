/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
// 动态上下文
public class DynamicContext {
  // _parameter 的 key
  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  // _databaseId 的 key
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    // 设置 OGNL 属性访问器
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }
  // 上下文参数集合
  private final ContextMap bindings;
  private final StringJoiner sqlBuilder = new StringJoiner(" ");
  private int uniqueNumber = 0;

  public DynamicContext(Configuration configuration, Object parameterObject) {
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
      bindings = new ContextMap(metaObject, existsTypeHandler);
    } else {
      bindings = new ContextMap(null, false);
    }
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }
  // 往 bindings 属性添加 KV键值对
  public void bind(String name, Object value) {
    bindings.put(name, value);
  }
  // 将拼接的 sql 暂时存储到 sqlBuilder
  public void appendSql(String sql) {
    sqlBuilder.add(sql);
  }

  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  public int getUniqueNumber() {
    return uniqueNumber++;
  }
  // 上下文参数集合
  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;
    private final MetaObject parameterMetaObject;
    private final boolean fallbackParameterObject;

    public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
      this.parameterMetaObject = parameterMetaObject;
      this.fallbackParameterObject = fallbackParameterObject;
    }

    @Override
    public Object get(Object key) {
      // 如果有 key 对应的值, 直接获取
      String strKey = (String) key;
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }

      if (parameterMetaObject == null) {
        return null;
      }

      // 如果 fallbackParameterObject 标识为 true, 并且 parameterMetaObject 没有 getter 方法
      if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
        // 返回原始对象
        return parameterMetaObject.getOriginalObject();
      } else {
        // issue #61 do not modify the context when reading
        // 获取指定属性 key 的值
        return parameterMetaObject.getValue(strKey);
      }
    }
  }
  // 上下文访问器(实现了 ognl.PropertyAccessor 接口, 上下文访问器)
  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name) {
      Map map = (Map) target;
      // 先从 context 中获取
      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }
      // 再从 PARAMETER_OBJECT_KEY 对应的 map 中获取
      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}
