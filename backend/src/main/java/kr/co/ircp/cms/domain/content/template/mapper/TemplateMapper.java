package kr.co.ircp.cms.domain.content.template.mapper;

import kr.co.ircp.cms.domain.content.template.entity.Template;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 템플릿 MyBatis 매퍼.
 * REQ-CONTENT-004-D: 템플릿 CRUD
 *
 */
@Mapper
public interface TemplateMapper {

    /** 전체 템플릿 목록 조회 */
    List<Template> findAll();

    /** ID로 단건 조회 */
    Optional<Template> findById(@Param("id") Long id);

    /** 해당 템플릿을 사용 중인 page 수 조회 */
    long countPagesByTemplateId(@Param("templateId") Long templateId);

    /** 템플릿 생성 */
    void insert(Template template);

    /** 템플릿 수정 */
    int update(Template template);

    /** 상태 변경 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
