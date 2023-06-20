package xiangshan.cache.mmu

import chipsalliance.rocketchip.config.Parameters
import chisel3.{Mux, _}
import chisel3.internal.naming.chiselName
import chisel3.util._
import freechips.rocketchip.util.SRAMAnnotation
import xiangshan._
import utils._
import xiangshan.backend.fu.{PMPChecker, PMPReqBundle, PMPPerm}
import xiangshan.backend.rob.RobPtr
import xiangshan.backend.fu.util.HasCSRConst

class PlbRequestIO(implicit p: Parameters) extends TlbBundle {
  val req = Flipped(ValidIO(new Bundle {
    val offset = UInt(33.W)
    val patp = UInt(XLEN.W)
  }))
  val miss = Output(Bool())
  val resp = Output(new PMPPerm())
}

class PlbPtwIO(implicit p: Parameters) extends TlbBundle {
  val req = DecoupledIO(new Bundle {
    val offset = UInt(33.W)
    val patp = UInt(XLEN.W)
    val l1Hit = Bool()
  })
  val resp = Flipped(DecoupledIO(new Bundle {
    val offset = UInt(33.W)
    val patp = UInt(XLEN.W)
    val level = Bool()
    val data = UInt(XLEN.W)
  }))
}

class PlbIO(Width: Int, q: TLBParameters)(implicit p: Parameters)  extends TlbBundle {
  val requestor = Vec(Width, new PlbRequestIO)
  val ptw = new PlbPtwIO
}


class PLB (Width: Int, q: TLBParameters)(implicit p: Parameters) extends TlbModule {
  val io = IO(new PlbIO(Width , q))
  val req = io.requestor.map(_.req)
  val miss = io.requestor.map(_.miss)
  val resp = io.requestor.map(_.resp)


  val cnt = Vec(Width, RegInit(0.U(3.W)))

  for ( i <- 0 until Width ) {
    val rand = Vec(Width, LFSR64(req(i).fire)(3,0))
    when(req(i).fire) {
      cnt(i) := Mux(rand(i)(3), rand(i)(2, 0), 0.U)
    }.otherwise {
      cnt(i) := Mux(cnt(i) =/= 0.U, cnt(i) - 1.U, cnt(i))
    }
    miss(i) := Mux(req(i).fire, !(Mux(rand(i)(3), rand(i)(2, 0), 0.U) === 0.U), !(cnt(i) === 0.U))
  }

  /*val normalPage = TlbStorage(
    name = "normal",
    associative = q.normalAssociative,
    sameCycle = q.sameCycle,
    ports = Width,
    nSets = q.normalNSets,
    nWays = q.normalNWays,
    nDups = nRespDups,
    saveLevel = q.saveLevel,
    normalPage = true,
    superPage = false
  )
  val superPage = TlbStorage(
    name = "super",
    associative = q.superAssociative,
    sameCycle = q.sameCycle,
    ports = Width,
    nSets = q.superNSets,
    nWays = q.superNWays,
    saveLevel = q.saveLevel,
    normalPage = q.normalAsVictim,
    superPage = true,
  )*/
}
