package com.hyd.ayiyaha.entity;

import java.util.StringJoiner;

public class ExtensiveSubject {
  //fields
  private byte aByte;
  private short aShort;
  private char aChar;
  private int anInt;
  private long aLong;
  private float aFloat;
  private double aDouble;
  private boolean aBoolean;
  private String aString;

  // enum types
  private TestEnum anEnum;

  // array types

  // string to lob types:
  private String aClob;
  private String aBlob;

  public enum TestEnum {
    AVALUE, BVALUE, CVALUE;
  }

  // getter and setter


  public byte getaByte() {
    return aByte;
  }

  public void setaByte(byte aByte) {
    this.aByte = aByte;
  }

  public short getaShort() {
    return aShort;
  }

  public void setaShort(short aShort) {
    this.aShort = aShort;
  }

  public char getaChar() {
    return aChar;
  }

  public void setaChar(char aChar) {
    this.aChar = aChar;
  }

  public int getAnInt() {
    return anInt;
  }

  public void setAnInt(int anInt) {
    this.anInt = anInt;
  }

  public long getaLong() {
    return aLong;
  }

  public void setaLong(long aLong) {
    this.aLong = aLong;
  }

  public float getaFloat() {
    return aFloat;
  }

  public void setaFloat(float aFloat) {
    this.aFloat = aFloat;
  }

  public double getaDouble() {
    return aDouble;
  }

  public void setaDouble(double aDouble) {
    this.aDouble = aDouble;
  }

  public boolean isaBoolean() {
    return aBoolean;
  }

  public void setaBoolean(boolean aBoolean) {
    this.aBoolean = aBoolean;
  }

  public String getaString() {
    return aString;
  }

  public void setaString(String aString) {
    this.aString = aString;
  }

  public TestEnum getAnEnum() {
    return anEnum;
  }

  public void setAnEnum(TestEnum anEnum) {
    this.anEnum = anEnum;
  }

  public String getaClob() {
    return aClob;
  }

  public void setaClob(String aClob) {
    this.aClob = aClob;
  }

  public String getaBlob() {
    return aBlob;
  }

  public void setaBlob(String aBlob) {
    this.aBlob = aBlob;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", ExtensiveSubject.class.getSimpleName() + "[", "]")
      .add("aByte=" + aByte)
      .add("aShort=" + aShort)
      .add("aChar=" + aChar)
      .add("anInt=" + anInt)
      .add("aLong=" + aLong)
      .add("aFloat=" + aFloat)
      .add("aDouble=" + aDouble)
      .add("aBoolean=" + aBoolean)
      .add("aString='" + aString + "'")
      .add("anEnum=" + anEnum)
      .add("aClob='" + aClob + "'")
      .add("aBlob='" + aBlob + "'")
      .toString();
  }
}
