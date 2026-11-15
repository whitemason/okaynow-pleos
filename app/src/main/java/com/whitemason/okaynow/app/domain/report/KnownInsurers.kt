package com.whitemason.okaynow.app.domain.report

/**
 * C0 설정에서 고를 수 있는 보험사의 사고접수·긴급출동 번호(개인용 자동차보험 판매사).
 * 2026-11-05 각 보험사 공식 사이트에서 확인했다. 번호는 바뀔 수 있으니 C0에서 직접 고칠 수 있다.
 * 출처(공식 페이지):
 * - 삼성화재 samsungfire.com/v2/html/claim/02/C_020_010_001.html (사고접수 ARS 1번)
 * - 현대해상 hi.co.kr 사고접수 안내 (ARS 2번)
 * - DB손해보험 idbins.com/pc/bizxpress/ask/cc/FWCLAV1126.shtm
 * - KB손해보험 kbinsure.co.kr/CG206010001.ec (대표콜센터, 사고접수·보상 24시간)
 * - 메리츠화재 meritzfire.com 고객센터("사고접수/긴급출동")
 * - 한화손해보험 hwgeneralins.com 고객상담센터·전국 보상센터
 * - 롯데손해보험 lotteins.co.kr/web/C/D/K/cdk160.jsp (사고접수 ARS 2번)
 * - 흥국화재 heungkukfire.co.kr/FRW/helpdesk/useGuideCallCenterARS.do (사고접수 ARS 1번)
 * - 예별손해보험(구 MG손해보험) yebyeol.co.kr/RW031030DM.scp (사고접수 ARS 2번, 전화 접수 평일 9~18시 표기)
 * - 캐롯(한화손해보험 운영) carrotins.com/desktop/reward/accident/info/ (사고접수 ARS 1번, 24시간)
 * - AXA손해보험 axa.co.kr 고객센터 (사고접수 ARS 3번, 24시간)
 * - 하나손해보험 hanainsure.co.kr 고객센터(사고접수/긴급출동 24시간)
 * 신한EZ손해보험은 자동차보험을 팔지 않아 뺐다.
 */
val KNOWN_INSURERS: List<Insurer> = listOf(
    Insurer("삼성화재", "1588-5114"),
    Insurer("현대해상", "1588-5656"),
    Insurer("DB손해보험", "1588-0100"),
    Insurer("KB손해보험", "1544-0114"),
    Insurer("메리츠화재", "1566-7711"),
    Insurer("한화손해보험", "1566-8000"),
    Insurer("롯데손해보험", "1588-3344"),
    Insurer("흥국화재", "1688-1688"),
    Insurer("예별손해보험", "1588-5959"),
    Insurer("캐롯", "1566-0300"),
    Insurer("AXA손해보험", "1566-1566"),
    Insurer("하나손해보험", "1566-3000"),
)
