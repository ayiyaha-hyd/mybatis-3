package com.hyd.ayiyaha.mapper;

import com.hyd.ayiyaha.entity.ExtensiveSubject;

import java.util.List;

public interface ExtensiveSubjectMapper {
  List<ExtensiveSubject> selectAll(ExtensiveSubject extensiveSubject);
  ExtensiveSubject selectOne(ExtensiveSubject extensiveSubject);
}
