package wishbone

import freechips.rocketchip.diplomacy._

package object wishbone {
  type WishboneOutwardNode = OutwardNodeHandle[
    WBMasterPortParameters,
    WBSlavePortParameters,
    WBEdgeParameters,
    WBBundle]
  type WishboneInwardNode = InwardNodeHandle[
    WBMasterPortParameters,
    WBSlavePortParameters,
    WBEdgeParameters,
    WBBundle]
  type WishboneNode = SimpleNodeHandle[
    WBMasterPortParameters,
    WBSlavePortParameters,
    WBEdgeParameters,
    WBBundle]
}
