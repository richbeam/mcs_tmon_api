
package com.melchi.common.constant;



public interface BaseConst {
	
public class Code {
		// From ApiConstants 
		//공통영역처리
		public final static int SUCCESS = 200;
		public final static String API_SUCCESS_MSG = "정상처리되었습니다.";
		 
	}
	
	public class SessionInfo {
		public	final	static	String	SESSION_USER	=	"session_user";
	}

	public class shippingmethod{
		public final static String S_01 = "01"; //택배
		public final static String S_02 = "01"; //직배송
	}

	public class ProdStauts {
		public final static String STAUTS_01 = "01"; //상품 등록
		public final static String STAUTS_02 = "02"; //상품 수정
		public final static String STAUTS_03 = "03"; //가격수정
		public final static String STAUTS_04 = "04"; //판매중지
		public final static String STAUTS_05 = "05"; //판매중지 해제
		public final static String STAUTS_06 = "06"; //재고수량 수정
		public final static String STAUTS_07 = "07"; //재고수량 수정 API
		public final static String STAUTS_08 = "08"; //매핑정보 추가
		public final static String STAUTS_99 = "99"; //상품 승인 목록 조회
		public final static String STAUTS_98 = "98"; //상품 상세 조회
	}
	 
	public class OrderStauts {
		public final static String STAUTS_01 = "01"; //입금대기
		public final static String STAUTS_02 = "02"; //결제완료
		public final static String STAUTS_03 = "03"; //주문확인
		public final static String STAUTS_04 = "04"; //배송중
		public final static String STAUTS_05 = "05"; //배송완료
		public final static String STAUTS_06 = "06"; //취소요청
		public final static String STAUTS_07 = "07"; //취소완료
		public final static String STAUTS_08 = "08"; //교환요청
		public final static String STAUTS_09 = "09"; //교환확인중
		public final static String STAUTS_10 = "10"; //교환회수완료
		public final static String STAUTS_11 = "11"; //교환배송중
		public final static String STAUTS_12 = "12"; //교환배송완료
		public final static String STAUTS_13 = "13"; //반품요청
		public final static String STAUTS_14 = "14"; //반품확인중
		public final static String STAUTS_15 = "15"; //반품회수완료
		public final static String STAUTS_16 = "16"; //환불완료(이머니)
		public final static String STAUTS_17 = "17"; //환불요청(계좌)
		public final static String STAUTS_18 = "18"; //환불완료(계좌)
		public final static String STAUTS_19 = "19"; //환불완료(신용카드)
		public final static String STAUTS_20 = "20"; //정산완료
		public final static String STAUTS_21 = "21"; //취소철회
		public final static String STAUTS_22 = "22"; //교환철회
		public final static String STAUTS_23 = "23"; //반품철회
		public final static String STAUTS_24 = "24"; //취소거부
		public final static String STAUTS_25 = "25"; //교환거부
		public final static String STAUTS_26 = "26"; //반품거부 
		public final static String STAUTS_27 = "27"; //판매거부
		public final static String STAUTS_28 = "28"; //배송번호채번
		
		public final static String STAUTS_61 = "61"; //주문확인 시 취소요청
		public final static String STAUTS_77 = "77"; //오류발생
		
	}
}
