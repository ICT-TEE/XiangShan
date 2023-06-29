package xiangshan.cache.mmu

import chipsalliance.rocketchip.config.Parameters
import chisel3._
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
    val offset = UInt(34.W)
    val patp = UInt(XLEN.W)
  }))
  val miss = Output(Bool())
  val resp = Output(new PMPPerm())
}

class PlbPtwIO(implicit p: Parameters) extends TlbBundle {
  val req = DecoupledIO(new PMPtwReqIO)
  val resp = Flipped(ValidIO(new PMPtwRespIO))
}

class PlbReplaceAccessBundle(nSets: Int, nWays: Int)(implicit p: Parameters) extends TlbBundle {
  val sets = Output(UInt(log2Up(nSets).W))
  val touch_ways = ValidIO(Output(UInt(log2Up(nWays).W)))
}

class PlbIO(Width: Int, EntrySize: Int)(implicit p: Parameters)  extends MMUIOBaseBundle {
  val requestor = Vec(Width, new PlbRequestIO)
  val ptw = new PlbPtwIO
  val access = Vec(Width, new PlbReplaceAccessBundle(1, EntrySize))
}

class RespBundle(implicit p: Parameters) extends TlbBundle {
  val c = Bool()
  val x = Bool()
  val w = Bool()
  val r = Bool()
}

class PlbEntry(implicit p: Parameters) extends TlbBundle {
  val tag_offset = UInt(34.W)
  val tag_ppn = UInt(44.W)
  val asid = UInt(asidLen.W) //for sfence
  val level = UInt(1.W)
  val perm = UInt(64.W)

  def hit(offset: UInt, ppn: UInt, asid: UInt, ignoreAsid: Boolean = false): Bool = {
    val asid_hit = if (ignoreAsid) true.B else (this.asid === asid)
    val tag_ppn_match =  tag_ppn === ppn
    val tag_offset_match_hi = tag_offset(33, 25) === offset(33, 25)
    val tag_offset_match_lo = tag_offset(24, 16) === offset(24, 16)
    val tag_match = tag_offset_match_hi && (!level.asBool() || tag_offset_match_lo)
    asid_hit && tag_match && tag_ppn_match
  }
  def gen_resp(offset: UInt):UInt = {
    val perm_offset = offset(15, 12)
    val permvec = Wire(Vec(16, UInt(4.W)))
    for (i <- 0 until 16) {
      permvec(i) := perm(4*i+3, 4*i)
    }
    val resp = Wire(UInt(4.W))
      when (level.asBool()) { resp := permvec(perm_offset)
    }.otherwise{
    resp := perm(4, 1)
      }
  resp
  }
}
class PLB (Width: Int = 4, EntrySize: Int = 8, FilterSize: Int = 5)(implicit p: Parameters) extends TlbModule {
  val io = IO(new PlbIO(Width, EntrySize))
  val req = io.requestor.map(_.req)
  val miss = io.requestor.map(_.miss)
  val resp = io.requestor.map(_.resp)
  //val ptwresp = io.ptw.resp
  val sfence_dup = Seq.fill(2)(RegNext(io.sfence))
  val csr_dup = Seq.fill(Width)(RegNext(io.csr))
  val satp = csr_dup.head.satp

  io.ptw.req.bits.sourceId := 0.U

  val v = RegInit(VecInit(Seq.fill(EntrySize)(false.B)))
  val entries = Reg(Vec(EntrySize , new PlbEntry))
  val respvalid = RegInit(VecInit(Seq.fill(Width)(false.B)))

  //read
  for (i <- 0 until Width){
    val offset = req(i).bits.offset
    val ppn = req(i).bits.patp(43, 0)
    //val resp_miss = miss(i)
    //val resp_data = resp(i)
    val access = io.access(i)
    //val refill_mask = Mux(refill_valid, UIntToOH(refill_idx), 0.U(EntrySize.W))

    //val hitvec = VecInit((entries.zipWithIndex).zip (v zip refill_mask.asBools).map{case (e, m) => e._1.hit(offset, ppn, asid, true) && m._1 && !m._2})
    val hitvec = VecInit((entries.zipWithIndex).zip (v).map{case (e, m) => e._1.hit(offset, ppn, io.csr.satp.asid, true) && m })
    val resp_data = ParallelMux(hitvec zip entries.map(_.gen_resp(offset)))
    val hitvecreg = RegNext(hitvec)

    miss(i) := !(Cat(hitvec).orR)
    resp(i) := RegNext(resp_data.asTypeOf(new PMPPerm))

    when(!miss(i)){
      respvalid(i) := true.B //one cycle
    }.otherwise{
      respvalid(i) := false.B
    }

    //update replace_way
    access.sets := 0.U// no use
    access.touch_ways.valid := respvalid(i) && Cat(hitvecreg).orR
    access.touch_ways.bits := OHToUInt(hitvecreg)
  }

//sfence
val sfence = io.sfence

