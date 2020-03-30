package fpga

/** This class is designed for arty100t board */

/** rocketchip dependency */

import freechips.rocketchip.config._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile._
import gemmini.{Gemmini, GemminiConfigs}

/** sifive blocks dependency */
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._

/** Example Top with periphery devices and ports, and a Rocket subsystem */
class CustomRocketSystem(implicit p: Parameters) extends RocketSubsystem
  with HasHierarchicalBusTopology
  with HasAsyncExtInterrupts
  with CanHaveMasterAXI4MemPort
  with HasPeripheryBootROM
  with HasPeripherySPIFlash
  with HasPeripheryUART {

  override lazy val module = new CustomRocketSystemModuleImp(this)
}

class CustomRocketSystemModuleImp[+L <: CustomRocketSystem](_outer: L) extends RocketSubsystemModuleImp(_outer)
  with HasRTCModuleImp
  with HasExtInterruptsModuleImp
  with HasPeripheryBootROMModuleImp
  with HasPeripherySPIFlashModuleImp
  with HasPeripheryUARTModuleImp
  with HasPeripheryDebugModuleImp

class WithNCustomCores(n: Int) extends Config((site, here, up) => {
  case RocketTilesKey => {
    val small = RocketTileParams(
      core = RocketCoreParams(fpu = None, nBreakpoints = 8),
      btb = None,
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 32,
        nWays = 1,
        nTLBEntries = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 2,
        nWays = 1,
        nTLBEntries = 4,
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => small.copy(hartId = i))
  }
})

class CustomConfig extends Config(
  new Config((site, here, up) => {
    case BuildRoCC => Seq(
      (p: Parameters) => {
        implicit val q = p
        implicit val v = implicitly[ValName]
        LazyModule(new Gemmini(OpcodeSet.custom3, GemminiConfigs.defaultConfig))
      }
    )
    case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
    case PeripheryBusKey => PeripheryBusParams(
      beatBytes = site(XLen) / 8,
      blockBytes = site(CacheBlockBytes),
      dtsFrequency = Some(50000000))
    case ExtMem => Some(MemoryPortParams(MasterPortParams(
      base = BigInt("80000000", 16),
      size = BigInt("40000000", 16),
      beatBytes = site(MemoryBusKey).beatBytes,
      idBits = 4), 1))
    case BootROMParams => new BootROMParams(contentFileName = "rocketchip/bootrom/bootrom.img")
    case PeripheryUARTKey => List(
      UARTParams(address = 0x10012000),
    )
    case PeripherySPIFlashKey => List(
      SPIFlashParams(fAddress = 0x20000000, fSize = 0x10000000, rAddress = 0x10013000)
    )
  }) ++
    new WithNCustomCores(2) ++
    new WithInclusiveCache(capacityKB = 32) ++
    new WithJtagDTM ++
    new WithDefaultMemPort() ++
    new WithNoMMIOPort() ++
    new WithNoSlavePort() ++
    new WithDTS("freechips,rocketchip-arty100t", Nil) ++
    new WithNExtTopInterrupts(0) ++
    new WithoutTLMonitors ++
    new WithTimebase(BigInt(50000000)) ++
    new BaseSubsystemConfig
)
