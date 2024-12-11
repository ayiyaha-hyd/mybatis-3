package com.hyd.ayiyaha.test;

import com.hyd.ayiyaha.entity.ExtensiveSubject;
import com.hyd.ayiyaha.entity.Subject;
import com.hyd.ayiyaha.mapper.ExtensiveSubjectMapper;
import com.hyd.ayiyaha.mapper.SubjectMapper;
import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;


public class TEST001 {
  private static SqlSessionFactory sqlSessionFactory;
  @BeforeAll
  static void setUp() throws Exception {
    // create a SqlSessionFactory
    try (Reader reader = Resources.getResourceAsReader("com/hyd/ayiyaha/mybatis-config.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    // populate in-memory database
    // 获取JDBC connection,执行初始化脚本为之后测试做准备
    BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
      "com/hyd/ayiyaha/init_db.sql");
  }

  @Test
  public void subjectSelectAllTest() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      SubjectMapper mapper = sqlSession.getMapper(SubjectMapper.class);
      Subject queryBean = new Subject();
      queryBean.setAge(10);
      List<Subject> list = mapper.selectAll(queryBean);
      assertNotNull(list);
      System.out.println(list);
    }
  }

  @Test
  public void subjectSelectOneTest() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      SubjectMapper mapper = sqlSession.getMapper(SubjectMapper.class);
      Subject queryBean = new Subject();
      queryBean.setId(1);
      Subject subject = mapper.selectOne(queryBean);
      assertNotNull(subject);
      System.out.println(subject);
    }
  }


  @Test
  public void extensiveSubjectSelectAllTest() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      ExtensiveSubjectMapper mapper = sqlSession.getMapper(ExtensiveSubjectMapper.class);
      ExtensiveSubject queryBean = new ExtensiveSubject();
      List<ExtensiveSubject> list = mapper.selectAll(queryBean);
      assertNotNull(list);
    }
  }

  @Test
  public void extensiveSubjectSelectOneTest() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      ExtensiveSubjectMapper mapper = sqlSession.getMapper(ExtensiveSubjectMapper.class);
      ExtensiveSubject queryBean = new ExtensiveSubject();
      queryBean.setaBlob("aaaaaabbbbbb");
      ExtensiveSubject extensiveSubject = mapper.selectOne(queryBean);
      assertNotNull(extensiveSubject);
    }
  }
}
