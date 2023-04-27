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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.LinkedList;

/**
 * Weak Reference cache decorator.
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
// 弱引用缓存装饰器
public class WeakCache implements Cache {
  // 避免垃圾回收的硬链接(先进先出,队首添加, 队尾移除)
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  // 垃圾回收条目队列(cache value 已被 GC 回收, 需要清除的键)
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  // Cache 缓存委托
  private final Cache delegate;
  // 硬链接数量(强引用队列上限)
  private int numberOfHardLinks;

  public WeakCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }
  // 获取缓存大小
  @Override
  public int getSize() {
    // 移除已经被 GC 回收的 WeakEntry
    removeGarbageCollectedItems();
    return delegate.getSize();
  }
  // 设置强引用队列的上限
  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }
  // 添加
  @Override
  public void putObject(Object key, Object value) {
    // 移除已经被 GC 回收的 WeakEntry
    removeGarbageCollectedItems();
    // 添加缓存(包装为 WeakEntry)
    delegate.putObject(key, new WeakEntry(key, value, queueOfGarbageCollectedEntries));
  }
  // 获取
  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
    // 获取 WeakReference 对象
    WeakReference<Object> weakReference = (WeakReference<Object>) delegate.getObject(key);
    if (weakReference != null) {
      // 获取值
      result = weakReference.get();
      if (result == null) {// 如果值为空(说明该对象已被 GC 回收)
        delegate.removeObject(key);
      } else {// 值不为空
        // 添加到强引用队列(先进先出淘汰机制,队首添加队尾移除)
        hardLinksToAvoidGarbageCollection.addFirst(result);
        if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
          hardLinksToAvoidGarbageCollection.removeLast();
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    // 移除已经被 GC 回收的 WeakEntry
    removeGarbageCollectedItems();
    // 移除 对应缓存数据
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    // 清除 hardLinksToAvoidGarbageCollection
    hardLinksToAvoidGarbageCollection.clear();
    // 移除已经被 GC 回收的 WeakEntry
    removeGarbageCollectedItems();
    // 清除 Cache
    delegate.clear();
  }
  // 删除垃圾回收项目(从 Cache 里, 移除已被 GC 回收的 WeakEntry)
  private void removeGarbageCollectedItems() {
    WeakEntry sv;
    // 移除已被 GC 回收的键
    while ((sv = (WeakEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      // 移除对应的缓存
      delegate.removeObject(sv.key);
    }
  }
  // 自定义弱引用项
  private static class WeakEntry extends WeakReference<Object> {
    private final Object key;// 缓存键

    private WeakEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue);
      // 关联 key
      this.key = key;
    }
  }

}
