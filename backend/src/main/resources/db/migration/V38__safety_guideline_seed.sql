-- V38: 안전 가이드라인 템플릿 시드 데이터
-- REQ-SAFETY-001 — 공개 가이드라인 조회 화면 개발·검증용

INSERT INTO safety_guideline_template
    (code, name, description, applicable_industry_codes, applicable_grades,
     structure, status, version, review_status, reviewed_by, created_by)
VALUES
-- 건설업 고소작업 가이드라인
('GL-CONST-001', '건설업 고소작업 안전 가이드라인',
 '건설 현장에서 2m 이상의 고소작업 시 적용되는 추락 방지 안전 기준 및 체크리스트입니다. 안전대 착용, 추락 방지망 설치, 작업발판 설치 기준을 포함합니다.',
 ARRAY['41001', '41002', '41003'], ARRAY['A', 'B'],
 '{"sections": ["준비", "작업", "완료"]}',
 'PUBLISHED', 'v1.0', 'APPROVED', 1, 1),

-- 제조업 기계설비 가이드라인
('GL-MFCT-001', '제조업 기계설비 협착사고 예방 가이드라인',
 '프레스, 컨베이어 등 기계설비 작업 시 협착·절단 사고 예방을 위한 안전 절차입니다. LOTO(잠금·태그아웃) 절차, 방호장치 점검, 작업 전 안전확인 체크리스트를 포함합니다.',
 ARRAY['28910', '28920', '24110'], ARRAY['A', 'B', 'C'],
 '{"sections": ["LOTO", "점검", "복구"]}',
 'PUBLISHED', 'v1.0', 'APPROVED', 1, 1),

-- 화학업 유해물질 취급 가이드라인
('GL-CHEM-001', '화학업 유해화학물질 취급 안전 가이드라인',
 '유해화학물질 취급·이송·보관 시 누출 사고 예방을 위한 안전 절차입니다. 개인보호장비 착용 기준, 비상대응 절차, 정기 배관 점검 기준을 포함합니다.',
 ARRAY['20121', '20122', '20190'], ARRAY['A', 'B'],
 '{"sections": ["취급", "보관", "비상대응"]}',
 'PUBLISHED', 'v1.0', 'APPROVED', 1, 1),

-- 물류 지게차 운행 가이드라인
('GL-LGST-001', '물류업 지게차 안전운행 가이드라인',
 '물류창고 내 지게차 운행 시 보행자 충돌 예방을 위한 안전 기준입니다. 차량·보행자 동선 분리, 후방 감지 장치 점검, 운행 전 일상 점검 체크리스트를 포함합니다.',
 ARRAY['52100', '52910'], ARRAY['B', 'C'],
 '{"sections": ["운행전점검", "운행중", "주차"]}',
 'PUBLISHED', 'v1.0', 'APPROVED', 1, 1),

-- 전기공사 활선작업 가이드라인
('GL-ELEC-001', '전기공사업 활선작업 금지 및 정전 작업 가이드라인',
 '전기 배선·배전반 점검 시 감전 사고 예방을 위한 정전 작업 절차입니다. 활선 작업 금지 원칙, 정전 확인 절차, 절연 보호구 착용 기준을 포함합니다.',
 ARRAY['43211', '43212'], ARRAY['A', 'B'],
 '{"sections": ["정전절차", "작업", "복전절차"]}',
 'PUBLISHED', 'v1.0', 'APPROVED', 1, 1);

-- 체크리스트 아이템 삽입 (template_id는 서브쿼리로 참조)

-- GL-CONST-001: 건설업 고소작업
INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '준비', '안전대(안전벨트) 착용 상태를 작업 전 점검한다.', 'CRITICAL', 1, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '준비', '추락 방지망이 작업 구역 하부에 설치되어 있는지 확인한다.', 'CRITICAL', 2, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '준비', '안전대 부착설비(생명줄, 앵커포인트)가 충분한 강도를 확보하고 있는지 확인한다.', 'CRITICAL', 3, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '준비', '작업발판(비계)의 폭, 강도, 고정 상태를 점검한다.', 'HIGH', 4, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '작업', '안전대를 항시 생명줄에 연결한 상태로 작업한다.', 'CRITICAL', 5, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '작업', '공구는 공구 연결줄로 고정하여 낙하를 방지한다.', 'HIGH', 6, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '작업', '하부 작업구역에 관계자 외 출입을 통제한다.', 'HIGH', 7, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '완료', '작업 완료 후 안전대 및 보호구를 지정 장소에 보관한다.', 'NORMAL', 8, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CONST-001';

