package org.bxteam.divinemc.region;

import java.io.IOException;

@FunctionalInterface
public interface IRegionCreateFunction {

    IRegionFile create(RegionFileInfo info) throws IOException;
}
