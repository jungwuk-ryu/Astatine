/* SPDX-License-Identifier: MIT */
package alternate.current.wire;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
/** Real constructors and real priority methods; no world reads or Node subclasses. */
final class ActualNodes {
    static Node create(int id) {
        WireNode wire = new WireNode(null, new BlockPos(id, 64, 0), Blocks.REDSTONE_WIRE.defaultBlockState());
        if ((id & 1) == 0) return wire;
        Node node = new Node(null);
        node.pos = wire.pos;
        node.state = Blocks.STONE.defaultBlockState();
        node.neighborWire = wire;
        return node;
    }
    static void requested(Node node, int priority) {
        if (node.isWire()) node.asWire().virtualPower = priority;
        else node.neighborWire.priority = priority;
    }
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
}