  when(io.sfence.valid && sfence.bits.rs1 && sfence.bits.rs2) {
        v.map(_ := false.B)  //all entries
  }

  //ptw -> filter
  val ptwreq= Flipped(ValidIO(new PMPtwReqIO))
  val filter_ptwreq = Wire(Vec(Width, ptwreq))
  val refill_valid = WireInit(false.B)

  for (i <- 0 until Width) {
    filter_ptwreq(i).valid := RegNext(miss(i) && req(i).valid, init = false.B) && !RegNext(refill_valid, init = false.B)
    filter_ptwreq(i).bits.offset := RegNext(req(i).bits.offset)
    filter_ptwreq(i).bits.ppn := RegNext(req(i).bits.patp(43, 0))
    filter_ptwreq(i).bits.sourceId := 0.U
  }

  //val FilterSize = 5
  //val v = RegInit(VecInit(Seq.fill(FilterSize)(false.B)))
  val filter_v = RegInit(VecInit(Seq.fill(FilterSize)(false.B)))
  val ports = Reg(Vec(FilterSize, Vec(Width, Bool()))) // record which port(s) the entry come from, may not able to cover all the ports
  val filter_offset = Reg(Vec(FilterSize, UInt(34.W)))
  val filter_ppn = Reg(Vec(FilterSize, UInt(44.W)))
  val enqPtr = RegInit(0.U(log2Up(FilterSize).W)) // Enq
  val issPtr = RegInit(0.U(log2Up(FilterSize).W)) // Iss to Ptw
  val deqPtr = RegInit(0.U(log2Up(FilterSize).W)) // Deq
  val mayFullDeq = RegInit(false.B)
  val mayFullIss = RegInit(false.B)
  val counter = RegInit(0.U(log2Up(FilterSize + 1).W))
  val flush = DelayN(io.sfence.valid || io.csr.satp.changed, 2) //todo
  //val tlb_req = WireInit(io.tlb.req)
  val plb_req = WireInit(filter_ptwreq)

  val inflight_counter = RegInit(0.U(log2Up(FilterSize + 1).W))
  val inflight_full = inflight_counter === FilterSize.U
  when(io.ptw.req.fire() =/= io.ptw.resp.fire()) {
    inflight_counter := Mux(io.ptw.req.fire(), inflight_counter + 1.U, inflight_counter - 1.U)
  }

  val ptwResp = RegEnable(io.ptw.resp.bits, io.ptw.resp.fire())
  //返回的与队列中有重复
  val ptwResp_OldMatchVec = (filter_offset.zip (filter_ppn) ).zip(filter_v).map { case (f, v) =>
    v && io.ptw.resp.bits.refill_hit(f._1, f._2, io.csr.satp.asid, true)
  }
  //与队列中有重复的返回才能有效
  val ptwResp_valid = RegNext(io.ptw.resp.fire() && Cat(ptwResp_OldMatchVec).orR, init = false.B)
  //4个新请求中与队列中有重复
  val oldMatchVec_early = filter_ptwreq.map(a => (filter_offset.zip(filter_ppn)).zip(filter_v).map { case (f, v) => v && (f._1 === a.bits.offset) && (f._2 === a.bits.ppn) })
  //4个新请求与4个旧请求中有重复
  val lastReqMatchVec_early = filter_ptwreq.map(a => plb_req.map{ b => b.valid && (b.bits.offset === a.bits.offset) && (b.bits.ppn === a.bits.ppn)})
  //4个新请求中有重复
  val newMatchVec_early = filter_ptwreq.map(a => filter_ptwreq.map(b => (a.bits.ppn === b.bits.ppn) && (a.bits.offset === b.bits.offset)))

