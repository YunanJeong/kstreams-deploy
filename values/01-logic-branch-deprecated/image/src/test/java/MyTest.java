import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;


public class MyTest {
    
    @Test
    void testMask(){
        //Security sec = new Security(); 
        String msg = "010-1234-3699과031-1566-3003는 전화번호이고, 1234-6047-1234-9879은 카드번호이고, 999999-1234565은 주민번호다.";
        String msg2 = "**MASKED_PHONE_NUM**과**MASKED_PHONE_NUM**는 전화번호이고, **MASKED_CREDIT_CARD_NUM**은 카드번호이고, **MASKED_RRN**은 주민번호다.";
        
        // Test 할 때는 대상 메소드를 public으로 바꿔야 함
        // System.out.println(sec.getMaskedMsg(msg));
        // assertEquals(sec.getMaskedMsg(msg), msg2);
        
    }
}
