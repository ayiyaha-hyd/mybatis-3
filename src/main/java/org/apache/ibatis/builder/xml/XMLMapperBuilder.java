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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
// xml mapper 建造者, 解析 Mapper 映射配置文件 (建造者模式)
public class XMLMapperBuilder extends BaseBuilder {
  // XPath 解析器
  private final XPathParser parser;
  // Mapper 构造器 助手
  private final MapperBuilderAssistant builderAssistant;
  // 可被其他语句引用的可重用语句块的集合
  private final Map<String, XNode> sqlFragments;
  // 资源引用地址
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    // 创建 MapperBuilderAssistant
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }
  // parse, 解析 Mapper XML 配置文件
  public void parse() {
    // 判断当前 mapper 是否已被加载
    if (!configuration.isResourceLoaded(resource)) {
      // 解析 <mapper /> 节点
      configurationElement(parser.evalNode("/mapper"));
      // 添加到 loadedResources 集合, 标记为已加载
      configuration.addLoadedResource(resource);
      // 绑定 mapper
      bindMapperForNamespace();
    }
    // 解析待定 <parameterMap /> 节点
    parsePendingResultMaps();
    // 解析待定 <cache-ref /> 节点
    parsePendingCacheRefs();
    // 解析待定 SQL 语句 节点
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }
  // 解析 <mapper /> 节点
  private void configurationElement(XNode context) {
    try {
      // 获取 mapper 命名空间
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {// 命名空间不能为空, 因为用作唯一标识
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      // 设置 namespace
      builderAssistant.setCurrentNamespace(namespace);
      // 解析 <cache-ref /> 标签
      cacheRefElement(context.evalNode("cache-ref"));
      // 解析 <cache /> 标签
      cacheElement(context.evalNode("cache"));
      // 解析 <parameterMap /> 标签
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // 解析 <resultMap /> 标签
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // 解析 <sql /> 标签
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析 <select />, <insert />, <update />, <delete /> 标签
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }
  // 解析 <select />, <insert />, <update />, <delete /> 标签
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }
  // 解析 <select />, <insert />, <update />, <delete /> 标签(**)
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // 遍历 <select />, <insert />, <update />, <delete /> 标签集合
    for (XNode context : list) {
      // 创建 XMLStatementBuilder
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // 解析
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // 解析失败, 添加到 configuration incompleteStatements 中
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    // 获取 ResultMapResolver 集合, 遍历进行解析, 解析成功则移除元素
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    // 同步
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          // 解析
          iter.next().resolve();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    // 获取 CacheRefResolver 集合, 遍历进行解析, 解析成功则移除元素
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    // 同步
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          // 解析
          iter.next().resolveCacheRef();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    // 获取 XMLStatementBuilder 集合, 遍历进行解析, 解析成功则移除元素
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    // 同步
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          // 解析
          iter.next().parseStatementNode();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }
  // 解析 <cache-ref /> 标签
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // 获取指向的 namespace 名字, 并添加到 configuration 的 cacheRefMap 中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // 创建 CacheRef 解析器
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        // 执行解析
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // 如果解析失败, 添加到 configuration 的 incompleteCacheRefs
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }
  // 解析 <cache /> 标签
  private void cacheElement(XNode context) {
    if (context != null) {
      // 获取 Cache, 默认为 PerpetualCache(永久缓存 Cache)
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // 获取过期策略, 默认为  LruCache(LRU(最近最少使用)淘汰机制)
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // 缓存刷新间隔
      Long flushInterval = context.getLongAttribute("flushInterval");
      // 缓存大小
      Integer size = context.getIntAttribute("size");
      // 是否只读
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      // 是否阻塞
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // 获取 properties 属性
      Properties props = context.getChildrenAsProperties();
      // 创建 Cache 对象
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }
  // 解析 <parameterMap /> 标签
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }
  // 解析 <resultMap /> 标签
  private void resultMapElements(List<XNode> list) throws Exception {
    // 遍历 <resultMap /> 标签 集合
    for (XNode resultMapNode : list) {
      try {
        // 解析 单个 <resultMap /> 标签
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }
  // 解析 <resultMap /> 标签
  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }
  // 解析 <resultMap /> 标签
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // 获取 type 属性
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<>();
    resultMappings.addAll(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();
    // 遍历 <resultMap /> 子节点
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        // 处理 <constructor /> 节点
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        // 处理 <discriminator /> 节点
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        // 处理其它节点
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        // 将当前 XNode 节点构建成 ResultMapping, 添加到 resultMappings
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // 获取 id 属性
    String id = resultMapNode.getStringAttribute("id",
            resultMapNode.getValueBasedIdentifier());
    // 获取 extends 属性
    String extend = resultMapNode.getStringAttribute("extends");
    // 获取 autoMapping 属性
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // 创建 ResultMapResolver
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      // 解析
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      // 解析失败, 添加到 incompleteResultMaps 中
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }
  // 处理 <constructor /> 节点
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }
  // 处理 <discriminator /> 节点
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }
  // 解析 <sql /> 标签
  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }
  // 解析 <sql /> 标签
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      String databaseId = context.getStringAttribute("databaseId");
      // 获取 <sql /> id 属性
      String id = context.getStringAttribute("id");
      // 获取完整的 id 属性, namespace(mapper) + id(sql), 例如 `${namespace}.${id}`
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 判断 databaseId 是否与当前的匹配
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // 添加到 sqlFragments
        sqlFragments.put(id, context);
      }
    }
  }
  // 判断 <sql /> databaseId 是否与当前 configuration databaseId(requiredDatabaseId) 的匹配
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    // 如果 requiredDatabaseId 不为空
    if (requiredDatabaseId != null) {
      // equals 进行比较
      return requiredDatabaseId.equals(databaseId);
    }
    // 如果 requiredDatabaseId 为空, databaseId 不为空, 说明确实不匹配,返回 false
    if (databaseId != null) {
      return false;
    }
    // 到这里 requiredDatabaseId,databaseId 都为空, 进行接下来的处理
    // 判断是否已经存在
    if (!this.sqlFragments.containsKey(id)) {
      // 第一次不存在则返回 true(后面的解析会添加到 sqlFragments)
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    // 如果 sqlFragments 存在该 id
    XNode context = this.sqlFragments.get(id);
    // 判断 databaseId 属性是否为空(因为当前 databaseId 为 null, 只有都为 null,才匹配)
    return context.getStringAttribute("databaseId") == null;
  }
  // 将当前 XNode 节点构建成 ResultMapping
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      if (context.getStringAttribute("select") == null) {
        validateCollection(context, enclosingType);
        ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
        return resultMap.getId();
      }
    }
    return null;
  }

  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }
  // 绑定 xml namespace (绑定 mapper 接口)
  private void bindMapperForNamespace() {
    // 获取当前 namespace (命名空间), namespace 其实就是 mapper 接口类 全限定类名
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        // 获取绑定类型 (获取 xml 关联绑定的 mapper 接口类)
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        // 如果不存在 mapper 接口, 则添加
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          // 标记 namespace 已经添加, configuration.isResourceLoaded(resource)
          configuration.addLoadedResource("namespace:" + namespace);
          // 添加到 configuration
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
