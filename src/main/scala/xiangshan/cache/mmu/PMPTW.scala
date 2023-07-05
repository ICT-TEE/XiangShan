/***************************************************************************************
* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
* Copyright (c) 2020-2021 Peng Cheng Laboratory
*
* XiangShan is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

package xiangshan.cache.mmu

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util._
import chisel3.internal.naming.chiselName
import xiangshan._
import utils._
import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink._

trait HasPMPtwConst {
  val PMPtwWidth = 3

  val PMPtwSize = 6
  val MemReqWidth = PMPtwSize

  val BlockBytes = 64
}

class PMPtwReqIO(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val offset = UInt(34.W)
  val ppn = UInt(44.W)
  val sourceId = UInt(log2Up(PMPtwWidth).W) // use number distinguish source
}

class PMPtwRespIO(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val offset = UInt(34.W)
  val ppn = UInt(44.W)
  val level = UInt(1.W)
  val data = UInt(64.W)
  val asid = UInt(asidLen.W)
  val sourceIds = UInt(PMPtwWidth.W) // use index distinguish source
}

class MemReqIO(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val addr = UInt(PAddrBits.W)
  val id = UInt(log2Up(MemReqWidth).W)
}

class MemRespIO(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val data = UInt(64.W)
  val id = UInt(log2Up(MemReqWidth).W)
}

class L2Data(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val data = UInt((32 * 8).W)
  val beat = UInt(2.W)
  val corrupt = Bool()
}

class PMPtwIO(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val req = Vec(PMPtwWidth, Flipped(DecoupledIO(new PMPtwReqIO)))
  val resp = Vec(PMPtwWidth, Valid(new PMPtwRespIO))
  val sfence = Input(new SfenceBundle)
  val csr = Input(new TlbCsrBundle)
}

class PMPTW()(implicit p: Parameters) extends LazyModule with HasPMPtwConst {

  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = Seq(TLMasterParameters.v1(
      "pmptw",
      sourceId = IdRange(0, MemReqWidth)
    )),
    // requestFields = Seq(PreferCacheField())
  )))

  lazy val module = new PMPTWImp(this)
}

@chiselName
class PMPTWImp(outer: PMPTW)(implicit p: Parameters) extends LazyModuleImp(outer)
  with HasXSParameter with HasPMPtwConst { //with HasPerfEvents {
  val (mem, edge) = outer.node.out.head

  val io = IO(new PMPtwIO)

  val pmpt = Module(new BasePMPTW)
  val pmpt_arb = Module(new Arbiter(new PMPtwReqIO, PMPtwWidth))
  /* pmptw */
  // req
  // io.req.zipWithIndex.map{ case (req, i) =>   // alloc source id
  //   req.bits.sourceId := i.U
  // }
  pmpt_arb.io.in <> io.req
  pmpt.io.req <> pmpt_arb.io.out
  pmpt_arb.io.in.zipWithIndex.map{ case (req, i) =>   // alloc source id
    req.bits.sourceId := i.U
  }

  // resp
  io.resp.map(_.valid := false.B)
  io.resp.map(_.bits := pmpt.io.resp.bits)
  when (pmpt.io.resp.valid) {
    io.resp.zip(pmpt.io.resp.bits.sourceIds.asBools).map{ case (r, s) =>
      r.valid := s
    }
  }
  io.resp.map(_.bits.sourceIds := 0.U)  // delete source id
  // flush
  pmpt.io.flush := io.sfence.valid || io.csr.satp.changed

  // mem
  // get_part函数负责将从L2取到的一个Cacheline的数据切片，取其中的8 Bytes， 即64 bits
  def get_part(data: Vec[UInt], index: UInt): UInt = {
    val inner_data = data.asTypeOf(Vec(data.getWidth / XLEN, UInt(XLEN.W)))
    inner_data(index)
  }
  def addr_low_from_paddr(paddr: UInt) = {
    paddr(log2Up(BlockBytes) - 1, log2Up(XLEN / 8))
  }

  // 取发送到L2的请求信息作为从L2取回数据的“mask”
  val req_addr_low = RegInit(VecInit(Seq.fill(MemReqWidth)(0.U((log2Up(BlockBytes) - log2Up(XLEN / 8)).W))))
  when (pmpt.io.mem.req.valid) {
    req_addr_low(pmpt.io.mem.req.bits.id) := addr_low_from_paddr(pmpt.io.mem.req.bits.addr)
  }
  // 将id、addr、size封装到Get请求中并发送到A通道
  val memRead = edge.Get( // 确定请求的类型为Get
    fromSource = pmpt.io.mem.req.bits.id,
    toAddress = pmpt.io.mem.req.bits.addr, // 是否存在地址宽度不匹配的问题
    lgSize = log2Up(BlockBytes).U
  )._2
  mem.a.bits := memRead
  mem.a.valid := pmpt.io.mem.req.valid && !pmpt.io.flush
  mem.d.ready := true.B
  // mem -> data buffer
  // 从L2取回两个Beats的数据
  val refill_data = Reg(Vec(2, UInt((32 * 8).W)))
  val refill_helper = edge.firstlastHelper(mem.d.bits, mem.d.fire())
  val mem_resp_done = refill_helper._3
  when (mem.d.valid) {
    assert(mem.d.bits.source <= PMPtwSize.U)  // 不知道有什么用
    refill_data(refill_helper._4) := mem.d.bits.data
  }

  pmpt.io.mem.resp.valid := mem_resp_done
  val resp_back = get_part(refill_data, req_addr_low(mem.d.bits.source))
  pmpt.io.mem.resp.bits.data := resp_back
  pmpt.io.mem.resp.bits.id := mem.d.bits.source
}

