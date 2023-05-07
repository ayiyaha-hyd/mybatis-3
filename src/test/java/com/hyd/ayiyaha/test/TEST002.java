package com.hyd.ayiyaha.test;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;

public class TEST002 {
  @Test public void PropertyTokenizer_test(){
    PropertyTokenizer propertyTokenizer = new PropertyTokenizer("order[3].item[1].name");
    PropertyTokenizer cur = propertyTokenizer;
    System.out.printf("%s, %s, %s, %s, %s%n",cur,cur.getName(),cur.getIndex(),cur.getIndexedName(),cur.getChildren());
    while (cur.hasNext()){
      PropertyTokenizer next = cur.next();
      System.out.printf("%s, %s, %s, %s, %s%n",next,next.getName(),next.getIndex(),next.getIndexedName(),next.getChildren());
      cur = next;
    }
  }
  @Test public void MetaClass_test(){
    MetaClass metaClass = MetaClass.forClass(PropertyTokenizer.class, new DefaultReflectorFactory());
    String[] getterNames = metaClass.getGetterNames();
    System.out.println(Arrays.toString(getterNames));
  }
}
