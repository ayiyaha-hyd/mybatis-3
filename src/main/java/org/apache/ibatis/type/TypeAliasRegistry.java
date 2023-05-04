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
package org.apache.ibatis.type;

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.*;

/**
 * @author Clinton Begin
 */
// 类型别名注册表(注册模式)
public class TypeAliasRegistry {
  // <类型别名,类型> 的映射
  private final Map<String, Class<?>> typeAliases = new HashMap<>();
  // 初始化映射,另外在 org/apache/ibatis/session/Configuration.java, 也存在部分 registerAlias() 操作
  public TypeAliasRegistry() {
    registerAlias("string", String.class);

    registerAlias("byte", Byte.class);
    registerAlias("long", Long.class);
    registerAlias("short", Short.class);
    registerAlias("int", Integer.class);
    registerAlias("integer", Integer.class);
    registerAlias("double", Double.class);
    registerAlias("float", Float.class);
    registerAlias("boolean", Boolean.class);

    registerAlias("byte[]", Byte[].class);
    registerAlias("long[]", Long[].class);
    registerAlias("short[]", Short[].class);
    registerAlias("int[]", Integer[].class);
    registerAlias("integer[]", Integer[].class);
    registerAlias("double[]", Double[].class);
    registerAlias("float[]", Float[].class);
    registerAlias("boolean[]", Boolean[].class);

    registerAlias("_byte", byte.class);
    registerAlias("_long", long.class);
    registerAlias("_short", short.class);
    registerAlias("_int", int.class);
    registerAlias("_integer", int.class);
    registerAlias("_double", double.class);
    registerAlias("_float", float.class);
    registerAlias("_boolean", boolean.class);

    registerAlias("_byte[]", byte[].class);
    registerAlias("_long[]", long[].class);
    registerAlias("_short[]", short[].class);
    registerAlias("_int[]", int[].class);
    registerAlias("_integer[]", int[].class);
    registerAlias("_double[]", double[].class);
    registerAlias("_float[]", float[].class);
    registerAlias("_boolean[]", boolean[].class);

    registerAlias("date", Date.class);
    registerAlias("decimal", BigDecimal.class);
    registerAlias("bigdecimal", BigDecimal.class);
    registerAlias("biginteger", BigInteger.class);
    registerAlias("object", Object.class);

    registerAlias("date[]", Date[].class);
    registerAlias("decimal[]", BigDecimal[].class);
    registerAlias("bigdecimal[]", BigDecimal[].class);
    registerAlias("biginteger[]", BigInteger[].class);
    registerAlias("object[]", Object[].class);

    registerAlias("map", Map.class);
    registerAlias("hashmap", HashMap.class);
    registerAlias("list", List.class);
    registerAlias("arraylist", ArrayList.class);
    registerAlias("collection", Collection.class);
    registerAlias("iterator", Iterator.class);

    registerAlias("ResultSet", ResultSet.class);
  }

  @SuppressWarnings("unchecked")
  // throws class cast exception as well if types cannot be assigned
  // 解析别名,获取别名对应的类型
  public <T> Class<T> resolveAlias(String string) {
    try {
      if (string == null) {
        return null;
      }
      // issue #748
      // 转小写(String,string,STRING都会映射为同一类型)
      String key = string.toLowerCase(Locale.ENGLISH);
      Class<T> value;
      // 查找注册表,获取别名对应的类型(别名)
      if (typeAliases.containsKey(key)) {
        value = (Class<T>) typeAliases.get(key);
      } else {
        // 注册表没找到,直接反射获取对应的类(全类名)
        value = (Class<T>) Resources.classForName(string);
      }
      return value;
    } catch (ClassNotFoundException e) {
      throw new TypeException("Could not resolve type alias '" + string + "'.  Cause: " + e, e);
    }
  }
  // 注册指定包下的别名与类的映射(扫描指定包下的所有类，并进行注册)
  public void registerAliases(String packageName) {
    // Object 类型,即所有类都要扫描
    registerAliases(packageName, Object.class);
  }
  // 注册指定包下的别名与类的映射(扫描指定包下的指定类，并进行注册)
  public void registerAliases(String packageName, Class<?> superType) {
    // 通过 ResolverUtil 工具类, 获取符合条件的 Class 集合
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    // 寻找 superType 类或其子类
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> typeSet = resolverUtil.getClasses();
    // 遍历
    for (Class<?> type : typeSet) {
      // Ignore inner classes and interfaces (including package-info.java)
      // Skip also inner classes. See issue #6
      // 忽略 匿名类,接口类,内部类
      if (!type.isAnonymousClass() && !type.isInterface() && !type.isMemberClass()) {
        // 注册
        registerAlias(type);
      }
    }
  }
  // 注册指定类
  public void registerAlias(Class<?> type) {
    // 默认获取简单类名
    String alias = type.getSimpleName();
    // 获取 @Alias 注解(alias)
    Alias aliasAnnotation = type.getAnnotation(Alias.class);
    if (aliasAnnotation != null) {
      // 如果 @Alias 注解不为空, 则使用注解指定的别名,作为类的别名
      alias = aliasAnnotation.value();
    }
    // 注册 <别名,类型> 注册表
    registerAlias(alias, type);
  }
  // 注册别名 (<别名,类型>)
  public void registerAlias(String alias, Class<?> value) {
    if (alias == null) {
      throw new TypeException("The parameter alias cannot be null");
    }
    // issue #748
    // 别名转换为小写(提升兼容性,String,string,STRING...等别名都会映射成 String 类型)
    String key = alias.toLowerCase(Locale.ENGLISH);
    if (typeAliases.containsKey(key) && typeAliases.get(key) != null && !typeAliases.get(key).equals(value)) {
      // 如果别名已经存在,抛异常,提升别名已经存在(已被设置)
      throw new TypeException("The alias '" + alias + "' is already mapped to the value '" + typeAliases.get(key).getName() + "'.");
    }
    // 映射赋值
    typeAliases.put(key, value);
  }
  // 注册别名, 通过别名,类名字符串
  public void registerAlias(String alias, String value) {
    try {
      // 解析 value (类名字符串) 为对应的 Class
      registerAlias(alias, Resources.classForName(value));
    } catch (ClassNotFoundException e) {
      throw new TypeException("Error registering type alias " + alias + " for " + value + ". Cause: " + e, e);
    }
  }

  /**
   * @since 3.2.2
   */
  public Map<String, Class<?>> getTypeAliases() {
    return Collections.unmodifiableMap(typeAliases);
  }

}
