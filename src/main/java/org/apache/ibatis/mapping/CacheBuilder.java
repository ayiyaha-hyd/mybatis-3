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
package org.apache.ibatis.mapping;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.cache.decorators.BlockingCache;
import org.apache.ibatis.cache.decorators.LoggingCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.ScheduledCache;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.decorators.SynchronizedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * @author Clinton Begin
 */
// 缓存构建器
public class CacheBuilder {
  // xml namespace, 即对应的 mapper 全限定名
  private final String id;
  private Class<? extends Cache> implementation;
  private final List<Class<? extends Cache>> decorators;
  // 缓存容量
  private Integer size;
  // 清除间隔
  private Long clearInterval;
  // 读写(是否支持序列化)
  private boolean readWrite;
  private Properties properties;
  // 是否阻塞获取
  private boolean blocking;

  public CacheBuilder(String id) {
    this.id = id;
    this.decorators = new ArrayList<>();
  }

  public CacheBuilder implementation(Class<? extends Cache> implementation) {
    this.implementation = implementation;
    return this;
  }

  public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
    if (decorator != null) {
      this.decorators.add(decorator);
    }
    return this;
  }

  public CacheBuilder size(Integer size) {
    this.size = size;
    return this;
  }

  public CacheBuilder clearInterval(Long clearInterval) {
    this.clearInterval = clearInterval;
    return this;
  }

  public CacheBuilder readWrite(boolean readWrite) {
    this.readWrite = readWrite;
    return this;
  }

  public CacheBuilder blocking(boolean blocking) {
    this.blocking = blocking;
    return this;
  }

  public CacheBuilder properties(Properties properties) {
    this.properties = properties;
    return this;
  }
  // 构建缓存
  public Cache build() {
    // 设置 Cache implementation 和 decorators 属性
    setDefaultImplementations();
    // 创建基础 Cache 实例对象
    Cache cache = newBaseCacheInstance(implementation, id);
    // 设置 Cache 属性
    setCacheProperties(cache);
    // issue #352, do not apply decorators to custom caches
    // 只有 PerpetualCache 才会设置装饰器, 如果是自定义的 Cache (CustomCache), 则不处理
    if (PerpetualCache.class.equals(cache.getClass())) {
      for (Class<? extends Cache> decorator : decorators) {
        // 创建 Cache 装饰器实例
        cache = newCacheDecoratorInstance(decorator, cache);
        // 属性赋值
        setCacheProperties(cache);
      }
      // 设置装饰器的相关属性
      cache = setStandardDecorators(cache);
    } else if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
      // 如果没有进行过日志包装, 则进行日志包装
      cache = new LoggingCache(cache);
    }
    return cache;
  }
  // 设置 Cache implementation (实现类) 属性
  private void setDefaultImplementations() {
    if (implementation == null) {
      // 默认为永久缓存 PerpetualCache
      implementation = PerpetualCache.class;
      if (decorators.isEmpty()) {
        // 默认缓存装饰器为 LRU(最近最少使用)淘汰机制 装饰器
        decorators.add(LruCache.class);
      }
    }
  }
  // 设置装饰器的相关属性
  private Cache setStandardDecorators(Cache cache) {
    try {
      // 获取 Cache 的 MetaObject, 反射赋值
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      if (size != null && metaCache.hasSetter("size")) {
        // 反射设置 缓存容量大小
        metaCache.setValue("size", size);
      }
      // 如果 clearInterval (清除间隔) 属性存在值
      if (clearInterval != null) {
        // 包装为 ScheduledCache
        cache = new ScheduledCache(cache);
        // 设置清除间隔
        ((ScheduledCache) cache).setClearInterval(clearInterval);
      }
      if (readWrite) {
        // 包装为 SerializedCache, 支持序列化/反序列化
        cache = new SerializedCache(cache);
      }
      // 日志包装
      cache = new LoggingCache(cache);
      // 同步操作包装
      cache = new SynchronizedCache(cache);
      if (blocking) {
        // 阻塞包装
        cache = new BlockingCache(cache);
      }
      return cache;
    } catch (Exception e) {
      throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
    }
  }
  // 读取 properties, 设置 Cache 属性
  private void setCacheProperties(Cache cache) {
    if (properties != null) {
      // 获取 Cache 对应的 MetaObject
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      // 遍历 properties, 对 Cache 进行属性赋值
      for (Map.Entry<Object, Object> entry : properties.entrySet()) {
        // 获取 property key 和 value
        String name = (String) entry.getKey();
        String value = (String) entry.getValue();
        // 判断 Cache 是否有该属性 (name) 的 setter 方法
        if (metaCache.hasSetter(name)) {
          // 获取 setter 方法的形参类型
          Class<?> type = metaCache.getSetterType(name);
          // if-else 逐个判断, 七个基本数据类型(除了 char) + String 类型, 符合则进行类型转换并赋值
          if (String.class == type) {
            metaCache.setValue(name, value);
          } else if (int.class == type
              || Integer.class == type) {
            metaCache.setValue(name, Integer.valueOf(value));
          } else if (long.class == type
              || Long.class == type) {
            metaCache.setValue(name, Long.valueOf(value));
          } else if (short.class == type
              || Short.class == type) {
            metaCache.setValue(name, Short.valueOf(value));
          } else if (byte.class == type
              || Byte.class == type) {
            metaCache.setValue(name, Byte.valueOf(value));
          } else if (float.class == type
              || Float.class == type) {
            metaCache.setValue(name, Float.valueOf(value));
          } else if (boolean.class == type
              || Boolean.class == type) {
            metaCache.setValue(name, Boolean.valueOf(value));
          } else if (double.class == type
              || Double.class == type) {
            metaCache.setValue(name, Double.valueOf(value));
          } else {
            // setterType 不支持(不在上述8个类型里边), 则抛异常
            throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
          }
        }
      }
    }
    // 如果 cache 是 InitializingObject 子类(即此 Cache 需要执行初始化操作 initialize)
    if (InitializingObject.class.isAssignableFrom(cache.getClass())) {
      try {
        // 执行初始化
        ((InitializingObject) cache).initialize();
      } catch (Exception e) {
        throw new CacheException("Failed cache initialization for '"
          + cache.getId() + "' on '" + cache.getClass().getName() + "'", e);
      }
    }
  }
  // 创建基础 Cache 实例
  private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
    // 获取构造器
    Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
    try {
      // 创建实例
      return cacheConstructor.newInstance(id);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
    }
  }
  // 获取基础 Cache 构造器
  private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(String.class);
    } catch (Exception e) {
      throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  "
        + "Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e, e);
    }
  }
  // 创建 Cache 装饰器的实例
  private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
    // 获取构造器
    Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(base);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
    }
  }
  // 获取 Cache 装饰器的构造器
  private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(Cache.class);
    } catch (Exception e) {
      throw new CacheException("Invalid cache decorator (" + cacheClass + ").  "
        + "Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
    }
  }
}