-- GL-MFCT-001: 제조업 기계설비
INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, 'LOTO', '작업 전 기계 전원을 완전히 차단하고 잠금장치(Lock)를 설치한다.', 'CRITICAL', 1, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-MFCT-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, 'LOTO', '잠금장치에 경고 태그(Tag)를 부착하고 작업자 이름을 기재한다.', 'CRITICAL', 2, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-MFCT-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, 'LOTO', '잔류 에너지(유압·공압·중력)를 완전히 해제한다.', 'CRITICAL', 3, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-MFCT-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '점검', '협착 위험 부위에 방호장치(덮개, 가드)가 설치되어 있는지 확인한다.', 'HIGH', 4, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-MFCT-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '점검', '비상정지 버튼의 정상 작동 여부를 확인한다.', 'HIGH', 5, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-MFCT-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '복구', '작업 완료 후 공구 및 이물질을 기계 내부에서 제거한다.', 'HIGH', 6, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-MFCT-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '복구', '모든 작업자가 안전구역으로 대피 후 잠금장치를 해제한다.', 'CRITICAL', 7, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-MFCT-001';

-- GL-CHEM-001: 화학업 유해물질
INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '취급', '취급 물질의 물질안전보건자료(MSDS)를 확인하고 취급 주의사항을 숙지한다.', 'CRITICAL', 1, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CHEM-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '취급', '방독마스크, 내화학성 장갑, 보안경 등 개인보호장비를 착용한다.', 'CRITICAL', 2, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CHEM-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '취급', '작업구역 내 환기 상태를 확인하고 강제 환기 설비를 가동한다.', 'HIGH', 3, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CHEM-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '보관', '배관 이음부, 밸브 등의 누출 여부를 정기 점검한다.', 'HIGH', 4, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CHEM-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '보관', '긴급차단밸브의 위치를 숙지하고 자동 작동 여부를 점검한다.', 'CRITICAL', 5, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CHEM-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '비상대응', '누출 발생 시 즉시 상급자에게 보고하고 대피 경보를 발령한다.', 'CRITICAL', 6, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CHEM-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '비상대응', '흡입 피해자 발생 시 신선한 공기가 있는 곳으로 신속히 이송하고 119에 신고한다.', 'CRITICAL', 7, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-CHEM-001';

-- GL-LGST-001: 물류 지게차
INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '운행전점검', '제동장치, 조향장치, 경보장치의 정상 작동 여부를 확인한다.', 'HIGH', 1, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-LGST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '운행전점검', '후방 감지 센서 및 카메라의 정상 작동 여부를 확인한다.', 'HIGH', 2, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-LGST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '운행중', '차량·보행자 동선 분리 구역 표시를 준수하고 보행자 구역에 진입하지 않는다.', 'CRITICAL', 3, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-LGST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '운행중', '교차로 및 시야 사각지대에서는 반드시 일시 정지 후 주변을 확인한다.', 'HIGH', 4, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-LGST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '운행중', '제한 속도(실내 10km/h)를 준수한다.', 'HIGH', 5, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-LGST-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '주차', '작업 완료 후 포크를 완전히 내리고 주차 브레이크를 체결한다.', 'NORMAL', 6, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-LGST-001';

-- GL-ELEC-001: 전기 활선작업
INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '정전절차', '작업 전 반드시 차단기를 OFF하고 잠금장치를 설치한다.', 'CRITICAL', 1, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-ELEC-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '정전절차', '검전기로 충전 여부를 확인한 후 접지를 설치한다.', 'CRITICAL', 2, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-ELEC-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '정전절차', '차단기 잠금장치에 작업 중 경고 표시를 부착한다.', 'HIGH', 3, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-ELEC-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '작업', '절연장갑, 절연화, 절연안전모 등 절연 보호구를 착용한다.', 'CRITICAL', 4, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-ELEC-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '작업', '활선 작업은 절대 금지하며, 부득이한 경우 관할 기관의 허가를 받는다.', 'CRITICAL', 5, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-ELEC-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '복전절차', '작업 완료 후 공구 및 이물질을 제거하고 접지를 해제한다.', 'HIGH', 6, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-ELEC-001';

INSERT INTO safety_checklist_item
    (template_id, category, item_text, severity, sort_order, status)
SELECT t.id, '복전절차', '모든 작업자가 안전구역 복귀 후 잠금장치를 해제하고 차단기를 ON한다.', 'CRITICAL', 7, 'ACTIVE'
FROM safety_guideline_template t WHERE t.code = 'GL-ELEC-001';