// real PMPTW logic
class PMPtwEntry(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val offset = UInt(34.W)
  val ppn = UInt(44.W)
  val sourceIds = UInt(PMPtwWidth.W)
  val rootData = UInt(64.W)
  val level = UInt(1.W)
}

class BasePMPTW(implicit p: Parameters) extends XSModule with HasPMPtwConst {
  val io = IO(new Bundle {
    val req = Flipped(DecoupledIO(new PMPtwReqIO))
    val resp = Valid(new PMPtwRespIO)
    val flush = Input(Bool())
    val mem = new Bundle {
      val req = Valid(new MemReqIO)
      val resp = Flipped(Valid(new MemRespIO))
    }
  })

  val entries = Reg(Vec(PMPtwSize, new PMPtwEntry()))
  // waste 3 cycle
  val s_empty :: s_l1req :: s_l1resp :: s_l2req :: s_l2resp :: s_deq :: Nil = Enum(6);
  val state = RegInit(VecInit(Seq.fill(PMPtwSize)(s_empty)))

  val empty_vec   = state.map(_ === s_empty)
  val l1req_vec   = state.map(_ === s_l1req)
  val l1resp_vec  = state.map(_ === s_l1resp)
  val l2req_vec   = state.map(_ === s_l2req)
  val l2resp_vec  = state.map(_ === s_l2resp)
  val deq_vec     = state.map(_ === s_deq)

  val full = !ParallelOR(empty_vec)
  val enq_ptr = ParallelPriorityEncoder(empty_vec)
  val deq_fire = ParallelOR(deq_vec)
  val deq_ptr = ParallelPriorityEncoder(deq_vec)

  val l1mem_req = ParallelOR(l1req_vec)
  val l2mem_req = ParallelOR(l2req_vec)
  val l1req_ptr = ParallelPriorityEncoder(l1req_vec)
  val l2req_ptr = ParallelPriorityEncoder(l2req_vec)

  val resp_data = RegInit(0.U(64.W))

