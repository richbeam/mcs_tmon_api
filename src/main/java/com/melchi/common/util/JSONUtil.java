package com.melchi.common.util;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

public class JSONUtil { 

	static final Logger logger = LoggerFactory.getLogger(JSONUtil.class);
	
	private final static ObjectMapper objectMapper = new ObjectMapper();
	

	/**
	 * get Instance of ObjectMapper 
	 * 
	 * @return ObjectMapper
	 */ 
	public static ObjectMapper getInstance() { 
		return objectMapper;
	}

	/** 
	 * JavaBean convert to Json
	 * @param bean
	 * @return
	 */
	public static String bean2Json(Object bean) {
		try {
			objectMapper.setSerializationInclusion(Include.NON_EMPTY);
			return objectMapper.writeValueAsString(bean);
		} catch (JsonProcessingException e) {
			logger.error("jackson进程异常:" + e.getMessage());
			return null;
		}
	}

	/**
	 * Json convert to JavaBean
	 * @param json
	 * @param clazz
	 * @return
	 */
	public static <T> T json2Bean(String json, Class<T> clazz) {
		try {
			return objectMapper.readValue(json, clazz);
		} catch (JsonParseException e) {
			logger.error("Jackson解析异常:" + e.getMessage());
			return null;
		} catch (JsonMappingException e) {
			logger.error("Jackson映射异常:" + e.getMessage());
			return null;
		} catch (IOException e) {
			logger.error("IO异常:" + e.getMessage());
			return null;
		}
	}

	/**
	 * Json convert to Map
	 * @param json
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> Map<String, Object> json2Map(String json) {
		try {
			return objectMapper.readValue(json, Map.class);
		} catch (JsonParseException e) {
			logger.error("Jackson:" + e.getMessage());
			return null;
		} catch (JsonMappingException e) {
			logger.error("Jackson:" + e.getMessage());
			return null;
		} catch (IOException e) {
			logger.error("IO:" + e.getMessage());
			return null;
		}
	}

	/**
	 * Json convert to Map with javaBean
	 * @param json
	 * @param clazz
	 * @return
	 */
	public static <T> Map<String, T> json2Map(String json, Class<T> clazz) {
		Map<String, Map<String, Object>> map;
		try {
			map = objectMapper.readValue(json, new TypeReference<Map<String, T>>(){});
			Map<String, T> result = new HashMap<String, T>();
			for (Entry<String, Map<String, Object>> entry : map.entrySet()) {
				result.put(entry.getKey(), map2Bean(entry.getValue(), clazz));
			}
			return result;
		} catch (JsonParseException e) {
			logger.error("Jackson:" + e.getMessage());
			return null;
		} catch (JsonMappingException e) {
			logger.error("Jackson:" + e.getMessage());
			return null;
		} catch (IOException e) {
			logger.error("IO:" + e.getMessage());
			return null;
		}
	}

	/**
	 * Json convert to List with JavaBean
	 * @param json
	 * @param clazz
	 * @return
	 */
	public static <T> List<T> json2List(String json, Class<T> clazz) {
		List<Map<String, Object>> list;
		try {
			list = objectMapper.readValue(json, new TypeReference<List<T>>() {});
			List<T> result = new ArrayList<T>();
			for (Map<String, Object> map : list) {
				result.add(map2Bean(map, clazz));
			}
			return result;
		} catch (JsonParseException e) {
			logger.error("Jackson:" + e.getMessage());
			return null;
		} catch (JsonMappingException e) {
			logger.error("Jackson:" + e.getMessage());
			return null;
		} catch (IOException e) {
			logger.error("IO:" + e.getMessage());
			return null;
		}
	}

	/**
	 * Map convert to JavaBean
	 * 
	 * @param map
	 * @param clazz
	 * @return bean
	 */
	@SuppressWarnings("rawtypes")
	public static <T> T map2Bean(Map map, Class<T> clazz) {
		return objectMapper.convertValue(map, clazz);
	}
	
	

	
	public static  String toJson(Object obj) {
        Gson gson = new Gson();
        return gson.toJson(obj);
    }

    public static <T>  T fromJson(String str, Class<T> type) { 
        Gson gson = new Gson();
        return gson.fromJson(str, type);
    }

    public static  JSONObject map2Json(Map<?, ?> data) {
        JSONObject object = new JSONObject();

        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String key = (String) entry.getKey();
            if (key == null) { 
                throw new NullPointerException("key == null");
            }
            try {
                object.put(key, wrap(entry.getValue()));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return object;
    }

    public static  JSONArray collection2Json(Collection<?> data) {
        JSONArray jsonArray = new JSONArray();
        if (data != null) {
            for (Object aData : data) {
                jsonArray.put(wrap(aData));
            }
        }
        return jsonArray;
    }

    public static  JSONArray object2Json(Object data) throws JSONException {
        if (!data.getClass().isArray()) {
            throw new JSONException("Not a primitive data: " + data.getClass());
        }
        final int length = Array.getLength(data);
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < length; ++i) {
            jsonArray.put(wrap(Array.get(data, i)));
        }

        return jsonArray;
    }

    private static  Object wrap(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof JSONArray || o instanceof JSONObject) {
            return o;
        }
        try {
            if (o instanceof Collection) {
                return collection2Json((Collection<?>) o);
            } else if (o.getClass().isArray()) {
                return object2Json(o);
            }
            if (o instanceof Map) {
                return map2Json((Map<?, ?>) o);
            }

            if (o instanceof Boolean || o instanceof Byte || o instanceof Character || o instanceof Double || o instanceof Float || o instanceof Integer || o instanceof Long
                    || o instanceof Short || o instanceof String) {
                return o;
            }
            if (o.getClass().getPackage().getName().startsWith("java.")) {
                return o.toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static  JSONObject string2JSONObject(String json) {
        JSONObject jsonObject = null;
        try {
            JSONTokener jsonParser = new JSONTokener(json);
            jsonObject = (JSONObject) jsonParser.nextValue();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }
	 
	
 
	public static List<Map<String, Object>> jsonStrToListMap(String json) {
		ObjectMapper objectMapper = new ObjectMapper();
	
		List<Map<String, Object>> data = null;
		try {

			data = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});


		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) { 
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
	}
    
    	public static Map<String, Object> xml2Map(String xml) throws Exception {

		JSONObject xmlJSONObj = XML.toJSONObject(xml);
		Map<String, Object> result = json2Map(xmlJSONObj.toString());
		return result;

	}
	 

	public static String json2Xml(String json) throws Exception {

		JSONObject jsonData = new JSONObject(json);
		String xml = XML.toString(jsonData); 
		return xml;

	}
	
	public static String map2Xml(Map<String, Object> map) throws Exception {
		JSONObject json = map2Json(map);
		String xml = json2Xml(json.toString());
		return xml;  
	}
    
}

