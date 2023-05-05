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

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
// xml配置建造者, 主要负责解析 mybatis-config.xml 配置文件, 解析为 Configuration 对象(重要)
public class XMLConfigBuilder extends BaseBuilder {
  // 是否已解析
  private boolean parsed;
  // 基于 Java XPath 的解析器
  private final XPathParser parser;
  // 环境
  private String environment;
  // 反射器工厂
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    // 创建 Configuration 对象
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // 设置 Configuration 的 variables 属性
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  // 解析 XML 为 Configuration 对象(重要)
  // XPathParser 对象包含 XML Document 对象
  public Configuration parse() {
    // 确保配置只被解析一次
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    // 解析配置(重要)
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      // 解析 <properties /> 标签
      propertiesElement(root.evalNode("properties"));
      // 解析 <settings /> 标签
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 加载自定义 VFS 实现类
      loadCustomVfs(settings);
      // 加载自定义 LOG 实现类
      loadCustomLogImpl(settings);
      // 解析 <typeAliases /> 标签
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析 <plugins /> 标签
      pluginElement(root.evalNode("plugins"));
      // 解析 <objectFactory /> 标签
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析 <objectWrapperFactory /> 标签
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析 <reflectorFactory /> 标签
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 将 <settings /> 标签内属性, 赋值到 Configuration 对象中
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析 <environments /> 标签
      environmentsElement(root.evalNode("environments"));
      // 解析 <databaseIdProvider /> 标签
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析 <typeHandlers /> 标签
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析 <mappers /> 标签
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
  // 解析 <settings /> 标签
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 校验每个属性, 在 configuration 中, 都有对应的 setting 方法(如果没有, 则抛异常)
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      // 如果没有 setter, 则抛异常
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }
  // 加载自定义 VFS 实现类
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    // 获取 vfs 属性
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      // ','分隔, 拆分 VFS类名数组
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
            // 获取 VFS 类
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          // 加载到 configuration
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }
  // 加载自定义 LOG 实现类
  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }
  // 解析 <typeAliases /> 标签
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      // 遍历子节点
      for (XNode child : parent.getChildren()) {
        // 指定包, 则注册包下每个类(别名映射)
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          // 注册包下的每个类(别名映射)
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          // 指定为类的话, 注册类和别名
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }
  // 解析 <plugins /> 标签
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历 <plugins /> 标签
      for (XNode child : parent.getChildren()) {
        // 获取拦截器
        String interceptor = child.getStringAttribute("interceptor");
        // 获取配置的属性
        Properties properties = child.getChildrenAsProperties();
        // 创建拦截器实例
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        // 设置属性
        interceptorInstance.setProperties(properties);
        // 添加拦截器(添加到拦截器链)
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }
  // 解析 <objectFactory /> 标签
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取 objectFactory 实现类
      String type = context.getStringAttribute("type");
      // 获取属性
      Properties properties = context.getChildrenAsProperties();
      // 创建实例
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置属性
      factory.setProperties(properties);
      // 设置 objectFactory
      configuration.setObjectFactory(factory);
    }
  }
  // 解析 <objectWrapperFactory /> 标签
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取 objectWrapperFactory 实现类
      String type = context.getStringAttribute("type");
      // 创建实例
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置
      configuration.setObjectWrapperFactory(factory);
    }
  }
  // 解析 <reflectorFactory /> 标签
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 创建 reflectorFactory 实例, 并设置到 configuration
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }
  // 解析 <properties /> 标签
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 读取子标签为 Properties 对象
      Properties defaults = context.getChildrenAsProperties();
      // 获取 resource 和 url 属性
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      // resource 和 url 只能二选一
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      // 读取本地 Properties 配置文件到 defaults 中
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        // 读取远程 Properties 配置文件到 defaults 中
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      // 覆盖 configuration 中的 Properties 对象到 defaults 中
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      // 设置 defaults 到 parser 和 configuration 中
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }
  // 将 <settings /> 标签内属性, 赋值到 Configuration 对象中
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }
  // 解析 <environments /> 标签
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        // 获取 < environments /> 中的 default environment
        environment = context.getStringAttribute("default");
      }
      // 遍历
      for (XNode child : context.getChildren()) {
        // 获取 environment id
        String id = child.getStringAttribute("id");
        // 加载指定环境
        if (isSpecifiedEnvironment(id)) {
          // 事务工厂
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          // 数据源工厂
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          // 从数据源工厂中, 获取数据源
          DataSource dataSource = dsFactory.getDataSource();
          // 环境 配置事务工厂,数据源
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          // 设置环境
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }
  // 解析 <databaseIdProvider /> 标签
  // 配置数据库厂商之间的 databaseId (读取 Connection DatabaseMetaData getDatabaseProductName 方法,获取实际数据库产品名称)
  // 由于通常情况下这些字符串都非常长而且相同产品的不同版本会返回不同的值，所以你可能想通过设置属性别名来使其变短
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      // 这里 获取 "DB_VENDOR" 的原因是因为 "DB_VENDOR" 是 VendorDatabaseIdProvider 的别名
      // Configuration.java (line 196)     typeAliasRegistry.registerAlias("DB_VENDOR", VendorDatabaseIdProvider.class);
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {// 向后兼容
        type = "DB_VENDOR";
      }
      // 获取属性
      Properties properties = context.getChildrenAsProperties();
      // 获取实例
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置属性
      databaseIdProvider.setProperties(properties);
    }
    // 获取环境
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      // 设置 databaseId
      configuration.setDatabaseId(databaseId);
    }
  }
  // 解析 <transactionManager /> 标签
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 创建 transactionManager 实例
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }
  // 解析 <dataSource /> 标签
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 创建 dataSource 对象实例, 设置属性
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }
  // 解析 <typeHandlers /> 标签
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      // 遍历
      for (XNode child : parent.getChildren()) {
        // 如果是 package 标签, 扫描整个包
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          // 否则 注册单个, 注册该 typeHandler
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          // 注册
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }
  // 解析 <mappers /> 标签
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历
      for (XNode child : parent.getChildren()) {
        // 如果是 package 标签, 则扫描注册该包下的所有类
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          // 否则是 单个 mapper, 获取其属性,进行注册
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          // resource, url, class 三种方式选其一, 进行 mapper注册
          if (resource != null && url == null && mapperClass == null) {// 使用相对于类路径的资源引用
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {// 使用 url
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {// 使用指定的 class
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }
  // 判断是否是指定的环境(加载指定环境)
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
