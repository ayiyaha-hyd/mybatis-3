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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
// 动态属性解析器
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   *   The default value is {@code false} (indicate disable a default value on placeholder)
   *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   *   The default separator is {@code ":"}.
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  private static final String ENABLE_DEFAULT_VALUE = "false";
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }
  // 基于variables对象, 替换 string 字符串中的动态属性, 返回替换(解析)后的结果
  public static String parse(String string, Properties variables) {
    // 从给定的变量构造变量处理器
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    // 创建通用标记解析器
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    // 通用标记解析器解析标记返回最终结果(调用内部处理器, 对动态变量进行处理,此处为替换为variables属性值)
    // 替换"${value}"里边的动态值为实际值
    return parser.parse(string);
  }
  // 静态内部类, 变量 Token 处理器(Token就是标记的意思)
  // 将动态值替换为实际值, 例如:
  // jdbc.username=xxx;
  // username=${jdbc.username};
  // 将 ${jdbc.username} 替换为 xxx, 替换后字符串为 username=xxx
  private static class VariableTokenHandler implements TokenHandler {
    private final Properties variables;
    // 是否开启默认值
    private final boolean enableDefaultValue;
    // 默认值分隔符
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }
    // 获取 property 属性值
    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    // 处理标记(变量标记处理器具体处理逻辑)
    @Override
    public String handleToken(String content) {
      if (variables != null) {
        String key = content;
        // 如果开启了默认值
        if (enableDefaultValue) {
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          if (separatorIndex >= 0) {
            key = content.substring(0, separatorIndex);
            // 例如 "jdbc.username:tom" 如果property没有"jdbc.username",会使用分隔符(默认为':')后的"tom"
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue);
          }
        }
        // 从 variables 获取变量实际值
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      return "${" + content + "}";
    }
  }

}
