package com.avemonica.avemusic.user.provider.mapper;

import com.avemonica.avemusic.user.provider.entity.UserDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}