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
import xiangshan._
import utils._
import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink._

trait HasPMPtwConst {
  val PMPtwWidth = 3

  val PMPtwSize = 6
  val MemReqWidth = PMPtwSize
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
  val sourceIds = UInt(PMPtwWidth.W) // use index distinguish source
}

class MemReqIO(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val addr = UInt(56.W)
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

class PMPTW(val parentName:String = "Unknown")(implicit p: Parameters) extends LazyModule with HasPMPtwConst {

  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = Seq(TLMasterParameters.v1(
      "ptw",
      sourceId = IdRange(0, MemReqWidth)
    )),
    // requestFields = Seq(PreferCacheField())
  )))

  lazy val module = new PMPTWImp(this)
}

// @chiselName
class PMPTWImp(outer: PMPTW)(implicit p: Parameters) extends LazyModuleImp(outer)
  with HasXSParameter with HasPMPtwConst { //with HasPerfEvents {
  val (mem, edge) = outer.node.out.head

  val io = IO(new PMPtwIO)

  val pmpt = Module(new BasePMPTW)
  val pmpt_arb = Module(new Arbiter(new PMPtwReqIO, PMPtwWidth))
  /* pmptw */
  // req
  io.req.zipWithIndex.map{ case (req, i) =>   // alloc source id
    req.bits.sourceId := i.U
  }
  pmpt_arb.io.in <> io.req
  pmpt.io.req <> pmpt_arb.io.out

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
    paddr(log2Up(l2tlbParams.blockBytes) - 1, log2Up(XLEN / 8))
  }

  // 取发送到L2的请求信息作为从L2取回数据的“mask”
  val req_addr_low = Reg(Vec(MemReqWidth, UInt(log2Up(l2tlbParams.blockBytes) - log2Up(XLEN / 8)).W))
  when (pmpt.io.mem.req.fire()) {
    req_addr_low(pmpt.io.mem.req.bits.id) := addr_low_from_paddr(pmpt.io.mem.req.bits.id)
  }
  // 将id、addr、size封装到Get请求中并发送到A通道
  val memRead = edge.Get( // 确定请求的类型为Get
    fromSource = pmpt.io.mem.req.bits.id,
    toAddress = pmpt.io.mem.req.bits.addr, // 是否存在地址宽度不匹配的问题
    lgSize = log2Up(l2tlbParams.blockBytes).U
  ).v2
  mem.a.bits := memRead
  mem.a.valid := pmpt.io.mem.req.valid && !pmpt.io.flush
  mem.d.ready := true.B
  // mem -> data buffer
  // 从L2取回两个Beats的数据
  val refill_data = Reg(Vec(2, UInt((32 * 8).W)))
  val refill_helper = edge.firstlastHelper(mem.d.bits, mem.d.fire())
  val mem_resp_done = refill_helper._3
  when (mem.d.valid) {
    assert(mem.d.bits.source <= l2tlbParams.llptwsize.U)  // 不知道有什么用
    refill_data(refill_helper._4) := mem.d.bits.data
  }

  val resp_pte = RegEnable(get_part(refill_data, req_addr_low(i)), mem_resp_done)
  pmpt.io.mem.resp.bits := resp_pte
  // TODO
}

// real PMPTW logic
class PMPtwEntry(implicit p: Parameters) extends XSBundle with HasPMPtwConst {
  val offset = UInt(34.W)
  val ppn = UInt(44.W)
  val sourceIds = UInt(PMPtwWidth.W)
  val rootData = UInt(64.W)
  val level = UInt(1.W)
  val running = Bool() // valid
}

class BasePMPTW(implicit p: Parameters) extends XSModule with HasPMPtwConst with HasCircularQueuePtrHelper {
  val io = IO(new Bundle {
    val req = Flipped(DecoupledIO(new PMPtwReqIO))
    val resp = Valid(new PMPtwRespIO)
    val flush = Input(Bool())
    val mem = new Bundle {
      val req = Valid(new MemReqIO)  // 是否是反的
      val resp = FLipped(Valid(new MemRespIO)) // Base始终ready?
    }
  })

  class PMPtwPtr(implicit p: Parameters) extends CircularQueuePtr[PMPtwPtr](PMPtwSize) {
    def flush() = {
      this.flag := false.B
      this.value := 0.U
    }
  }

  object PMPtwPtr {
    def apply()(implicit p: Parameters): PMPtwPtr = {
      val ptr = Wire(new PMPtwPtr)
      ptr.flag := false.B
      ptr.value := 0.U
      ptr
    }
  }

  val enqPtr, l1ReqPtr, l1RespPtr, l2ReqPtr, l2RespPtr, deqPtr = RegInit(PMPtwPtr())
  val entries = Reg(Vec(PMPtwSize, new PMPtwEntry()))

  val resp_data = RegInit(0.U(64.W))
  val is_pte = WireInit(false.B)

