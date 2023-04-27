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

import java.util.concurrent.TimeUnit;

/**
 * @author Clinton Begin
 */
// 定时清除缓存
// getSize/put/get/remove 会调用定期清缓存方法, 直接调用 clear 则直接清除
public class ScheduledCache implements Cache {
  // Cache (缓存委托)
  private final Cache delegate;
  // 清除间隔
  protected long clearInterval;
  // 上次清除的实际(时间戳)
  protected long lastClear;
  // 构造方法
  public ScheduledCache(Cache delegate) {
    // 委托代理
    this.delegate = delegate;
    // 清除间隔为 1 小时
    this.clearInterval = TimeUnit.HOURS.toMillis(1);
    // 上次清除实际设置为当前时间
    this.lastClear = System.currentTimeMillis();
  }

  public void setClearInterval(long clearInterval) {
    this.clearInterval = clearInterval;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    clearWhenStale();
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object object) {
    clearWhenStale();
    delegate.putObject(key, object);
  }

  @Override
  public Object getObject(Object key) {
    return clearWhenStale() ? null : delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    clearWhenStale();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    // 直接调用 clear 方法, 会刷新上一次清除时间标记
    lastClear = System.currentTimeMillis();
    delegate.clear();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }
  // 过期清除的方法
  private boolean clearWhenStale() {
    // 判断是否过期
    if (System.currentTimeMillis() - lastClear > clearInterval) {
      // 清除
      clear();
      return true;
    }
    return false;
  }

}
