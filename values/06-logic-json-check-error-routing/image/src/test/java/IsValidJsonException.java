import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class IsValidJsonException {

    ObjectMapper mMapper = new ObjectMapper();
    private static String JSON = "{\"source\": \"source\", \"message\": \"message\"}";
    private static String NONJSON0 = "{\"sinkAeqwrgqergq\":}";


    // fasterxml.jackson은 단일 value로 취급될 수 있는 것들을 JsonNode Object로 취급한다.
    // 이들은 Json은 아니지만 readTree로 변환시 exception이 발생하지 않는다.
    // 따라서 Json 타당성 검사 시 이들에 대한 별도 처리가 필요
    // 단일 value로 취급되는 것들 : String, Number, Boolean, Null
    // 위 사례에 대해서 양끝에 이스케이프 쌍따옴표가 있으면 JsonNode로 취급된다.
    private static String NONJSON1 = "\"JustString\""; // 이스케이프로 쌍따옴표가 있을시 Json은 아니지만 JsonNode로 취급됨 
    private static String NONJSON2 = "123.45";  // 단일 값 숫자
    private static String NONJSON3 = "true";    // 단일 값 Boolean
    private static String NONJSON4 = "null";    // 단일 값 Nullable 
    private static String NONJSON1_1 = "\"JustString\": \"source\"";  // 콜론 이후로 무시하고 JustString을 JsonNode로 취급
    private static String NONJSON1_2 = "\"JustString\":";             // 콜론 무시하고 JustString 부분을 JsonNode로 취급

    
    private static String SAMPLE7 = """
                                    {
                                        \"source\":\"source\",
                                        \"message\":\"{\\\"NormalKey\\\":\\\"NormalValue\\\"}\"
                                    }
                                    """;
    private static String SAMPLE8 = """
                                        {
                                        \"source\":\"source\",
                                        \"message\":\"\\\"Broken\\\":\\\"JsonString\\\"\"
                                    }
                                    """;

    @Test
    @DisplayName("Json String을 파싱하기")
    void testScenario1() {
        assertEquals(true ,isValidJson(JSON));
        assertEquals(false ,isValidJson(NONJSON0));
    }

    @Test
    @DisplayName("Json포맷이 아닌데, JsonNode로 취급되는 케이스")
    void testScenario2() {
        assertEquals(true ,isValidJson(NONJSON1));
        assertEquals(true ,isValidJson(NONJSON2));
        assertEquals(true ,isValidJson(NONJSON3));
        assertEquals(true ,isValidJson(NONJSON4));
        assertEquals(true ,isValidJson(NONJSON1_1));
        assertEquals(true ,isValidJson(NONJSON1_2));
    }

    @Test
    void testScenario3() {
        String txt = null;
        String str = null;
        try {
            txt = mMapper.readTree(SAMPLE7).get("message").asText();   // Json String을 Json으로 읽은 후 String 반환
            str = mMapper.readTree(SAMPLE7).get("message").toString(); // Json String을 bytes형태 그대로 읽은 후 String 반환 (이스케이프, 개행 등이 그대로 남음) //writeValueAsString 와 유사

            System.out.println("txt = " + txt);
            System.out.println("str = " + str);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
  
        assertEquals(true, isValidJson(txt));
        assertEquals(true, isValidJson(str));
        assertEquals(isValidJson(str), isValidJson(txt));
    }

    @Test
    @DisplayName("Json포맷이 아닌데, JsonNode로 취급되는 케이스")
    void testScenari4() {
        String txt = null;
        String str = null;
        try {
            txt = mMapper.readTree(SAMPLE8).get("message").asText();
            str = mMapper.readTree(SAMPLE8).get("message").toString();
            System.out.println("txt = " + txt);
            System.out.println("str = " + str);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
  
        assertEquals(true, isValidJson(txt));
        assertEquals(true, isValidJson(str));
        assertEquals(isValidJson(str), isValidJson(txt));
    }





    public boolean isValidJson(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.readTree(jsonString);
            // System.err.println("jsonString = " + objectMapper.readTree(jsonString));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