  (0 until Width) foreach { i =>
    plb_req(i).valid := RegNext(filter_ptwreq(i).valid &&
      !(ptwResp_valid && ptwResp.refill_hit(filter_ptwreq(i).bits.offset, filter_ptwreq(i).bits.ppn, io.csr.satp.asid, true)) &&
      !Cat(lastReqMatchVec_early(i)).orR,
      init = false.B)//不能进入下一拍的请求有：新请求与上一拍返回的重复；新请求与旧请求有重复
    plb_req(i).bits := RegEnable(filter_ptwreq(i).bits, filter_ptwreq(i).valid)
  }

  val oldMatchVec = oldMatchVec_early.map(a => RegNext(Cat(a).orR))//4个新请求中与队列中有重复的下一拍
  val newMatchVec = (0 until Width).map(i => (0 until Width).map(j =>
    RegNext(newMatchVec_early(i)(j)) && plb_req(j).valid
  ))//4个新请求中有重复的下一拍
  val ptwResp_newMatchVec = plb_req.map(a =>
    ptwResp_valid && ptwResp.refill_hit(a.bits.offset, a.bits.ppn, 0.U, true))

  val oldMatchVec2 = (0 until Width).map(i => oldMatchVec_early(i).map(RegNext(_)).map(_ & plb_req(i).valid))
  val update_ports = filter_v.indices.map(i => oldMatchVec2.map(j => j(i)))
  val ports_init = (0 until Width).map(i => (1 << i).U(Width.W))
  val filter_ports = (0 until Width).map(i => ParallelMux(newMatchVec(i).zip(ports_init).drop(i)))
  val resp_vector = RegEnable(ParallelMux(ptwResp_OldMatchVec zip ports), io.ptw.resp.fire())

  def canMerge(index: Int): Bool = {
    ptwResp_newMatchVec(index) || oldMatchVec(index) ||
      Cat(newMatchVec(index).take(index)).orR
  }
//filter -> ptw
  def filter_req() = {
    val reqs = plb_req.indices.map { i =>
      val req = Wire(ptwreq)
      val merge = canMerge(i)
      req.bits := plb_req(i).bits
      req.valid := !merge && plb_req(i).valid
      //plb_req(i).ready := DontCare
      req
    }
    reqs
  }

  val reqs = filter_req()
  val req_ports = filter_ports
  val isFull = enqPtr === deqPtr && mayFullDeq
  val isEmptyDeq = enqPtr === deqPtr && !mayFullDeq
  val isEmptyIss = enqPtr === issPtr && !mayFullIss
  val accumEnqNum = (0 until Width).map(i => PopCount(reqs.take(i).map(_.valid)))
  val enqPtrVecInit = VecInit((0 until Width).map(i => enqPtr + i.U))
  val enqPtrVec = VecInit((0 until Width).map(i => enqPtrVecInit(accumEnqNum(i))))
  val enqNum = PopCount(reqs.map(_.valid))
  val canEnqueue = counter +& enqNum <= FilterSize.U
  // tlb req flushed by ptw resp: last ptw resp && current ptw resp
  // the flushed tlb req will fakely enq, with a false valid
  val plb_req_flushed = reqs.map(a => io.ptw.resp.valid && io.ptw.resp.bits.refill_hit(a.bits.offset, a.bits.ppn, 0.U, true))

  //io.tlb.req.map(_.ready := true.B) // NOTE: just drop un-fire reqs
  //io.tlb.resp.valid := ptwResp_valid
  //io.tlb.resp.bits.data := ptwResp
  //io.tlb.resp.bits.vector := resp_vector

  val issue_valid = filter_v(issPtr) && !isEmptyIss && !inflight_full
  val issue_filtered = ptwResp_valid && ptwResp.refill_hit(io.ptw.req.bits.offset, io.ptw.req.bits.ppn, io.csr.satp.asid, ignoreAsid = true)
  val issue_fire_fake = issue_valid && (io.ptw.req.ready || (issue_filtered && false.B /*timing-opt*/))
  io.ptw.req.valid := issue_valid && !issue_filtered
  io.ptw.req.bits.offset := filter_offset(issPtr)
  io.ptw.req.bits.ppn := filter_ppn(issPtr)
  // io.ptw.resp.ready := true.B