  val l1Req_fire  = enqPtr =/= l1ReqPtr && l2ReqPtr === l1RespPtr
  val l1Resp_fire = io.mem.resp.bits.id === l1RespPtr.value
  val l2Req_fire  = l2ReqPtr =/= l1RespPtr
  val l2Resp_fire = io.mem.resp.bits.id === l2RespPtr.value
  val deq_fire    = l2RespPtr =/= deqPtr

  val tag_eq_vec = entries.indices.map(i =>
    entries(i).running &&
    makeTag(io.req.bits.ppn, io.req.bits.offset) ===
    makeTag(entries(i).ppn, entries(i).offset)

  )
  val tag_eq = ParallelOR(tag_eq_vec)
  val tag_eq_ptr = ParallelPriorityEncoder(tag_eq_vec)

  /* input */
  io.req.ready := !isFull(enqPtr, deqPtr)

  when (io.req.fire()) {
    when (!tag_eq) {
      entries(enqPtr.value).offset := io.req.bits.offset
      entries(enqPtr.value).ppn := io.req.bits.ppn
      entries(enqPtr.value).sourceIds :=
        setBits(entries(enqPtr.value).sourceIds, io.req.bits.sourceId)
      entries(enqPtr.value).running := true.B

    }.otherwise { // tag equal merge
      entries(tag_eq_ptr).sourceIds :=
        setBits(entries(tag_eq_ptr).sourceIds, io.req.bits.sourceId)
    }
  }

  /* mem req */
  io.mem.req.valid := false.B
  when (l2Req_fire && !is_pte) {
    io.mem.req.valid := true.B
    io.mem.req.bits.id := l2ReqPtr.value
    io.mem.req.bits.addr :=
      makeAddr(1.U, getRootPpn(entries(l2ReqPtr.value).rootData), entries(l2ReqPtr.value).offset)

  }.elsewhen (l1Req_fire) {
    io.mem.req.valid := true.B
    io.mem.req.bits.id := l1ReqPtr.value
    io.mem.req.bits.addr := 
      makeAddr(0.U, entries(l1ReqPtr.value).ppn, entries(l1ReqPtr.value).offset)
  }

  /* mem resp */
  when (l1Resp_fire) {
    entries(l1RespPtr.value).rootData := io.mem.resp.bits.data

  }.elsewhen (l2Resp_fire) {
    resp_data := io.mem.resp.bits.data
  }

  /* resp */
  io.resp.valid := false.B
  when (deq_fire) {
    io.resp.valid := true.B
    io.resp.bits.level := entries(deqPtr.value).level
    io.resp.bits.offset := entries(deqPtr.value).offset
    io.resp.bits.ppn := entries(deqPtr.value).ppn
    io.resp.bits.sourceIds := entries(deqPtr.value).sourceIds
    io.resp.bits.data := Mux(
      io.resp.bits.level === 1.U ,resp_data, entries(deqPtr.value).rootData)
    entries(enqPtr.value).running := false.B
  }

  /* pte */
  // for l2Req_fire
  is_pte := isPte(entries(l2ReqPtr.value).rootData)
  when (l2Req_fire && !is_pte) {
    entries(l2ReqPtr.value).level := 1.U
  }

  /* update ptr */
  enqPtr := Mux(io.req.fire && !tag_eq, enqPtr + 1.U, enqPtr)
  deqPtr := Mux(deq_fire, deqPtr + 1.U, deqPtr)

  l1ReqPtr  := Mux(l1Req_fire, l1ReqPtr + 1.U, l1ReqPtr)
  l1RespPtr := Mux(l1Resp_fire, l1RespPtr + 1.U, l1RespPtr)
  l2ReqPtr  := Mux(l2Req_fire, l2ReqPtr + 1.U, l2ReqPtr)
  l2RespPtr := Mux(l2Resp_fire, l2RespPtr + 1.U, l2RespPtr)

  /* flush */
  when (io.flush) {
    io.mem.req.valid := false.B
    io.req.ready := false.B
    io.resp.valid := false.B

    enqPtr.flush()
    l1ReqPtr.flush()
    l1RespPtr.flush()
    l2ReqPtr.flush()
    l2RespPtr.flush()
    deqPtr.flush()
  }

  /* funtion */
  def isPte(rootData: UInt): Bool = {
    (rootData(0) === 0.U) || (rootData(3, 0) =/= 1.U)
  }

  def getRootPpn(rootData: UInt) = {
    rootData(48, 5)
  }

  def makeAddr(level: UInt, ppn: UInt, offset: UInt) = {
    Cat(ppn, Mux(level.asBool, offset(24, 16), offset(33, 25)), 0.U(3.W))
  }

  def makeTag(ppn: UInt, offset: UInt) = {
    Cat(ppn, offset(33, 16))
  }

  def setBits(bits: UInt, pos: UInt) = {
    bits | (1.U << pos)
  }
}
