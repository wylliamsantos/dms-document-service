package br.com.dms.util;

import br.com.dms.config.MongoConfig;
import br.com.dms.service.workflow.pojo.DmsEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureCache
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DmsEntryComparatorTest {

	@MockBean
	private MongoConfig mongoConfig;


	@Value(value = "${dms.uid}")
	private String uid;

	@Test
	void testCompareEqual() {
		DmsEntry entry = new DmsEntry();
		entry.setId("1");
		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		int response = dmsEntryComparator.compare(entry, entry);
		assertEquals(0, response);
	}

	@Test
	void testCompareDifferent() {
		DmsEntry entry = new DmsEntry();
		entry.setId("1");
		DmsEntry entry2 = new DmsEntry();
		entry2.setId("2");
		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		int response = dmsEntryComparator.compare(entry, entry2);
		assertEquals(-1, response);
	}

	@Test
	void testCompareDifferent2() {
		DmsEntry entry = new DmsEntry();
		entry.setId("2");
		DmsEntry entry2 = new DmsEntry();
		entry2.setId("1");
		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		int response = dmsEntryComparator.compare(entry, entry2);
		assertEquals(1, response);
	}

	@Test
	void testCompareDifferent3() {
		DmsEntry entry = new DmsEntry();
		entry.setId("002");
		DmsEntry entry2 = new DmsEntry();
		entry2.setId("1");
		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		int response = dmsEntryComparator.compare(entry, entry2);
		assertEquals(1, response);
	}

	@Test
	void testCompareDifferent4() {
		DmsEntry entry = new DmsEntry();
		entry.setId("2");
		DmsEntry entry2 = new DmsEntry();
		entry2.setId("001");
		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		int response = dmsEntryComparator.compare(entry, entry2);
		assertEquals(1, response);
	}

	@Test
	void testCompareRightWhenStringABisNotDigit(){
		String a = "a";
		String b = "b";

		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = 0;
		assertEquals(expected, response);
	}

	@Test
	void testCompareRightWhenStringABisComma(){
		String a = ",";
		String b = ",";

		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = 0;
		assertEquals(expected, response);
	}

	@Test
	void testCompareRightWhenStringABisPeriod(){
		String a = ".";
		String b = ".";

		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = 0;
		assertEquals(expected, response);
	}

	@Test
	void testCompareRightWhenStringAisNotDigit(){
		String a = "a";
		String b = "1";

		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = -1;
		assertEquals(expected, response);
	}

	@Test
	void testCompareRightWhenStringBisNotDigit(){
		String a = "1";
		String b = "b";

		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = 1;
		assertEquals(expected, response);
	}

	@Test
	void testCompareRightWhenStringABEmpty(){

		String a = "";
		String b = "";
		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = 0;
		assertEquals(expected, response);
	}

	@Test
	void testCompareRightWhenAIslesThanB(){
		String a = "1";
		String b = "2";

		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = -1;
		assertEquals(expected, response);
	}


	@Test
	void testCompareRightWhenBIslesThanA(){
		String a = "2";
		String b = "1";

		DmsEntryComparator dmsEntryComparator = new DmsEntryComparator();
		var response = dmsEntryComparator.compareRight(a, b);
		var expected = 1;
		assertEquals(expected, response);
	}

	@Test
	void testCompareEqualWhenNZAminusNZBisNotZero(){
		String a = "a";
		String b = "b";
		int nza = 2;
		int nzb = 1;
		var response = DmsEntryComparator.compareEqual(a, b, nza, nzb);
		var expected = 1;
		assertEquals(expected, response);

	}

	@Test
	void testCompareEqualWhenStringAisDifferentThanStringB(){
		String a = "aaaa";
		String b = "bb";
		int nza = 2;
		int nzb = 2;
		var response = DmsEntryComparator.compareEqual(a, b, nza, nzb);
		var expected = 2;
		assertEquals(expected, response);

	}

}
