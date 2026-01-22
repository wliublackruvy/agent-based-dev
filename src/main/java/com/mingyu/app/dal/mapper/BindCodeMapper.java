// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mingyu.app.dal.entity.BindCode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BindCodeMapper extends BaseMapper<BindCode> {
}
