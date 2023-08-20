/**
  * sPMP TODO List
  * - [x] sPMP基础配置，包括注册CSR
  *     - [x] 添加spmp内容到class PMP：初始化为0
  *     - [x] cfg写入限制：与PMP相同，除了可以出现可写不可读
  *     - [x] cfg读取限制：暂无
  * - [x] sPMP 开关，不需要判断satp.mode：由pmpChecker传入
  * - [x] 需要传入sstatus.sum位
  * - [x] 检测和匹配逻辑
  *     - [x] 修改PMPChecker（暂时使用默认值）
  *     - [x] 实现spmp_check：应该和pma一样
  *     - [x] 实现spmp_match_res
  * - [ ] 匹配异常号
  * - [ ] 修改整个香山以匹配新的PMP和PMPChecker
  * 
  * 疑问：
  * - class PMP是否一个写端口就够了？
  * - PMA在系统运行过程中能修改吗（l位为啥是0）
  */
package xiangshan.backend.fu

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util._
import xiangshan._
import utils.{ParallelPriorityMux}
import xiangshan.cache.mmu.{TlbCmd}

trait SPMPConst extends PMPConst

class SPMPBase(implicit p: Parameters) extends PMPBase {
  override def write_cfg_vec(mask: Vec[UInt], addr: Vec[UInt], index: Int)(cfgs: UInt): UInt = {
    val cfgVec = Wire(Vec(cfgs.getWidth/8, new PMPConfig))
    for (i <- cfgVec.indices) {
      val cfg_w_m_tmp = cfgs((i+1)*8-1, i*8).asUInt.asTypeOf(new PMPConfig)
      cfgVec(i) := cfg_w_m_tmp
      // 过滤NA4，重新生成mask
      if (CoarserGrain) { cfgVec(i).a := Cat(cfg_w_m_tmp.a(1), cfg_w_m_tmp.a.orR) }
      when (cfgVec(i).na4_napot) {
        mask(index + i) := match_mask(cfgVec(i), addr(index + i))
      }
    }
    cfgVec.asUInt
  }
}

trait SPMPMethod extends SPMPConst {
  def spmp_init() : (Vec[UInt], Vec[UInt], Vec[UInt])
}

trait SPMPCheckMethod extends SPMPConst {
  def spmp_check(cmd: UInt, cfg: PMPConfig) = {
    val resp = Wire(new PMPRespBundle)
    resp.ld := TlbCmd.isRead(cmd) && !TlbCmd.isAmo(cmd) && !cfg.r
    resp.st := (TlbCmd.isWrite(cmd) || TlbCmd.isAmo(cmd)) && !cfg.w
    resp.instr := TlbCmd.isExec(cmd) && !cfg.x
    resp.mmio := false.B
    resp
  }

  def spmp_match_res(leaveHitMux: Boolean = false, valid: Bool = true.B)(
    addr: UInt,
    size: UInt,
    spmpEntries: Vec[PMPEntry],
    mode: UInt,
    lgMaxSize: Int,
    //tlbCsr: TlbCsrBundle
    sum: Bool
  ) = {
    val num = spmpEntries.size
    require(num == NumPMA)

    val passThrough = if (spmpEntries.isEmpty) true.B else (mode > 1.U)
    val spmpDefault = WireInit(0.U.asTypeOf(new PMPEntry()))
    spmpDefault.cfg.r := true.B
    spmpDefault.cfg.w := true.B
    spmpDefault.cfg.x := true.B

    val match_vec = Wire(Vec(num+1, Bool()))
    val cfg_vec = Wire(Vec(num+1, new PMPEntry()))

    spmpEntries.zip(spmpDefault +: spmpEntries.take(num-1)).zipWithIndex.foreach{ case ((spmp, last_spmp), i) =>
      val is_match = spmp.is_match(addr, size, lgMaxSize, last_spmp)
      val ignore = passThrough
      val aligned = spmp.aligned(addr, size, lgMaxSize, last_spmp)

      val cur = WireInit(spmp)
      // check logic （先判断再输出匹配，应该是用来优化时序）
      val spmpTTCfg = spmp_truth_table_match(spmp.cfg, mode, sum)
      cur.cfg.r := aligned && (spmpTTCfg.r || ignore)
      cur.cfg.w := aligned && (spmpTTCfg.w || ignore)
      cur.cfg.x := aligned && (spmpTTCfg.x || ignore)

      match_vec(i) := is_match
      cfg_vec(i) := cur
    }

    match_vec(num) := true.B
    cfg_vec(num) := spmpDefault

    if (leaveHitMux) {
      ParallelPriorityMux(match_vec.map(RegEnable(_, init = false.B, valid)), RegEnable(cfg_vec, valid))
    } else {
      ParallelPriorityMux(match_vec, cfg_vec)
    }
  }

  // 真值表冗余，下次换个方式写
  def spmp_truth_table_match(cfg: PMPConfig, mode: UInt, sum: Bool): PMPConfig = {
    val trueTable: Seq[(BitPat, UInt)] = Array(
      // S,R,W,X,Mode,SUM -> r,w,x (Mode: 1:S, 0:U)
      BitPat("b0000_??") -> "b000".U,

      BitPat("b0001_1?") -> "b000".U,
      BitPat("b0001_0?") -> "b001".U,
      
      BitPat("b0010_1?") -> "b110".U,
      BitPat("b0010_0?") -> "b100".U,
      
      BitPat("b0011_??") -> "b110".U,

      BitPat("b0100_10") -> "b000".U,
      BitPat("b0100_11") -> "b100".U,
      BitPat("b0100_0?") -> "b100".U,

      BitPat("b0101_10") -> "b000".U,
      BitPat("b0101_11") -> "b100".U,
      BitPat("b0101_0?") -> "b101".U,

      BitPat("b0110_10") -> "b000".U,
      BitPat("b0110_11") -> "b110".U,
      BitPat("b0110_0?") -> "b110".U,

      BitPat("b0111_10") -> "b000".U,
      BitPat("b0111_11") -> "b110".U,
      BitPat("b0111_0?") -> "b111".U,

      BitPat("b1000_0?") -> "b000".U, // reserved, U
      BitPat("b1000_1?") -> "b111".U, // S

      BitPat("b1001_1?") -> "b001".U,
      BitPat("b1001_0?") -> "b000".U,

      BitPat("b1010_??") -> "b001".U,

      BitPat("b1011_1?") -> "b101".U,
      BitPat("b1011_0?") -> "b001".U,

      BitPat("b1100_1?") -> "b100".U,
      BitPat("b1100_0?") -> "b000".U,

      BitPat("b1101_1?") -> "b101".U,
      BitPat("b1101_0?") -> "b000".U,

      BitPat("b1110_1?") -> "b110".U,
      BitPat("b1110_0?") -> "b000".U,

      BitPat("b1111_??") -> "b100".U
    )

    val cur = WireInit(cfg)
    val pat = Cat(cfg.s, cfg.r, cfg.w, cfg.x, mode(0), sum.asUInt)
    val rwx = Lookup[UInt](pat, 0.U(3.W), trueTable)

    cur.r := rwx(2)
    cur.w := rwx(1)
    cur.x := rwx(0)
    cur
  }

  // class TempBundle(implicit p: Parameters) extends XSBundle {
  //   val ssum = Bool()
  //   val mode = UInt(2.W)
  // }
}
