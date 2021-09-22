package org.eclipse.platform.tools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BumpBundleVersionTest {
	
	@InjectMocks
	@Spy
	BumpBundleVersion instance;

	@Test
	void test() {
		fail("Not yet implemented");
	}

}