  val tag_eq_vec = entries.indices.map(i =>
    state(i) =/= s_empty && state(i) =/= s_deq &&
    makeTag(io.req.bits.ppn, io.req.bits.offset) ===
    makeTag(entries(i).ppn, entries(i).offset)
  )
  val tag_eq = ParallelOR(tag_eq_vec)
  val tag_eq_ptr = ParallelPriorityEncoder(tag_eq_vec)

  /* input */
  io.req.ready := !full

  when (io.req.fire()) {
    when (!tag_eq) {
      entries(enq_ptr).offset := io.req.bits.offset
      entries(enq_ptr).ppn := io.req.bits.ppn
      entries(enq_ptr).sourceIds :=
        setBits(entries(enq_ptr).sourceIds, io.req.bits.sourceId)
      entries(enq_ptr).level := 0.U
      state(enq_ptr) := s_l1req

    }.otherwise { // tag equal merge
      entries(tag_eq_ptr).sourceIds :=
        setBits(entries(tag_eq_ptr).sourceIds, io.req.bits.sourceId)
    }
  }

  /* mem req */
  io.mem.req.valid := false.B
  io.mem.req.bits := 0.U.asTypeOf(new MemReqIO)
  when (l2mem_req && !isPte(entries(l2req_ptr).rootData)) {
    io.mem.req.valid := true.B
    io.mem.req.bits.id := l2req_ptr
    io.mem.req.bits.addr :=
      makeAddr(1.U, getRootPpn(entries(l2req_ptr).rootData), entries(l2req_ptr).offset)
    entries(l2req_ptr).level := 1.U
    state(l2req_ptr) := s_l2resp

  }.elsewhen (l1mem_req) {
    io.mem.req.valid := true.B
    io.mem.req.bits.id := l1req_ptr
    io.mem.req.bits.addr :=
      makeAddr(0.U, entries(l1req_ptr).ppn, entries(l1req_ptr).offset)
    state(l1req_ptr) := s_l1resp
  }

  /* mem resp */
  when (io.mem.resp.valid) {
    val resp_state = state(io.mem.resp.bits.id)
    when (resp_state === s_l1resp) {
      entries(io.mem.resp.bits.id).rootData := io.mem.resp.bits.data
      resp_state := Mux(isPte(io.mem.resp.bits.data), s_deq, s_l2req)

    }.elsewhen (resp_state === s_l2resp) {
      resp_data := io.mem.resp.bits.data
      resp_state := s_deq
    }
  }

  /* resp */
  io.resp.valid := false.B
  io.resp.bits := 0.U.asTypeOf(new PMPtwRespIO)
  when (deq_fire) {
    io.resp.valid := true.B
    io.resp.bits.level := entries(deq_ptr).level
    io.resp.bits.offset := entries(deq_ptr).offset
    io.resp.bits.ppn := entries(deq_ptr).ppn
    io.resp.bits.sourceIds := entries(deq_ptr).sourceIds
    io.resp.bits.data := Mux(
      entries(deq_ptr).level === 1.U, resp_data, entries(deq_ptr).rootData)
    state(deq_ptr) := s_empty
  }

  /* flush */
  when (io.flush) {
    io.mem.req.valid := false.B
    io.req.ready := false.B
    io.resp.valid := false.B

    state.map(_ := s_empty)
  }

  /* funtion */
  def isPte(rootData: UInt): Bool = {
    (rootData(0) === 0.U) || (rootData(3, 0) =/= 1.U)
  }

  def getRootPpn(rootData: UInt) = {
    rootData(48, 5)
  }

  def makeAddr(level: UInt, ppn: UInt, offset: UInt) = {
    val paddr = Cat(ppn, Mux(level.asBool, offset(24, 16), offset(33, 25)), 0.U(3.W))
    paddr(PAddrBits-1, 0)
  }

  def makeTag(ppn: UInt, offset: UInt) = {
    Cat(ppn, offset(33, 16))
  }

  def setBits(bits: UInt, pos: UInt) = {
    bits | (1.U << pos)
  }
}
