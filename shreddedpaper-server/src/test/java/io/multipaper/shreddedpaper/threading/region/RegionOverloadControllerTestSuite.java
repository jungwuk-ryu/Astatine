package io.multipaper.shreddedpaper.threading.region;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({RegionOverloadControllerTest.class, RegionChunkIoTrackerTest.class})
public class RegionOverloadControllerTestSuite {
}
