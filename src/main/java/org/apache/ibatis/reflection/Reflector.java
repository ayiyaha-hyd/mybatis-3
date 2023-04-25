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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
// 反射器
// 缓存了一些包含Class(类信息)的集合, 用来方便获取属性名称,getter/setter方法等
public class Reflector {

  // 对应的 Class 类对象
  private final Class<?> type;
  // 可读属性数组
  private final String[] readablePropertyNames;
  // 可写属性数组
  private final String[] writablePropertyNames;
  // 属性对应的 setter 方法 集合
  private final Map<String, Invoker> setMethods = new HashMap<>();
  // 属性对应的 getter 方法 集合
  private final Map<String, Invoker> getMethods = new HashMap<>();
  // 属性对应的 setter 方法的参数类型 集合
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  // 属性对应的 getter 方法的返回值类型 集合
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  // 默认无参构造器
  private Constructor<?> defaultConstructor;
  // 不区分大小写的属性集合(大写集合)
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();
  // Reflector 构造器
  public Reflector(Class<?> clazz) {
    // 设置 Class 对象
    type = clazz;
    // 初始化 defaultConstructor
    addDefaultConstructor(clazz);
    // 初始化 getMethods, getTypes
    addGetMethods(clazz);
    // 初始化 setMethods, setTypes
    addSetMethods(clazz);
    // 初始化 getMethods, getTypes 和 setMethods, setTypes
    addFields(clazz);
    // 初始化 readablePropertyNames
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // 初始化 writablePropertyNames
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 初始化 caseInsensitivePropertyMap (readablePropertyNames 大写)
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    // 初始化 caseInsensitivePropertyMap (writablePropertyNames 大写)
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }
  // 添加默认无参构造器
  private void addDefaultConstructor(Class<?> clazz) {
    // 获取所有构造方法
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    // 遍历获取无参构造方法
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }
  // 遍历 clazz 所有 getter 方法,初始化 getMethods, getTypes
  private void addGetMethods(Class<?> clazz) {
    // field 和 getter methods 映射(子类和父类可能有相同属性getter方法,所以是method集合)
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获取所有方法
    Method[] methods = getClassMethods(clazz);
    // 遍历方法
    Arrays.stream(methods)
      //获取无参且以get/is开头的方法, 说明是getter 方法
      .filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      // 遍历, 添加到 conflictingGetters 中(PropertyNamer.methodToProperty()用来解析属性名)
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决 getter 冲突的方法
    resolveGetterConflicts(conflictingGetters);
  }
  // 解决 getter 冲突的方法
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历每个属性, 查找最匹配的方法, 因为子类可以重写父类的方法, 所以一个属性, 可能对应多个 getter 方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 胜利者(最匹配的方法)
      Method winner = null;
      String propName = entry.getKey();
      boolean isAmbiguous = false;
      // 遍历候选的方法(确定最匹配的方法)
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          winner = candidate;
          continue;
        }
        // 当前假定胜利者方法的类型
        Class<?> winnerType = winner.getReturnType();
        // 候选者方法的类型
        Class<?> candidateType = candidate.getReturnType();
        // 基于返回类型做比较(pk)
        // 如果胜利者与候选者返回值类型相等
        if (candidateType.equals(winnerType)) {
          // 如果候选者返回类型不是 boolean类型
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          // 如果候选者返回类型是 boolean 类型
          } else if (candidate.getName().startsWith("is")) {
            // 选择 boolean 类型的 is 方法
            winner = candidate;
          }
        // isAssignableFrom 判断 candidateType 是否与 winnerType 相同或者是其父类
        // 此处说明 winnerType 是后代, 不做处理(因为子类的类型<=父类)
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 此处说明 candidateType 是后代, 将当前 candidate 设置为 winner
          winner = candidate;
        } else {
          isAmbiguous = true;
          break;
        }
      }
      // 初始化 getMethods, getTypes
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  // 初始化 getMethods, getTypes
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 创建方法调用器
    // 如果方法是模棱两可的, 则创建一个 AmbiguousMethodInvoker 方法调用器, 该调用器 invoke 的时候会抛异常, 只有调用时才会抛异常
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    // 添加到 getMethods 中
    getMethods.put(name, invoker);
    // 类型参数解析器解析返回值类型
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // type 转 Class 并添加到 getTypes 中
    getTypes.put(name, typeToClass(returnType));
  }

  // 遍历 clazz 所有 setter 方法,初始化 setMethods, setTypes
  private void addSetMethods(Class<?> clazz) {
    // field 和 setter methods 映射(子类和父类可能有相同属性setter方法,所以是method集合)
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获取所有方法
    Method[] methods = getClassMethods(clazz);
    // 遍历方法
    Arrays.stream(methods)
      //获取只有一个入参且以set开头的方法, 说明是setter 方法
      .filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      // 遍历, 添加到 conflictingGetters 中(PropertyNamer.methodToProperty()用来解析属性名)
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决 setter 冲突的方法
    resolveSetterConflicts(conflictingSetters);
  }

  // 添加 getter 冲突方法
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    // 判断是否是有效属性
    if (isValidPropertyName(name)) {
      // 如果属性不存在, 则放入
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      // 将方法放入list
      list.add(method);
    }
  }
  // 解决 setter 冲突的方法
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    // 遍历每个属性, 查找最匹配的方法, 因为子类可以重写父类的方法, 所以一个属性, 可能对应多个 setter 方法
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      // 获取 getter 是否模棱两可的标识
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      // 匹配
      Method match = null;
      // 遍历寻找最匹配的
      for (Method setter : setters) {
        // 如果 getter 不是模棱两可, 且 parameterTypes 和 getterType 类型一致, 那么它是最匹配的
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        // 如果 setter 不是模棱两可的
        if (!isSetterAmbiguous) {
          // 寻找最匹配的 setter
          match = pickBetterSetter(match, setter, propName);
          // 没找到最匹配的 setter, setter 是模棱两可的
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        // 初始化 setMethods, setTypes
        addSetMethod(propName, match);
      }
    }
  }
  // 从两个 setter 方法找到最匹配的 method
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // isAssignableFrom 判断 paramType1 是否与 paramType2 类型相同或者是其父类
    // 选取后代(子类)
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    // 创建一个 AmbiguousMethodInvoker 方法调用器, 该调用器 invoke 的时候会抛异常, 只有调用时才会抛异常
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    // 添加到 setMethods 中
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    // 添加到 setTypes 中
    setTypes.put(property, typeToClass(paramTypes[0]));
    // 返回null
    return null;
  }
  // 初始化 setMethods, setTypes
  private void addSetMethod(String name, Method method) {
    // 创建方法调用器
    MethodInvoker invoker = new MethodInvoker(method);
    // 添加到 setMethods 中
    setMethods.put(name, invoker);
    // 参数解析器解析参数类型
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    // type 转 CLass 并添加到 setTypes 中
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  // 获取 java.lang.reflect.Type 真正的 Class 类
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 普通类型, 直接使用Class类
    if (src instanceof Class) {
      result = (Class<?>) src;
    // 泛型, 使用泛型
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    // 泛型数组, 获取具体类
    } else if (src instanceof GenericArrayType) {
      // 获取泛型对象具体类
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      // 普通类型
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        // 递归获取
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    // 都不符合, 使用 Object 类型
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  // 遍历 fields, 添加 getMethods, getTypes 和 setMethods, setTypes
  // 对 addGetMethods,addSetMethods 方法的补充, 因为有些 field, 不存在对应的 setter,getter
  private void addFields(Class<?> clazz) {
    // 获取所有的属性
    Field[] fields = clazz.getDeclaredFields();
    // 遍历
    for (Field field : fields) {
      // setMethods 不存在则进行添加
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        // (https://github.com/mybatis/mybatis-3/pull/379)
        // (https://github.com/mybatis/mybatis-3/pull/16)
        // (https://stackoverflow.com/questions/3301635/change-private-static-final-field-using-java-reflection)
        // (https://www.cnblogs.com/sanzao/p/13267269.html)
        int modifiers = field.getModifiers();
        // 跳过 static final 属性(针对set)
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          // 添加 setMethods, setTypes
          addSetField(field);
        }
      }
      // getMethods 不存在则进行添加
      if (!getMethods.containsKey(field.getName())) {
        // 添加 getMethods, getTypes
        addGetField(field);
      }
    }
    if (clazz.getSuperclass() != null) {
      // 递归父类(可用迭代法)
      addFields(clazz.getSuperclass());
    }
  }
  // 添加 setMethods, setTypes
  private void addSetField(Field field) {
    // 判断是否是有效属性
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }
  // 添加 getMethods, getTypes
  private void addGetField(Field field) {
    // 判断是否是有效属性
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  // 判断是否是有效属性
  // (踩过坑,反射获取类所有属性时,这些无效字段很干扰诸如获取对象对应field value等操作,会有异常,此处过滤掉减少了不必要的麻烦)
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  // 获取 clazz 类所有方法
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    // 循环遍历当前 Class 及其父类 Class(迭代法)
    while (currentClass != null && currentClass != Object.class) {
      // 添加方法到 uniqueMethods
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 获取当前 Class 的接口
      Class<?>[] interfaces = currentClass.getInterfaces();
      // 遍历接口
      for (Class<?> anInterface : interfaces) {
        // 添加方法到 uniqueMethods
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 将当前 currentClass 指向其父类 Class
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();
    // 集合转数组
    return methods.toArray(new Method[0]);
  }

  // 添加方法到 uniqueMethods
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    // 遍历
    for (Method currentMethod : methods) {
      // 跳过桥接的方法(https://github.com/mybatis/mybatis-3/issues/237)
      // (https://www.zhihu.com/question/54895701/answer/141623158)
      if (!currentMethod.isBridge()) {
        // 获取方法的签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 当 uniqueMethods 不存在时, 进行添加(方法签名能区分重载的方法等)
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }
  // 获取方法的签名
  // returnType#methodName:parameterName1,parameterName2...
  // void#checkPackageAccess:java.lang.ClassLoader,boolean
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 获取返回值
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  // 检查是否可以控制成员可访问性
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        // 如果根据当前有效的安全策略不允许由给定权限指定的请求访问，则引发 a SecurityException
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  // 获取当前 Class 类信息对象
  public Class<?> getType() {
    return type;
  }
  // 获取默认构造器
  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }
  // 判断是否有默认构造器
  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }
  // 获取 setter method Invoker
  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }
  // 获取 getter method Invoker
  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  // 获取 setter 入参类型
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  // 获取 getter 返回值类型
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  // 获取可读属性数组
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  // 获取可写属性数组
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  // 判断属性是否有 setter
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  // 判断属性是否有 getter
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }
  // 获取属性名称
  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
