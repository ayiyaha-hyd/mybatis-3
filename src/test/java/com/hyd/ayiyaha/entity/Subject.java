package com.hyd.ayiyaha.entity;

import java.util.Date;
import java.util.StringJoiner;

public class Subject {
  //fields

  private int id;
  private String name;
  private int age;
  private int height;
  private int weight;
  private boolean active;
  private Date dt;

  //getter and setter

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public int getWeight() {
    return weight;
  }

  public void setWeight(int weight) {
    this.weight = weight;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Date getDt() {
    return dt;
  }

  public void setDt(Date dt) {
    this.dt = dt;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", Subject.class.getSimpleName() + "[", "]")
      .add("id=" + id)
      .add("name='" + name + "'")
      .add("age=" + age)
      .add("height=" + height)
      .add("weight=" + weight)
      .add("active=" + active)
      .add("dt=" + dt)
      .toString();
  }
}
