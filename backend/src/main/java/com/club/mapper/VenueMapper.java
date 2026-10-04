package com.club.mapper;

import com.club.entity.Venue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 场地表数据访问。
 */
@Mapper
public interface VenueMapper {

    @Select("SELECT * FROM t_venue ORDER BY venue_id")
    List<Venue> selectAll();

    @Select("SELECT * FROM t_venue WHERE venue_id = #{venueId}")
    Venue selectById(Long venueId);
}
