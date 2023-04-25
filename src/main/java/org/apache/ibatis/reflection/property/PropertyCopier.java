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
package org.apache.ibatis.reflection.property;

import java.lang.reflect.Field;

import org.apache.ibatis.reflection.Reflector;

/**
 * @author Clinton Begin
 */
// 属性复制器
public final class PropertyCopier {
  // 私有构造方法
  private PropertyCopier() {
    // Prevent Instantiation of Static Class
  }
  // 从 sourceBean 复制属性值到 destinationBean
  public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
    Class<?> parent = type;
    // 迭代遍历 type 当前类及其父类的各个属性
    while (parent != null) {
      final Field[] fields = parent.getDeclaredFields();
      // 遍历
      for (Field field : fields) {
        try {
          try {
            // 获取 sourceBean 属性,属性值, 赋值到 destinationBean (第一次)
            field.set(destinationBean, field.get(sourceBean));
          } catch (IllegalAccessException e) {
            if (Reflector.canControlMemberAccessible()) {
              // 获取 sourceBean 属性,属性值, 赋值到 destinationBean (第二次)
              field.setAccessible(true);
              field.set(destinationBean, field.get(sourceBean));
            } else {
              throw e;
            }
          }
        } catch (Exception e) {
          // Nothing useful to do, will only fail on final fields, which will be ignored.
        }
      }
      // 获取父类
      parent = parent.getSuperclass();
    }
  }

}
