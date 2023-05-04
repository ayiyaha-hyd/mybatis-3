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

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.chrono.JapaneseDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
// 类型处理器注册表(注册模式)
// 为解决全局共享和交互对象, 相当于一个容器, 统一管理
// 为应用中经常使用的对象创建一个中央存储器来存放这些对象,通过把对象挂到注册树上的方式管理全局对象
public final class TypeHandlerRegistry {
  // JdbcType 和 TypeHandler 映射
  private final Map<JdbcType, TypeHandler<?>>  jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);
  // Type 和 jdbcTypeHandlerMap 映射
  private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();
  // unknownType 类型处理器(处理 unknown Type)
  private final TypeHandler<Object> unknownTypeHandler = new UnknownTypeHandler(this);
  // 所有 TypeHandler 的集合
  private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();
  // NULL_TYPE 类型处理器(处理 null JdbcType)
  private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();
  // 默认枚举类型类型处理器
  private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;
  // 构造方法里,进行对象的注册
  public TypeHandlerRegistry() {
    register(Boolean.class, new BooleanTypeHandler());
    register(boolean.class, new BooleanTypeHandler());
    register(JdbcType.BOOLEAN, new BooleanTypeHandler());
    register(JdbcType.BIT, new BooleanTypeHandler());

    register(Byte.class, new ByteTypeHandler());
    register(byte.class, new ByteTypeHandler());
    register(JdbcType.TINYINT, new ByteTypeHandler());

    register(Short.class, new ShortTypeHandler());
    register(short.class, new ShortTypeHandler());
    register(JdbcType.SMALLINT, new ShortTypeHandler());

    register(Integer.class, new IntegerTypeHandler());
    register(int.class, new IntegerTypeHandler());
    register(JdbcType.INTEGER, new IntegerTypeHandler());

    register(Long.class, new LongTypeHandler());
    register(long.class, new LongTypeHandler());

    register(Float.class, new FloatTypeHandler());
    register(float.class, new FloatTypeHandler());
    register(JdbcType.FLOAT, new FloatTypeHandler());

    register(Double.class, new DoubleTypeHandler());
    register(double.class, new DoubleTypeHandler());
    register(JdbcType.DOUBLE, new DoubleTypeHandler());

    register(Reader.class, new ClobReaderTypeHandler());
    register(String.class, new StringTypeHandler());
    register(String.class, JdbcType.CHAR, new StringTypeHandler());
    register(String.class, JdbcType.CLOB, new ClobTypeHandler());
    register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
    register(JdbcType.CHAR, new StringTypeHandler());
    register(JdbcType.VARCHAR, new StringTypeHandler());
    register(JdbcType.CLOB, new ClobTypeHandler());
    register(JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(JdbcType.NVARCHAR, new NStringTypeHandler());
    register(JdbcType.NCHAR, new NStringTypeHandler());
    register(JdbcType.NCLOB, new NClobTypeHandler());

    register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
    register(JdbcType.ARRAY, new ArrayTypeHandler());

    register(BigInteger.class, new BigIntegerTypeHandler());
    register(JdbcType.BIGINT, new LongTypeHandler());

    register(BigDecimal.class, new BigDecimalTypeHandler());
    register(JdbcType.REAL, new BigDecimalTypeHandler());
    register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
    register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

    register(InputStream.class, new BlobInputStreamTypeHandler());
    register(Byte[].class, new ByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
    register(byte[].class, new ByteArrayTypeHandler());
    register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
    register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.BLOB, new BlobTypeHandler());

    register(Object.class, unknownTypeHandler);
    register(Object.class, JdbcType.OTHER, unknownTypeHandler);
    register(JdbcType.OTHER, unknownTypeHandler);

    register(Date.class, new DateTypeHandler());
    register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
    register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
    register(JdbcType.TIMESTAMP, new DateTypeHandler());
    register(JdbcType.DATE, new DateOnlyTypeHandler());
    register(JdbcType.TIME, new TimeOnlyTypeHandler());

    register(java.sql.Date.class, new SqlDateTypeHandler());
    register(java.sql.Time.class, new SqlTimeTypeHandler());
    register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

    register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

    register(Instant.class, new InstantTypeHandler());
    register(LocalDateTime.class, new LocalDateTimeTypeHandler());
    register(LocalDate.class, new LocalDateTypeHandler());
    register(LocalTime.class, new LocalTimeTypeHandler());
    register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
    register(OffsetTime.class, new OffsetTimeTypeHandler());
    register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
    register(Month.class, new MonthTypeHandler());
    register(Year.class, new YearTypeHandler());
    register(YearMonth.class, new YearMonthTypeHandler());
    register(JapaneseDate.class, new JapaneseDateTypeHandler());

    // issue #273
    register(Character.class, new CharacterTypeHandler());
    register(char.class, new CharacterTypeHandler());
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    this.defaultEnumTypeHandler = typeHandler;
  }

  public boolean hasTypeHandler(Class<?> javaType) {
    return hasTypeHandler(javaType, null);
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
    return hasTypeHandler(javaTypeReference, null);
  }

  public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
    return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
    return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
  }

  public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
    return allTypeHandlersMap.get(handlerType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    // jdbcType 为 null
    return getTypeHandler((Type) type, null);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
    // jdbcType 为 null
    return getTypeHandler(javaTypeReference, null);
  }
  // 获取 jdbcType 对应的 TypeHandler
  public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
    return jdbcTypeHandlerMap.get(jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
    // 将 type(Class) 转为 type(Type) 类型
    return getTypeHandler((Type) type, jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
    // 使用 rawType 作为 javaType(<T> 泛型中的 T)
    return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
  }

  @SuppressWarnings("unchecked")
  // 获取 typeHandler(最终调用该方法**)
  private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
    // 忽略 ParamMap
    if (ParamMap.class.equals(type)) {
      return null;
    }
    // 获取 javaType 对应的 typeHandlerMap (jdbcType,typeHandler 映射)
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
    TypeHandler<?> handler = null;
    if (jdbcHandlerMap != null) {
      // 获取 jdbcType 对应的 TypeHandler
      handler = jdbcHandlerMap.get(jdbcType);
      if (handler == null) {// 如果 handler 为 null
        // 获取 null 对应的 TypeHandler (可以认为是默认的 TypeHandler)
        handler = jdbcHandlerMap.get(null);
      }
      if (handler == null) {// 如果此处 handler 还是为 null
        // #591
        handler = pickSoleHandler(jdbcHandlerMap);
      }
    }
    // type drives generics here
    return (TypeHandler<T>) handler;
  }
  // 获取 javaType 对应的 jdbcHandlerMap (jdbcType,typeHandler 映射)
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
    // 根据 javaType 获取 jdbcType,typeHandler 映射集合
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
    // 如果为 NULL_TYPE_HANDLER_MAP(empty map), 说明为空,直接返回 null
    if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) {
      return null;
    }
    // 如果 jdbcHandlerMap 为 null
    if (jdbcHandlerMap == null && type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      // 如果 type 是枚举类
      if (Enum.class.isAssignableFrom(clazz)) {
        // 如果 clazz 是匿名类, 获取其父类
        Class<?> enumClass = clazz.isAnonymousClass() ? clazz.getSuperclass() : clazz;
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(enumClass, enumClass);
        // 如果找不到
        if (jdbcHandlerMap == null) {
          //注册默认的 defaultEnumTypeHandler
          register(enumClass, getInstance(enumClass, defaultEnumTypeHandler));
          return typeHandlerMap.get(enumClass);
        }
      } else {// 非枚举类类型
        // 获得父类对应的 TypeHandler 集合
        jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
      }
    }
    // typeHandlerMap 赋值(2)
    // 如果 到此 jdbcHandlerMap 为 null, 则创建 empty map,否则返回实际值(下次遇到 NULL_TYPE_HANDLER_MAP 直接返回 null,避免二次查找)
    typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
    return jdbcHandlerMap;
  }
  // 获取枚举接口的 jdbcHandlerMap (jdbcType,typeHandler 映射)
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
    // 遍历枚举类的所有接口
    for (Class<?> iface : clazz.getInterfaces()) {
      // 获取该接口对应的 jdbcHandlerMap
      Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
      // 如果为空
      if (jdbcHandlerMap == null) {
        // 递归,获取该接口父接口的 jdbcHandlerMap
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
      }
      // 如果找到
      if (jdbcHandlerMap != null) {
        // Found a type handler regsiterd to a super interface
        // new Map, 遍历 jdbcHandlerMap 赋值并返回
        HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
        for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
          // Create a type handler instance with enum type as a constructor arg
          newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
        }
        return newMap;
      }
    }
    return null;
  }
  // 获得父类对应的 TypeHandler 集合(jdbcType,TypeHandler 映射)
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
    Class<?> superclass =  clazz.getSuperclass();
    // 如果 superclass 为空,或者等于 Object, 返回 null
    if (superclass == null || Object.class.equals(superclass)) {
      return null;
    }
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
    // 找到了直接返回
    if (jdbcHandlerMap != null) {
      return jdbcHandlerMap;
    } else {// 找不到则递归遍历
      // 递归,获取父类 typeHandler 集合
      return getJdbcHandlerMapForSuperclass(superclass);
    }
  }
  // 从 jdbcHandlerMap 中,选一个唯一的 TypeHandler
  private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
    TypeHandler<?> soleHandler = null;
    for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
      if (soleHandler == null) {
        soleHandler = handler;
      } else if (!handler.getClass().equals(soleHandler.getClass())) {
        // 如果 typeHandler 类型存在多个相同的,返回 null
        // More than one type handlers registered.
        return null;
      }
    }
    return soleHandler;
  }

  public TypeHandler<Object> getUnknownTypeHandler() {
    return unknownTypeHandler;
  }
  // 注册(jdbcType + typeHandler)
  public void register(JdbcType jdbcType, TypeHandler<?> handler) {
    // jdbcTypeHandlerMap 赋值(1)
    jdbcTypeHandlerMap.put(jdbcType, handler);
  }

  //
  // REGISTER INSTANCE
  //

  // Only handler

  @SuppressWarnings("unchecked")
  // 注册 typeHandler
  public <T> void register(TypeHandler<T> typeHandler) {
    boolean mappedTypeFound = false;
    // 判断类是否有 @MappedTypes 注解标注
    MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
    // 如果注解不为空
    if (mappedTypes != null) {
      // 获取 @MappedTypes 注解值(javaType),遍历并注册 (javaType + typeHandler)
      for (Class<?> handledType : mappedTypes.value()) {
        // 优先使用 @MappedTypes 注解中的 javaType 进行注册
        register(handledType, typeHandler);
        mappedTypeFound = true;
      }
    }
    // @since 3.1.0 - try to auto-discover the mapped type
    // 如果注解为空,且 typeHandler 为 TypeReference 子类
    if (!mappedTypeFound && typeHandler instanceof TypeReference) {
      try {
        TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
        // typeReference.getRawType()(获取 <T> 泛型的实际类型 T)
        // 其次如果是 TypeReference 子类, 使用 rawType 注册(javaType + typeHandler)
        register(typeReference.getRawType(), typeHandler);
        mappedTypeFound = true;
      } catch (Throwable t) {
        // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
      }
    }
    // 如果注解和 TypeReference两种方式都不符合, 使用 null(javaType) 进行注册
    if (!mappedTypeFound) {
      register((Class<T>) null, typeHandler);
    }
  }

  // java type + handler

  public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
    register((Type) javaType, typeHandler);
  }
  // 注册指定 javaType 的 typeHandler
  private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
    // 获取 @MappedJdbcTypes 注解
    MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
    // 判断注解为不为空,进行不同的处理
    if (mappedJdbcTypes != null) {
      // 遍历 @MappedJdbcTypes 注解,获取 jdbcType 进行注册
      for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
        register(javaType, handledJdbcType, typeHandler);
      }
      // 如果包含 null jdbcType, 则注册 null(jdbcType) 类型
      if (mappedJdbcTypes.includeNullJdbcType()) {
        register(javaType, null, typeHandler);
      }
    } else {
      register(javaType, null, typeHandler);
    }
  }

  public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
    register(javaTypeReference.getRawType(), handler);
  }

  // java type + jdbc type + handler

  public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
    register((Type) type, jdbcType, handler);
  }
  // 注册 javaType + jdbcType + handler(实际的注册方法**)
  private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
    if (javaType != null) {
      Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);
      if (map == null || map == NULL_TYPE_HANDLER_MAP) {
        map = new HashMap<>();
        // typeHandlerMap 赋值(1)
        typeHandlerMap.put(javaType, map);
      }
      map.put(jdbcType, handler);
    }
    // allTypeHandlersMap 赋值(1)
    allTypeHandlersMap.put(handler.getClass(), handler);
  }

  //
  // REGISTER CLASS
  //

  // Only handler type
  // 注册指定类型的 typeHandler(**)
  public void register(Class<?> typeHandlerClass) {
    boolean mappedTypeFound = false;
    // 判断类是否有 @MappedTypes 注解标注
    MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
    // 如果注解不为空
    if (mappedTypes != null) {
      // 获取 @MappedTypes 注解值(javaType),遍历并注册 (javaType + typeHandler)
      for (Class<?> javaTypeClass : mappedTypes.value()) {
        // 注册
        register(javaTypeClass, typeHandlerClass);
        mappedTypeFound = true;
      }
    }
    // 如果注解为空(未使用 @MappedTypes 注解,直接注册)
    if (!mappedTypeFound) {
      // 未使用 @MappedTypes 注解,则调用此方法)
      register(getInstance(null, typeHandlerClass));
    }
  }

  // java type + handler type

  public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
    register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
  }
  // 注册指定 javaType 的 typeHandler
  public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
  }

  // java type + jdbc type + handler type

  public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
    register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
  }

  // Construct a handler (used also from Builders)

  @SuppressWarnings("unchecked")
  // 获取 TypeHandler 实例对象(创建实例对象)
  public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    // 判断 javaTypeClass 是否为空
    if (javaTypeClass != null) {
      try {
        // 获取 Class 入参类型的构造方法
        Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
        // 有参构造创建实例(例如 EnumTypeHandler)
        return (TypeHandler<T>) c.newInstance(javaTypeClass);
      } catch (NoSuchMethodException ignored) {
        // ignored
      } catch (Exception e) {
        throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
      }
    }
    try {
      Constructor<?> c = typeHandlerClass.getConstructor();
      // 无参构造创建实例(例如 StringTypeHandler)
      return (TypeHandler<T>) c.newInstance();
    } catch (Exception e) {
      throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
    }
  }

  // scan
  // 扫描指定包路径下的 TypeHandler, 注册(**)
  public void register(String packageName) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    // 扫描找到 TypeHandler 及其子类
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
    // 获取扫描后的结果集
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
    // 遍历 handler, 并注册
    for (Class<?> type : handlerSet) {
      //Ignore inner classes and interfaces (including package-info.java) and abstract classes
      // 忽略内部类和接口(包括 package-info.java )和抽象类
      if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
        // 注册
        register(type);
      }
    }
  }

  // get information

  /**
   * @since 3.2.2
   */
  public Collection<TypeHandler<?>> getTypeHandlers() {
    return Collections.unmodifiableCollection(allTypeHandlersMap.values());
  }

}
