package dev.cardadvisor.ai;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MerchantCategoryClassifierTest {
 @Test void localHintsAreSuggestionsAndRawMerchantNeverBecomesFeature(){for(var sample:Map.of("테스트 커피점","FOOD","테스트 약국","HEALTH","무신사","SHOPPING","택시","TRANSPORT","관리비","HOUSING","영화관","LEISURE").entrySet()){var s=MerchantCategoryClassifier.classify(sample.getKey());assertEquals(sample.getValue(),s.category());assertFalse(s.features().contains(sample.getKey()));assertTrue(s.features().stream().allMatch(x->x.matches("CATEGORY_HINT_[A-Z]+")));}}
 @Test void ambiguousMissingAndUnrecognizedNamesStayUnknown(){assertEquals("OTHER",MerchantCategoryClassifier.classify(null).category());assertEquals("OTHER",MerchantCategoryClassifier.classify("비공개 이름 01012345678").category());var s=MerchantCategoryClassifier.classify("병원 카페");assertEquals("OTHER",s.category());assertTrue(s.ambiguous());assertEquals(2,s.features().size());}
}
