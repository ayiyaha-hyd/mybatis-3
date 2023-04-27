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

import java.util.Deque;
import java.util.LinkedList;

/**
 * FIFO (first in, first out) cache decorator.
 *
 * @author Clinton Begin
 */
// 基于FIFO(先进先出)淘汰机制的缓存装饰器
public class FifoCache implements Cache {
  // Cache 缓存委托
  private final Cache delegate;
  // FIFO 队列(双端队列，记录缓存键的添加)
  private final Deque<Object> keyList;
  // 队列上限
  private int size;
  // 构造方法
  public FifoCache(Cache delegate) {
    this.delegate = delegate;
    // 使用 LinkedList 做 FIFO 队列
    this.keyList = new LinkedList<>();
    this.size = 1024;
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
  // 设置队列大小
  public void setSize(int size) {
    this.size = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    cycleKeyList(key);
    delegate.putObject(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }
  // 清空缓存
  @Override
  public void clear() {
    // 缓存
    delegate.clear();
    // 缓存队列
    keyList.clear();
  }
  // 循环队列
  private void cycleKeyList(Object key) {
    // 添加到队尾(后进)
    keyList.addLast(key);
    if (keyList.size() > size) {
      // 队列满之后, 移除队首元素(先进先出)
      Object oldestKey = keyList.removeFirst();
      // 移除实际缓存元素
      delegate.removeObject(oldestKey);
    }
  }

}
