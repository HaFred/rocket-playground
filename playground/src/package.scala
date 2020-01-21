import freechips.rocketchip._
import diplomacy._
import config._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelUtils
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalModuleTree
import freechips.rocketchip.diplomaticobjectmodel.model.OMComponent
import freechips.rocketchip.util.ElaborationArtefacts
import subsystem._
import pprint._

package object playground {
  /** helper to convert [[LazyModule]] to [[chisel3]] */
  def configToLazyModule[L <: LazyModule](lazyModuleClass: Class[L], config: Config): L = {
    lazyModuleClass.getConstructors()(0).newInstance(config.toInstance).asInstanceOf[L]
  }

  def configToRocketModule[L <: RocketSubsystem](rocketLazyModule: Class[L], config: Config): L = {
    configToLazyModule(rocketLazyModule, config)
  }

  def RocketModuleToSvd(rocketLazyModule: RocketSubsystem) = {

  }
}
