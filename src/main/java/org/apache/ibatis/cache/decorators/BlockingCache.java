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
import org.apache.ibatis.cache.CacheException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple blocking decorator
 *
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * @author Eduardo Macarron
 *
 */
// 阻塞的 Cache
// 这里的阻塞比较特殊，当线程去获取缓存值时，如果不存在，则会阻塞后续的其他线程去获取该缓存
// 原因: 当线程 A 在获取不到缓存值时，一般会去设置对应的缓存值，这样就避免其他也需要该缓存的线程 B、C 等，重复添加缓存
public class BlockingCache implements Cache {
  // 超时
  private long timeout;
  // 缓存委托
  private final Cache delegate;
  // 锁(缓存 key 与 ReentrantLock 的映射)
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }
  // 添加
  @Override
  public void putObject(Object key, Object value) {
    try {
      // 添加缓存
      delegate.putObject(key, value);
    } finally {
      // 释放锁
      releaseLock(key);
    }
  }
  // 获取
  @Override
  public Object getObject(Object key) {
    // 获取锁
    acquireLock(key);
    // 获取缓存
    Object value = delegate.getObject(key);
    // 缓存不为空, 释放锁
    if (value != null) {
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    // 尽管名称叫 remove, 但实际并不会移除缓存, 只会是否对应的锁
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 获取 key 对应的锁
   * 此处可以做优化(避免并发效率问题)
   * <pre>
   *  private ReentrantLock getLockForKey(Object key) {
   *     ReentrantLock lock = null;
   *     if (null == locks.get(key)) {
   *       lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
   *     }
   *     return lock;
   *   }
   * </pre>
   */
  private ReentrantLock getLockForKey(Object key) {
    return locks.computeIfAbsent(key, k -> new ReentrantLock());
  }
  // 获取锁
  private void acquireLock(Object key) {
    // 获取 key 对应的锁
    Lock lock = getLockForKey(key);
    if (timeout > 0) {
      try {
        // 加锁
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      // 加锁
      lock.lock();
    }
  }
  // 释放锁
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    // 查询此锁是否由当前线程持有
    if (lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }
  // 获取超时时间
  public long getTimeout() {
    return timeout;
  }
  // 设置超时时间
  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