  reqs.zipWithIndex.map {
    case (req, i) =>
      when(req.valid && canEnqueue) {
        filter_v(enqPtrVec(i)) := !plb_req_flushed(i)
        filter_offset(enqPtrVec(i)) := req.bits.offset
        filter_ppn(enqPtrVec(i)) := req.bits.ppn
        ports(enqPtrVec(i)) := req_ports(i).asBools
      }
  }
  for (i <- ports.indices) {
    when(filter_v(i)) {
      ports(i) := ports(i).zip(update_ports(i)).map(a => a._1 || a._2)
    }
  }

  val do_enq = canEnqueue && Cat(reqs.map(_.valid)).orR
  val do_deq = (!filter_v(deqPtr) && !isEmptyDeq)
  val do_iss = issue_fire_fake || (!filter_v(issPtr) && !isEmptyIss)
  when(do_enq) {
    enqPtr := enqPtr + enqNum
  }
  when(do_deq) {
    deqPtr := deqPtr + 1.U
  }
  when(do_iss) {
    issPtr := issPtr + 1.U
  }
  when(issue_fire_fake && issue_filtered) { // issued but is filtered
    filter_v(issPtr) := false.B
  }
  when(do_enq =/= do_deq) {
    mayFullDeq := do_enq
  }
  when(do_enq =/= do_iss) {
    mayFullIss := do_enq
  }

  when(io.ptw.resp.fire()) {
    filter_v.zip(ptwResp_OldMatchVec).map { case (vi, mi) => when(mi) {
      vi := false.B
    }
    }
  }

  counter := counter - do_deq + Mux(do_enq, enqNum, 0.U)
  assert(counter <= FilterSize.U, "counter should be no more than FilterSize")
  assert(inflight_counter <= FilterSize.U, "inflight should be no more than FilterSize")
  when(counter === 0.U) {
    assert(!io.ptw.req.fire(), "when counter is 0, should not req")
    assert(isEmptyDeq && isEmptyIss, "when counter is 0, should be empty")
  }
  when(counter === FilterSize.U) {
    assert(mayFullDeq, "when counter is FilterSize, should be full")
  }

  when(flush) {
    filter_v.map(_ := false.B)
    deqPtr := 0.U
    enqPtr := 0.U
    issPtr := 0.U
    ptwResp_valid := false.B
    mayFullDeq := false.B
    mayFullIss := false.B
    counter := 0.U
    inflight_counter := 0.U
  }

  //refill
  refill_valid := ptwResp_valid && !sfence_dup.head.valid && !satp.changed
  val re = ReplacementPolicy.fromString("plru", EntrySize)
  re.access(io.access.map(_.touch_ways))
  val refill_idx = re.way

  when(refill_valid) {
    v(refill_idx) := true.B
    entries(refill_idx).tag_offset := ptwResp.offset
    entries(refill_idx).tag_ppn := ptwResp.ppn
    entries(refill_idx).asid := io.csr.satp.asid
    entries(refill_idx).level := ptwResp.level
    entries(refill_idx).perm := ptwResp.data
  }

  //val refill_offset_reg = RegNext(ptwresp.bits.offset)
  val refill_wayIdx_reg = RegNext(refill_idx)
  //update replace_way
  when(RegNext(refill_valid)) {
    io.access.map { access =>
      access.sets := 0.U
      access.touch_ways.valid := true.B
      access.touch_ways.bits := refill_wayIdx_reg
    }
  }

  implicit class RespIO(resp: PMPtwRespIO) {
    def refill_hit(offset: UInt, ppn: UInt, asid: UInt, ignoreAsid: Boolean = false): Bool = {
      val asid_hit = if (ignoreAsid) true.B else (resp.asid === asid)
      val tag_ppn_match = resp.ppn === ppn
      val tag_offset_match_hi = resp.offset(33, 25) === offset(33, 25)
      val tag_offset_match_lo = resp.offset(24, 16) === offset(24, 16)
      val tag_match = tag_offset_match_hi && (!resp.level.asBool() || tag_offset_match_lo)
      asid_hit && tag_match && tag_ppn_match
    }
  }
}