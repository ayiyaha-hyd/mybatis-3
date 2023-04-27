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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lru (least recently used) cache decorator.
 *
 * @author Clinton Begin
 */
// 基于LRU(最近最少使用)淘汰机制的缓存装饰器
public class LruCache implements Cache {
  // Cache (缓存委托)
  private final Cache delegate;
  // 基于 LinkedHashMap 实现淘汰机制(数组+链表[双向链表])
  private Map<Object, Object> keyMap;
  // 最近最少使用的 key(要被淘汰的 key)
  private Object eldestKey;
  // 默认方法
  public LruCache(Cache delegate) {
    this.delegate = delegate;
    // 初始化 keyMap(缓存上限默认1024)
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }
  // 当前缓存大小
  @Override
  public int getSize() {
    return delegate.getSize();
  }
  // 设置缓存上限
  public void setSize(final int size) {
    // 基于 LinkedHashMap 实现淘汰机制(数组+链表[双向链表])
    // accessOrder 为 true, 指定排序方式, 使LinkedHashMap 基于访问顺序进行排序
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;
      // LinkedHashMap 默认 removeEldestEntry 不移除最老的元素, 此处重写, 按我们的规则, 满足一定条件之后移除最少最受用元素
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        // 当前 size 大于给定值, 移除元素
        boolean tooBig = size() > size;
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }
  // 添加
  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    // 循环 key 列表(刷新方法, 满足条件时, 移除最近最少使用的缓存)
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    // 刷新 keyMap 的访问顺序(重要)
    // get -> afterNodeAccess(根据accessOrder对key按照访问顺序排序)
    keyMap.get(key); //touch
    return delegate.getObject(key);
  }
  // 移除缓存(key的淘汰只在 put 的时候做处理)
  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }
  // 清空(缓存+keyMap)
  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }
  // 循环 key 列表(刷新方法, 满足条件时, 移除最近最少使用的缓存)
  private void cycleKeyList(Object key) {
    // 此处如果 keyMap 达到上限, 自动移除要被淘汰的 key
    // put -> afterNodeInsertion -> removeEldestEntry(插入后做移除)
    keyMap.put(key, key);
    if (eldestKey != null) {
      // 如果 eldestKey 不为空, 说明该 key 的缓存需要被淘汰, 做删除处理
      delegate.removeObject(eldestKey);
      // 标记清空
      eldestKey = null;
    }
  }

}
