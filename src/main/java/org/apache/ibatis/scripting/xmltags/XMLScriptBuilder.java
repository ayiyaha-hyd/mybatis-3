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
package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Clinton Begin
 */
// XML 动态语句构建器, 将 SQL 解析成 SqlSource
public class XMLScriptBuilder extends BaseBuilder {
  // 当前 SQL 的 XNode 对象
  private final XNode context;
  // 是否是动态节点
  private boolean isDynamic;
  // SQL 方法入参
  private final Class<?> parameterType;
  // NodeHandler 映射
  private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();

  public XMLScriptBuilder(Configuration configuration, XNode context) {
    this(configuration, context, null);
  }

  public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
    super(configuration);
    this.context = context;
    this.parameterType = parameterType;
    // 初始化 NodeHandler 映射
    initNodeHandlerMap();
  }

  // 初始化 NodeHandler 映射(<trim/>,<where/>,<set/>,<foreach/>,<if/>,<choose/>,<when/>,<otherwise/>,<bind/>等标签)
  private void initNodeHandlerMap() {
    nodeHandlerMap.put("trim", new TrimHandler());
    nodeHandlerMap.put("where", new WhereHandler());
    nodeHandlerMap.put("set", new SetHandler());
    nodeHandlerMap.put("foreach", new ForEachHandler());
    nodeHandlerMap.put("if", new IfHandler());
    nodeHandlerMap.put("choose", new ChooseHandler());
    nodeHandlerMap.put("when", new IfHandler());
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    nodeHandlerMap.put("bind", new BindHandler());
  }
  // 解析 SQL 为 SqlSource(**)
  public SqlSource parseScriptNode() {
    // 解析 SQL
    MixedSqlNode rootSqlNode = parseDynamicTags(context);
    // 创建 SqlSource 对象
    SqlSource sqlSource;
    if (isDynamic) {
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    } else {
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }
  // 解析动态标签
  protected MixedSqlNode parseDynamicTags(XNode node) {
    // 创建 SqlNode 集合
    List<SqlNode> contents = new ArrayList<>();
    // 遍历 SQL 节点所有子节点
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      // 当前节点
      XNode child = node.newXNode(children.item(i));
      // 如果当前节点类型为 CDATA_SECTION_NODE 或者 TEXT_NODE
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
        // 获取内容
        String data = child.getStringBody("");
        // 创建 TextSqlNode
        TextSqlNode textSqlNode = new TextSqlNode(data);
        // 如果是动态的 TextSqlNode
        if (textSqlNode.isDynamic()) {
          // 添加到 SqlNode 集合
          contents.add(textSqlNode);
          // 标识为动态标签
          isDynamic = true;
        } else {
          // 重新用 StaticTextSqlNode 构建 data, 添加到 SqlNode 集合
          contents.add(new StaticTextSqlNode(data));
        }// 如果类型是 ELEMENT_NODE
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628
        // 根据 NodeName 获取对应的 NodeHandler
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        // 执行 NodeHandler 处理
        handler.handleNode(child, contents);
        // 标记为动态 SQL
        isDynamic = true;
      }
    }
    // 返回 MixedSqlNode
    return new MixedSqlNode(contents);
  }

  // Node 处理器(内部类)
  private interface NodeHandler {
    // 处理 Node
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }
  // <bind/> 标签处理器
  private class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析 name,value 属性
      final String name = nodeToHandle.getStringAttribute("name");
      final String expression = nodeToHandle.getStringAttribute("value");
      // 创建 VarDeclSqlNode
      final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
      // 添加到 targetContents 集合
      targetContents.add(node);
    }
  }
  // <trim/> 标签处理器
  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的 SQL 节点为 MixedSqlNode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获得 prefix,prefixOverrides,suffix,suffixOverrides 属性
      String prefix = nodeToHandle.getStringAttribute("prefix");
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      String suffix = nodeToHandle.getStringAttribute("suffix");
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      // 创建 TrimSqlNode 对象
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      // 添加到 targetContents 集合中
      targetContents.add(trim);
    }
  }
  // <where/> 标签处理器
  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的 SQL 节点为 MixedSqlNode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 创建 WhereSqlNode 对象
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      // 添加到 targetContents 集合中
      targetContents.add(where);
    }
  }
  // <set/> 标签处理器
  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的 SQL 节点为 MixedSqlNode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      targetContents.add(set);
    }
  }
  // <foreach/> 标签处理器
  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的 SQL 节点为 MixedSqlNode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获取 collection,item,index,open,close,separator 属性
      String collection = nodeToHandle.getStringAttribute("collection");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
      targetContents.add(forEachSqlNode);
    }
  }
  // <if/> 标签处理器
  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的 SQL 节点为 MixedSqlNode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // test 属性
      String test = nodeToHandle.getStringAttribute("test");
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      targetContents.add(ifSqlNode);
    }
  }
  // <otherwise/> 标签处理器
  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      targetContents.add(mixedSqlNode);
    }
  }
  // <choose/> 标签处理器
  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> whenSqlNodes = new ArrayList<>();
      List<SqlNode> otherwiseSqlNodes = new ArrayList<>();
      // 解析 <when/>,<otherwise/> 标签
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
      List<XNode> children = chooseSqlNode.getChildren();
      for (XNode child : children) {
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
