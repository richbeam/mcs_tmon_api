package com.melchi.common.config;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper 
	public interface LogMapper {
		/* *
		 * 스케줄 EXCEPTION 로그 입력 
		 * */
		@Insert("INSERT INTO public.schedule_exception_log \r\n" + 
				"(\r\n" + 
				"	sitename\r\n" + 			
				"  , message\r\n" + 
				"  , \"content\"\r\n" + 
				"  , regdate) \r\n" + 
				"  values (\r\n" + 
				"    #{sitename}\r\n" +  				
				"   , #{message}\r\n" + 
				"   , #{content}\r\n" + 
				"   , now()\r\n" + 
				")\r\n" + 
				"")
		public int insertScheduleExceptionLog(Map<String, Object> paramMap);
		
		/* *
		 * API 호출 EXCEPTION 로그 입력 
		 * */		
		@Insert("	INSERT INTO rest_api_exception_log \r\n" + 
				"	(\r\n" + 
				"	   sitename\r\n" + 			
				"	 , request_method\r\n" + 
				"	 , request_url\r\n" + 
				"	 , request_param\r\n" +      
				"	 , request_body\r\n" +  
				"	 , content	 \r\n" + 
				"	 , regdate \r\n" + 
				"	) VALUES (\r\n" + 
				"	   #{sitename} 	   \r\n" + 
				"	 , #{request_method}\r\n" + 
				"	 , #{request_url}\r\n" + 
				"	 , #{request_param}\r\n" + 
				"	 , #{request_body}\r\n" + 
				"	 , #{content}\r\n" + 
				"	 , NOW() \r\n" + 
				"	)   ") 
		public int insertRestApiExceptionLog(Map<String, Object> paramMap); 	
		
		
	
		@Insert("INSERT INTO schedule_log\r\n" + 
				"(\r\n" + 
				"	 sitename\r\n" + 
				"  , api_url\r\n" + 
				"  , productcd\r\n" + 
				"  , productno\r\n" + 
				"  , m_ordercd\r\n" +  
				"  , ordercd\r\n" + 
				"  , detail_no\r\n" + 
				"  , content\r\n" + 
				"  , status\r\n" +  
				"  , regdate\r\n" + 
				")\r\n" + 
				"VALUES\r\n" + 
				"(\r\n" + 
				"	 #{sitename}\r\n" + 
				"  , #{api_url}\r\n" +  
				"  , #{productcd}::numeric\r\n" + 
				"  , #{productno}::numeric\r\n" +
				"  , #{m_ordercd}::text\r\n" + 
				"  , #{ordercd}::text\r\n" + 
				"  , #{detail_no}::text\r\n" + 
				"  , #{content}\r\n" + 
				"  , #{status}\r\n" + 
				"  , now()\r\n" +  
				")")
		/* *
		 * 스케줄러 로그 입력
		 * */				  		
		public int insertScheduleLog(Map<String, Object> paramMap);
		 
		@Select("SELECT m_ordercd \r\n" + 
				"  FROM comm_orders \r\n" +  
				" WHERE sitename = #{sitename}\r\n" +  
				"   AND ordercd = #{ordercd}::text") 
        public List<Map<String, Object>> selectMOrderCd(Map<String, Object> paramMap);	 			
					
	}     