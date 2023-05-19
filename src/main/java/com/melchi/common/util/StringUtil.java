package com.melchi.common.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
	//real
	  private static final String SECRET_KEY = "Mr4wqNdt7M9R10Qb"; //비밀키 노출에 주의! 개발/리얼환경 각각 별도로 전달됩니다.
	//test
	//private static final String SECRET_KEY = "prnRZ6u0AqZAG6jG"; //비밀키 노출에 주의! 개발/리얼환경 각각 별도로 전달됩니다.
	/**
	 * 문자열이 null이나 공백인지를 체크
	 * 
	 * @param value
	 * @return
	 */
	public static boolean isEmptyString(String value) {
		if (value == null || value.trim().equals("")) {
			return true;
		}
		return false;
	}
	
	/**
	 * 문자열이 null이나 공백인지를 체크
	 * 
	 * @param value
	 * @return
	 */
	public static boolean isEmptyString(Object value) {
		if (value == null) {
			return true;
		}
		
		if (value.toString().length() == 0) {
			return true; 
		} 		
		
		return false;
	}	

	/**
	 * source에 value를 추가한다.(value가 값이 있느냐 없느냐에 따라 개행문자 여부가 다름)
	 * 
	 * @param value
	 * @return
	 */
	public static String appendLineString(String source, String value) {
		if (source == null || value.trim().equals("")) {
			return value;
		} else {
			return source + "\n" + value;
		}
	}

	/**
	 * value가 format에 해당하는 date로 잘 넘어왔는지 체크
	 * 
	 * @param value
	 * @param format
	 * @return
	 */
	public static boolean dateFormatCheck(String value, String format) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(format);
		try {
			dateFormat.parse(value);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * value가 숫자인지 체크
	 * 
	 * @param value
	 * @return
	 */
	public static boolean isInteger(String value) {
		try {
			Integer.parseInt(value);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * 지정한 format에 오늘 날짜의 문자열을 리턴한다.
	 * 
	 * @param format
	 * @return
	 */
	public static String getTodayString(String format) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(format);
		return dateFormat.format(Calendar.getInstance().getTime());
	}

	/**
	 * 길게 들어온 date 문자열을 yyyyMMdd 형태의 문자로 변경한다.
	 * 
	 * @param value
	 * @return
	 */
	public static String getDateString(String value) {
		if (value != null && value.length() > 8) {
			value = value.replaceAll("-", "");
			return value.substring(0, 8);
		}
		return value;
	}

	/**
	 * 8자리 기준일을 입력 받아서 날짜를 더한다.
	 * @param
	 * @return
	 */
	public static String orderDateAdd(String stdDate, int addedDate)  {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		Calendar cal = Calendar.getInstance();
		Date date = null;
		try {
			date = format.parse(stdDate);
			cal.setTime(date);
			cal.add(Calendar.MINUTE, addedDate);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}

		return format.format(cal.getTime());
	}

	/**
	 * 8자리 기준일을 입력 받아서 날짜를 더한다.
	 * @param
	 * @return
	 */
	public static String orderTimeAdd(String stdDate, int addtime)  {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		Calendar cal = Calendar.getInstance();
		Date date = null;
		try {
			date = format.parse(stdDate);
			cal.setTime(date);
			cal.add(Calendar.MINUTE, addtime);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
		String datetime = format.format(cal.getTime());
		datetime = datetime.substring(0,10)+"T"+datetime.substring(11,16)+":00";
		return datetime;
	}

	/**
	 * compare와 value를 null을 포함하여 비교(null과 공백을 같은 것으로 봄)
	 * 
	 * @param compare
	 * @param value
	 * @return
	 */
	public static boolean isEqualIncludeNull(String compare, String value) {
		String transCompare = (compare == null) ? "" : compare;
		String transValue = (value == null) ? "" : value;
		return transCompare.equals(transValue);
	}

	public static String getFormatDateStr(String dateStr, String delimiter) {
		if (dateStr != null && dateStr.length() >= 8) {
			return dateStr.substring(0, 4) + delimiter + dateStr.substring(4, 6) + delimiter + dateStr.substring(6, 8);
		}

		return dateStr;
	}

	public static int getLength(String str) {
		if (str == null) {
			return 0;
		}
		return str.length();
	}

	public static String validCheckDateFormat(String strDate, String format) {
		String resultMsg = null;

		SimpleDateFormat dateFormat = new SimpleDateFormat(format);
		Calendar cal = Calendar.getInstance();

		// format의 length와 strStartDate의 length가 다르면
		if (getLength(strDate) != getLength(format)) {
			return "날짜의 길이가 지정 된 포맷과 맞지 않습니다.(" + strDate + ")";
		}

		try {
			cal.setTime(dateFormat.parse(strDate));

			String date = dateFormat.format(cal.getTime());
			if (!date.equals(strDate)) {
				throw new Exception();
			}
		} catch (Exception e) {
			return "날짜의 양식이 잘못되었습니다.(" + strDate + " - " + format + ")";
		}

		return resultMsg;
	}

	public static String validCheckDate(String strDate) {
		return validCheckDateFormat(strDate, "yyyyMMdd");
	}

	/**
	 * 시작일자, 종료일자, format에 해당하는 date가 정상적인지 check하는 공통 함수
	 * 
	 * @param strStartDate
	 * @param strEndDate
	 * @param format
	 * @return
	 */
	public static String validCheckDatesFormat(String strStartDate, String strEndDate, String format) {
		String resultMsg = null;

		// 시작일자 체크
		resultMsg = validCheckDateFormat(strStartDate, format);
		if (resultMsg != null) {
			return resultMsg;
		}

		// 종료일자 체크
		resultMsg = validCheckDateFormat(strEndDate, format);
		if (resultMsg != null) {
			return resultMsg;
		}

		try {
			SimpleDateFormat dateFormat = new SimpleDateFormat(format);

			Calendar startCal = Calendar.getInstance();
			Calendar endCal = Calendar.getInstance();
			startCal.setTime(dateFormat.parse(strStartDate));
			endCal.setTime(dateFormat.parse(strEndDate));

			if (startCal.after(endCal)) {
				return "시작 날짜는 종료 날짜보다 클 수 없습니다.";
			}
		} catch (Exception e) {
			// impossible
			return "날짜의 양식이 잘못되었습니다.(" + strStartDate + "~" + strEndDate + " - " + format + ")";
		}

		return resultMsg;
	}

	/**
	 * 시작일자, 종료일자가 yyyyMMdd에 해당하는 date가 정상적인지 check하는 공통 함수
	 * 
	 * @param strStartDate
	 * @param strEndDate
	 * @return
	 */
	public static String validCheckDates(String strStartDate, String strEndDate) {
		return validCheckDatesFormat(strStartDate, strEndDate, "yyyyMMdd");
	}

	/**
	 * 파일이름의 확장자를 추출한다.
	 * 
	 * @param filename
	 * @return
	 */
	public static String getFileExt(String filename) {
		if (filename == null) {
			return "";
		}
		int lastIndex = filename.lastIndexOf(".");
		if (lastIndex == -1 || filename.length() < lastIndex + 1) {
			return "";
		}
		return filename.substring(filename.lastIndexOf(".") + 1);
	}

	/**
	 * 확장자가 extList에 있는 확장자가 아니면 에러
	 * 
	 * @param ext
	 * @param extList
	 * @return
	 */
	public static boolean isRightExt(String ext, final String[] extList) {
		if (ext == null) {
			return false;
		}

		for (String rightExt : extList) {
			if (rightExt.equalsIgnoreCase(ext)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 각 ID값을 해당 키값으로 변경할때 사용. ex)캠페인ID : makeKeyId("CP_", "1", 11)
	 * return:"CP_00000001"
	 * 
	 * @param preFix
	 * @param value
	 * @param
	 * @return
	 */
	public static String makeKeyId(String preFix, String value, int len) {
		String result = value.trim();

		if (result.length() >= len)
			return result;

		if (result.indexOf(preFix) != -1)
			return result;
		else if (result.length() + preFix.length() > len)
			return result;

		for (int i = result.length(); i < len - preFix.length(); i++) {
			result = "0" + result;
		}

		result = preFix + result;

		return result;
	}

	/**
	 * 구분자 만들어진 string을 받아서 split하고 trim()으로 공백을 제거하여 배열로 만들어서 리턴한다.
	 * 
	 * @param source
	 * @param delimeter
	 * @return
	 */
	public static String[] split(String source, String delimeter) {
		String[] result = null;

		if (isEmptyString(source)) {
			result = new String[0];
		} else {
			result = source.trim().split(delimeter);
			for (int i = 0; i < result.length; i++) {
				result[i] = result[i].trim();
			}
		}

		return result;
	}

	/**
	 * MAC ADDRESS가 맞는지 체크하여 리턴한다.
	 * 
	 * @param macAddress
	 * @return
	 */
	public static boolean isMacAddress(String macAddress) {
		if (StringUtil.isEmptyString(macAddress)) {
			return false;
		}

		Pattern p = Pattern.compile("^([0-9A-F]{2}){5}([0-9A-F]{2})$");
		Matcher m = p.matcher(macAddress);
		return m.matches();
	}
	
	/**
	 * - 제거하여 리턴한다
	 * @param
	 * @return
	 */	  
	public static Object getRemoveHipen(Object object) {
		if(object != null )
		{
			object = ((String) object).replaceAll("-", "");
		}
		return (String) object;
	}	 	

	public static int checkByte(String value) {
		int strlen = 0;

		for (int j = 0; j < value.length(); j++) {
			char c = value.charAt(j);
			if (c < 0xac00 || 0xd7a3 < c)
				strlen++;
			else
				strlen += 2; // 한글이다..
		}
		return strlen;
	}

	/**
	 * 숫자형으로 반환한다.
	 * @param object
	 * @return
	 */	  	
	public static int getIntegerValue(Object object) {
		if (object.getClass() == Long.class) {
			return (int) (long) object;
		} else if (object.getClass() == String.class) {
			return Integer.parseInt((String) object);
		} else {
			return (int) object;
		}
	}
	

	/**
	 * 8자리 기준일을 입력 받아서 날짜를 더한다.
	 * @param
	 * @return
	 */	  	
	public static String dateAdd(String stdDate, int addedDate)  {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
		Calendar cal = Calendar.getInstance();
		Date date = null;
		try {
			date = format.parse(stdDate);
			cal.setTime(date);
			cal.add(Calendar.DATE, addedDate);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
		
		return format.format(cal.getTime());
	}

	/**
	 * 8자리 기준일을 입력 받아서 날짜를 더한다.
	 * @param
	 * @return
	 */
	public static String timeAdd(String stdDate, int addedDate)  {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm");
		Calendar cal = Calendar.getInstance();
		Date date = null;
		try {
			date = format.parse(stdDate);
			cal.setTime(date);
			cal.add(Calendar.MINUTE, addedDate);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}

		return format.format(cal.getTime());
	}
	
	/**
	 * NULL 값을 제거한다. 입력값(text)이 NULL이이면 공백("")을 리턴한다.
	 * 
	 * @param text
	 * @return
	 */
	public static String NVL(Object text) {
		if(text == null || text == "") {
			return "";
		} else {
			return text.toString();
		}
	}

	public static String encrytion(String plainUserId) throws Exception{
		try{
			SecretKey secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");

			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);

			byte[] encrypted = cipher.doFinal(plainUserId.getBytes());

			return new String(Base64.getEncoder().encode(encrypted), Charset.forName("UTF-8"));
		}catch (Exception e){
			e.printStackTrace();
		}
		return plainUserId;
	}

	/**
	 * 티몬 복호화
	 * @return
	 * @throws Exception
	 */
	public static String decryption(String encryptedUserId) {
		try {
			SecretKey secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");

			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, secretKey);

			byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedUserId));

			//System.out.println("----"+new String(decrypted));
			return new String(decrypted);
		}catch (Exception e){
			e.printStackTrace();
		}
		return encryptedUserId;

	}
}
