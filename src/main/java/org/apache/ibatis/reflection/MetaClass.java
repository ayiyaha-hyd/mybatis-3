/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
// 类的元数据
public class MetaClass {
  // 反射器工厂
  private final ReflectorFactory reflectorFactory;
  // 反射器
  private final Reflector reflector;
  // 构造方法
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }
  // 创建指定类的 MetaClass 对象
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }
  // 创建指定类属性的类的 MetaClass 对象
  public MetaClass metaClassForProperty(String name) {
    // 获取属性的类
    Class<?> propType = reflector.getGetterType(name);
    // 创建 MetaClass 对象
    return MetaClass.forClass(propType, reflectorFactory);
  }
  // 获取属性
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }
  // 获取属性
  public String findProperty(String name, boolean useCamelCaseMapping) {
    // 下划线转驼峰, 此处只把 "_" 替换为 "", 调用 reflector.findPropertyName(name) 时,
    // 会从 caseInsensitivePropertyMap 获取实际属性字段
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }
  // 获取 getter 方法名数组
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }
  // 获取 setter 方法名数组
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }
  // 获取属性入参类型
  public Class<?> getSetterType(String name) {
    // 通过属性分词器获取某个位置元素的入参类型(例如: order[3].item[1].name)
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      // 递归处理
      return metaProp.getSetterType(prop.getChildren());
    } else {
      // 调用 reflector 获取入参类型
      return reflector.getSetterType(prop.getName());
    }
  }
  // 获取返回类型
  public Class<?> getGetterType(String name) {
    // 通过属性分词器获取某个位置元素的返回类型(例如: order[3].item[1].name)
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      // 递归
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }
  // 获取返回类型
  private Class<?> getGetterType(PropertyTokenizer prop) {
    Class<?> type = reflector.getGetterType(prop.getName());
    // 如果是数组的某个位置的元素
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      Type returnType = getGenericGetterType(prop.getName());
      // 如果是泛型, 获取真正的类型
      if (returnType instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }
  // 获取通用返回类型
  private Type getGenericGetterType(String propertyName) {
    try {
      // 获取 Invoker 对象
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 如果是 MethodInvoker, 说明是 getter 方法
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        // 解析返回类型
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      // 如果是 GetFieldInvoker, 说明是 field, 直接访问(有些属性没有直接的 getter, 所以是 GetFieldInvoker
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        // 解析字段类型
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }
  // 判断属性是否有 setter
  public boolean hasSetter(String name) {
    // 分词器深层次获取属性
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }
  // 判断属性是否有 getter
  public boolean hasGetter(String name) {
    // 分词器深层次获取属性
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }
  // 获取 GetInvoker
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }
  // 获取 SetInvoker
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }
  // 构建属性
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 对 name 进行 分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 递归处理 children, 将结果添加到 builder 中
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }
  // 是否有默认构造方法
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
