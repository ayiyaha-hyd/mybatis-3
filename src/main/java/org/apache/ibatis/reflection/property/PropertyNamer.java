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

import java.util.Locale;

import org.apache.ibatis.reflection.ReflectionException;

/**
 * @author Clinton Begin
 */
// 属性名称获取器
public final class PropertyNamer {
  // 私有构造方法
  private PropertyNamer() {
    // Prevent Instantiation of Static Class
  }

  // 从 getter,setter 方法获取 field
  public static String methodToProperty(String name) {
    // 截取 is,get,set 方法, 获取属性名(只能处理 is,get,set 方法)
    if (name.startsWith("is")) {
      name = name.substring(2);
    } else if (name.startsWith("get") || name.startsWith("set")) {
      name = name.substring(3);
    } else {
      throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
    }
    // 首字母小写
    if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) {
      name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
    }

    return name;
  }
  // 是否是 getter,setter 属性方法
  public static boolean isProperty(String name) {
    return isGetter(name) || isSetter(name);
  }
  // 是否是 getter 方法
  public static boolean isGetter(String name) {
    return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
  }
  // 是否是 setter 方法
  public static boolean isSetter(String name) {
    return name.startsWith("set") && name.length() > 3;
  }

}
