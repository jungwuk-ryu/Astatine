package io.multipaper.shreddedpaper.audit;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({PlayerDataSaveQueueTest.class, PlayerDataStorageRegressionTest.class})
public class PlayerDataSaveQueueTestSuite {
}
