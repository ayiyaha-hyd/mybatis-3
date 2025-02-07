package com.hyd.ayiyaha.test;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TEST002 {
  @Test public void PropertyTokenizer_test(){
    PropertyTokenizer propertyTokenizer = new PropertyTokenizer("order[3].item[1].name");
    PropertyTokenizer cur = propertyTokenizer;
    System.out.printf("%s, %s, %s, %s%n",cur.getName(),cur.getIndex(),cur.getIndexedName(),cur.getChildren());
    while (cur.hasNext()){
      PropertyTokenizer next = cur.next();
      System.out.printf("%s, %s, %s, %s%n",next.getName(),next.getIndex(),next.getIndexedName(),next.getChildren());
      cur = next;
    }
  }
  @Test public void MetaClass_test(){
    MetaClass metaClass = MetaClass.forClass(PropertyTokenizer.class, new DefaultReflectorFactory());
    String[] getterNames = metaClass.getGetterNames();
    System.out.println(Arrays.toString(getterNames));
  }

  @Test public void testJavaStreamFindFirstAndFindAny(){
    List<String> list = new ArrayList<>(Arrays.asList("a","b","c"));
    list.stream().filter("d"::equals).findFirst().ifPresent(System.out::println);
    list.stream().filter("d"::equals).findAny().ifPresent(System.out::println);
  }
}
