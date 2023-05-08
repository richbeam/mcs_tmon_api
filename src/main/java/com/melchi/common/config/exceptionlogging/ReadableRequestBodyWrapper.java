package com.melchi.common.config.exceptionlogging;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.io.IOUtils;

//Request에 대하여 @RequestBody를 얻은 후, 지우지 않고 남겨둠
public class ReadableRequestBodyWrapper extends HttpServletRequestWrapper {
	private byte[] bytes;
	private String requestBody;

	public ReadableRequestBodyWrapper(HttpServletRequest request) throws IOException{
		super(request);

		InputStream in = super.getInputStream();
		bytes = IOUtils.toByteArray(in);
		requestBody = new String(bytes);		
 
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		return  new ServletImpl(bis);
	}

	public String getRequestBody() {
		return this.requestBody;
	}

	class ServletImpl extends ServletInputStream {
		private InputStream is;
		public ServletImpl(InputStream bis){
			is = bis;
		} 

		@Override
		public int read() throws IOException {
			return is.read();
		}

		@Override
		public int read(byte[] b) throws IOException {
			return is.read(b);
		}

		@Override
		public boolean isFinished() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isReady() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void setReadListener(ReadListener listener) {
			// TODO Auto-generated method stub			
		}
	}
}