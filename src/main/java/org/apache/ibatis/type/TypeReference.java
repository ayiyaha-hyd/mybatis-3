/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * References a generic type.
 *
 * @param <T> the referenced type
 * @since 3.1.0
 * @author Simone Tripodi
 */
// 引用泛型抽象类
public abstract class TypeReference<T> {

  private final Type rawType;

  protected TypeReference() {
    rawType = getSuperclassTypeParameter(getClass());
  }

  Type getSuperclassTypeParameter(Class<?> clazz) {
    // 获取父类,包括泛型信息(获取<T>)
    Type genericSuperclass = clazz.getGenericSuperclass();
    // 如果该父类是一个普通类
    if (genericSuperclass instanceof Class) {
      // try to climb up the hierarchy until meet something useful
      // 递归获取父类,尝试向上递归查找有效的泛型信息
      // 因为有些时候本类可能没有泛型, 需要从父类里边获取泛型
      if (TypeReference.class != genericSuperclass) {
        return getSuperclassTypeParameter(clazz.getSuperclass());
      }
      // 如果递归查找父类未能找到 TypeReference 类, 则抛 TypeException 异常
      // 提示当前类没有泛型参数。这时需要为当前类添加泛型参数或者去掉当前类继承TypeReference类
      throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
        + "Remove the extension or add a type parameter to it.");
    }
    // 获取实际的 Type
    Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
    // TODO remove this when Reflector is fixed to return Types
    if (rawType instanceof ParameterizedType) {
      // 如果 rawType 是泛型参数类型, 获取其原始类型
      rawType = ((ParameterizedType) rawType).getRawType();
    }

    return rawType;
  }

  public final Type getRawType() {
    return rawType;
  }

  @Override
  public String toString() {
    return rawType.toString();
  }

}
