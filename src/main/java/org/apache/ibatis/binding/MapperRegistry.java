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
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
// mapper 注册表(注册模式,映射器注册表)
public class MapperRegistry {

  private final Configuration config;
  // 已知的 mapper (映射器)
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    // 获取 MapperProxyFactory 代理对象 Class
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      // 获取 MapperProxyFactory 代理对象实例
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }
  // 判断是否有 mapper
  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }
  // 添加 mapper
  public <T> void addMapper(Class<T> type) {
    // 判断是否是接口
    if (type.isInterface()) {
      // 判断 mapper 是否已经存在
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        // 添加到 knownMappers
        knownMappers.put(type, new MapperProxyFactory<>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        // 解析 mapper 注解配置
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        // 解析
        parser.parse();
        loadCompleted = true;
      } finally {
        // 加载失败则移除
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * @since 3.2.2
   */
  // 扫描指定包下指定类,添加 mappers
  public void addMappers(String packageName, Class<?> superType) {
    // 通过 ResolverUtil 工具类,扫描出包下符合条件的类
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    // 遍历
    for (Class<?> mapperClass : mapperSet) {
      // 添加 mapper
      addMapper(mapperClass);
    }
  }

  /**
   * @since 3.2.2
   */
  // 扫描指定包,添加 mappers
  public void addMappers(String packageName) {
    // 扫描 Object 类型(即扫描所有类)
    addMappers(packageName, Object.class);
  }

}
