package com.hyd.ayiyaha.mapper;

import com.hyd.ayiyaha.entity.Subject;

import java.util.List;

public interface SubjectMapper {
  List<Subject> selectAll(Subject subject);
  Subject selectOne(Subject subject);
}
