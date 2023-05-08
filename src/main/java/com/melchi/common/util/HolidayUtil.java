package com.melchi.common.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import com.ibm.icu.util.ChineseCalendar;

public class HolidayUtil {
	
	//양력 휴일
	private static String[] solarArr = new String[]{"0101", "0301", "0505", "0606", "0815", "1225"};
	//음력 휴일
	private static String[] lunarArr = new String[]{"0101", "0102", "0408", "0814", "0815", "0816"};
	
	/**
	 * 해당일자가 법정공휴일, 대체공휴일, 토요일, 일요일인지 확인
	 * 
	 * @param date 양력날짜 (yyyyMMdd)
	 * @return 법정공휴일, 대체공휴일, 일요일이면 true, 오류시 false
	 */
	public static boolean isHoliday(String date) {
		try {
			return isHolidaySolar(date) || isHolidayLunar(date) || isHolidayAlternate(date) || isWeekend(date);
		} catch (ParseException e) {
			return false;
		}
	}
	
	/**
	 * 영업일 조회
	 * 
	 * @param date 양력날짜 (yyyyMMdd)
	 * @param days 구하고자하는 영업일
	 * @return date에 days를 더한 영업일
	 */
	public static String workingDay(String date, int days) {
		while(days>0) {
			date = StringUtil.dateAdd(date, 1);
			if(!isHoliday(date)) {
				days--;
			}
		}
		
		return date;
	}
	
	/**
	 * 토요일 또는 일요일이면 true를 리턴한다.
	 * @param date 양력날짜 (yyyyMMdd)
	 * @return 일요일인지 여부
	 * @throws ParseException
	 */
	private static boolean isWeekend(String date) throws ParseException {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		Calendar cal = Calendar.getInstance();
		cal.setTime(sdf.parse(date));
		return cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY;
	}
	
	 /**
	  * 해당일자가 대체공휴일에 해당하는 지 확인
	  * @param 양력날짜 (yyyyMMdd)
	  * @return 대체 공휴일이면 true
	  */
	private static boolean isHolidayAlternate(String date) {
		String[] altHoliday = new String[] {"20220912", "20230124", "20240212", "20240506", "20251008", "20270209", "20290924", "20290507"}; 
		return Arrays.asList(altHoliday).contains(date); 
	}
	
	/**
	 * 해당일자가 음력 법정공휴일에 해당하는 지 확인
	 * @param 양력날짜 (yyyyMMdd)
	 * @return 음력 공휴일이면 true
	 */
	private static boolean isHolidayLunar(String date) {
		try {
			Calendar cal = Calendar.getInstance();
			ChineseCalendar chinaCal = new ChineseCalendar();
			
			cal.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
			cal.set(Calendar.MONTH, Integer.parseInt(date.substring(4, 6)) - 1);
			cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date.substring(6)));
			
			chinaCal.setTimeInMillis(cal.getTimeInMillis());
			
			// 음력으로 변환된 월과 일자
			int mm = chinaCal.get(ChineseCalendar.MONTH) + 1;
			int dd = chinaCal.get(ChineseCalendar.DAY_OF_MONTH);
			
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%02d", mm));
			sb.append(String.format("%02d", dd));
			
			// 음력 12월의 마지막날 (설날 첫번째 휴일)인지 확인
			if (mm == 12) {
				int lastDate = chinaCal.getActualMaximum(ChineseCalendar.DAY_OF_MONTH);
				if (dd == lastDate) {
					return true;
				}
			}

			// 음력 휴일에 포함되는지 여부 리턴
			return Arrays.asList(lunarArr).contains(sb.toString()); 
		} catch(Exception ex) {
			System.out.println(ex.getStackTrace());
			return false;
		}
	}

	/**
	 * 해당일자가 양력 법정공휴일에 해당하는 지 확인
	 * @param date 양력날짜 (yyyyMMdd)
	 * @return 양력 공휴일이면 true
	 */
	private static boolean isHolidaySolar(String date) {
		try {
			// 공휴일에 포함 여부 리턴 
			return Arrays.asList(solarArr).contains(date.substring(4));
		} catch(Exception ex) {
			System.out.println(ex.getStackTrace());
			return false;
		}
	}
}
