package wishbone

import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._

object WBImp extends SimpleNodeImp[WBMasterPortParameters, WBSlavePortParameters, WBEdgeParameters, WBBundle] {
  def edge(pd: WBMasterPortParameters,
           pu: WBSlavePortParameters,
           p: Parameters,
           sourceInfo: SourceInfo): WBEdgeParameters = WBEdgeParameters(pd, pu, p, sourceInfo)

  def bundle(e: WBEdgeParameters): WBBundle = WBBundle(e.bundle)

  def render(e: WBEdgeParameters): RenderedEdge = RenderedEdge(colour = "#00ccff" /* bluish */ , (e.slave.beatBytes * 8).toString)

  override def mixO(pd: WBMasterPortParameters,
                    node: OutwardNode[
                      WBMasterPortParameters,
                      WBSlavePortParameters,
                      WBBundle]
                   ): WBMasterPortParameters = pd.copy(masters = pd.masters.map { c => c.copy(nodePath = node +: c.nodePath) })

  override def mixI(pu: WBSlavePortParameters,
                    node: InwardNode[
                      WBMasterPortParameters,
                      WBSlavePortParameters,
                      WBBundle]
                   ): WBSlavePortParameters = pu.copy(slaves = pu.slaves.map { m => m.copy(nodePath = node +: m.nodePath) })
}

case class WBMasterNode(portParams: Seq[WBMasterPortParameters])(implicit valName: ValName) extends SourceNode(WBImp)(portParams)

case class WBSlaveNode(portParams: Seq[WBSlavePortParameters])(implicit valName: ValName) extends SinkNode(WBImp)(portParams)

case class WBNexusNode(masterFn: Seq[WBMasterPortParameters] => WBMasterPortParameters,
                       slaveFn: Seq[WBSlavePortParameters] => WBSlavePortParameters)(implicit valName: ValName)
  extends NexusNode(WBImp)(masterFn, slaveFn)

case class WBIdentityNode()(implicit valName: ValName) extends IdentityNode(WBImp)()
