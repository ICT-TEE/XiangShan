package device_test

import chisel3._
import chisel3.RawModule
// import Chisel._
import chisel3.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import chisel3.experimental._
import freechips.rocketchip.regmapper.{RegField, RegFieldAccessType, RegFieldDesc, RegFieldGroup}

class interruptIO extends Bundle{
    val intr_hmac_hmac_done_o = Output(Bool())
    val intr_hmac_fifo_empty_o = Output(Bool())  
    val intr_hmac_hmac_err_o = Output(Bool())

    val intr_kmac_kmac_done_o = Output(Bool())
    val intr_kmac_fifo_empty_o = Output(Bool())
    val intr_kmac_kmac_err_o = Output(Bool())

    val intr_keymgr_op_done_o = Output(Bool())

    val intr_csrng_cs_cmd_req_done_o = Output(Bool())
    val intr_csrng_cs_entropy_req_o = Output(Bool())
    val intr_csrng_cs_hw_inst_exc_o = Output(Bool())
    val intr_csrng_cs_fatal_err_o = Output(Bool())

    val intr_entropy_src_es_entropy_valid_o = Output(Bool())
    val intr_entropy_src_es_health_test_failed_o = Output(Bool())
    val intr_entropy_src_es_observe_fifo_ready_o = Output(Bool())
    val intr_entropy_src_es_fatal_err_o = Output(Bool())

    val intr_edn0_edn_cmd_req_done_o = Output(Bool())
    val intr_edn0_edn_fatal_err_o = Output(Bool())
}


class TLROT_top extends BlackBox with HasBlackBoxResource {
  val bundleParams  = TLBundleParameters(
  addressBits = 32,
  dataBits = 32,
  sourceBits = 8,
  sinkBits = 8,
  sizeBits = 3,
  echoFields = Seq(),
  requestFields = Seq(),
  responseFields = Seq(),
  hasBCE = false 
)
  val TL_AW = UInt(32.W)
  val TL_DW = UInt(32.W)
  val TL_AIW = UInt(8.W)
  val TL_DIW = UInt(1.W)
  val TL_DBW = UInt(4.W)
  val TL_SZW = UInt(2.W)

  val io = IO(new Bundle {
    val clk_i = Input(Clock())
    val rst_ni = Input(AsyncReset())

    // val tl_i = Input(new TlH2d())
    // val tl_o = Output(new TlD2h())

    // val tl_i = Input(new TLBundleA(bundleParams))
    // val tl_i = Input(Decoupled(new TLBundleA(bundleParams)))

    // val tl_o = Output(Decoupled(new TLBundleD(bundleParams)))

    // val done_o = Output(Bool())
    

    val a_valid = Input(Bool())
    val a_bits_opcode = Input(UInt(3.W))
    val a_bits_param = Input(UInt(3.W))
    val a_bits_size = Input(TL_SZW)
    val a_bits_source = Input(TL_AIW)
    val a_bits_address = Input(TL_AW)
    val a_bits_mask = Input(TL_DBW)
    val a_bits_data = Input(TL_DW)
    val a_ready = Output(Bool())

    val d_valid = Output(Bool())
    val d_bits_opcode = Output(UInt(3.W))
    val d_bits_param = Output(UInt(3.W))
    val d_bits_size = Output(TL_SZW)
    val d_bits_source = Output(TL_AIW)
    val d_bits_sink = Output(TL_DIW)
    val d_bits_data = Output(TL_DW)
    val d_bits_denied = Output(Bool())
    val d_ready = Input(Bool())

    val intr_hmac_hmac_done_o = Output(Bool())
    val intr_hmac_fifo_empty_o = Output(Bool())  
    val intr_hmac_hmac_err_o = Output(Bool())

    val intr_kmac_kmac_done_o = Output(Bool())
    val intr_kmac_fifo_empty_o = Output(Bool())
    val intr_kmac_kmac_err_o = Output(Bool())

    val intr_keymgr_op_done_o = Output(Bool())

    val intr_csrng_cs_cmd_req_done_o = Output(Bool())
    val intr_csrng_cs_entropy_req_o = Output(Bool())
    val intr_csrng_cs_hw_inst_exc_o = Output(Bool())
    val intr_csrng_cs_fatal_err_o = Output(Bool())

    val intr_entropy_src_es_entropy_valid_o = Output(Bool())
    val intr_entropy_src_es_health_test_failed_o = Output(Bool())
    val intr_entropy_src_es_observe_fifo_ready_o = Output(Bool())
    val intr_entropy_src_es_fatal_err_o = Output(Bool())

    val intr_edn0_edn_cmd_req_done_o = Output(Bool())
    val intr_edn0_edn_fatal_err_o = Output(Bool())
  })

  addResource("/TLROT/TLROT_top.sv")
  // addResource("/tlul_pkg.sv")
}



class TLROT_blackbox(implicit p: Parameters) extends LazyModule {
  // val device = new SimpleDevice("tlrot", Seq("sifive,dtim0"))
  val beatBytes = 4
  // val mem = SyncReadMem(0x1000, UInt(32.W))
  // val regmap = RegField.map("mem" -> mem)

  val tlrotAddr = ResourceAddress(
    address = Seq(AddressSet(0x39000000L, 0xffffff)),
    permissions = ResourcePermissions(
      r = true,
      w = true,
      x = false, 
      c = false,
      a = false
    )
  )
  val tlrotDevice = new SimpleDevice("tlrot", Seq("sifive,tlrot0"))
  val tlrotResource = Resource(tlrotDevice, "tlrot")

  // val tlrot = Module(new TLROT_top)

  // Create a TLManagerNode
  val node = TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(TLSlaveParameters.v1(
    address = Seq(AddressSet(0x3b000000, 0xffffff)), 
    // resources = device.reg("mem"),
    resources = Seq(
      // tlrotAddr, 
      tlrotResource
    ),
    regionType         = RegionType.IDEMPOTENT,
    supportsGet        = TransferSizes(1, beatBytes),
    supportsPutFull    = TransferSizes(1, beatBytes),
    supportsPutPartial = TransferSizes(1, beatBytes),
    fifoId             = Some(0))),
     beatBytes)))

  lazy val module = new LazyModuleImp(this) {
    val io_rot = IO(new Bundle { 
      val clock = Input(Clock())
      val reset = Input(AsyncReset())
      val intr = Output(Vec(17,Bool()))
      // val intr = Output(new interruptIO)

      // val intr_hmac_hmac_done_o = Output(Bool())
      // val intr_hmac_fifo_empty_o = Output(Bool())  
      // val intr_hmac_hmac_err_o = Output(Bool())

      // val intr_kmac_kmac_done_o = Output(Bool())
      // val intr_kmac_fifo_empty_o = Output(Bool())
      // val intr_kmac_kmac_err_o = Output(Bool())

      // val intr_keymgr_op_done_o = Output(Bool())

      // val intr_csrng_cs_cmd_req_done_o = Output(Bool())
      // val intr_csrng_cs_entropy_req_o = Output(Bool())
      // val intr_csrng_cs_hw_inst_exc_o = Output(Bool())
      // val intr_csrng_cs_fatal_err_o = Output(Bool())

      // val intr_entropy_src_es_entropy_valid_o = Output(Bool())
      // val intr_entropy_src_es_health_test_failed_o = Output(Bool())
      // val intr_entropy_src_es_observe_fifo_ready_o = Output(Bool())
      // val intr_entropy_src_es_fatal_err_o = Output(Bool())

      // val intr_edn0_edn_cmd_req_done_o = Output(Bool())
      // val intr_edn0_edn_fatal_err_o = Output(Bool())
  })
    val (in, edge) = node.in(0)
    dontTouch(in)
    dontTouch(io_rot.reset)

    // val rot = withClock(clock) {
    //   // val tlrot = Module(new TLROT)
    //    Module(new TLROT)
    //  }    
    val tlrot = Module(new TLROT_top)
    in.a.ready := tlrot.io.a_ready
    // tlrot.io.a_ready := in.a.ready
    tlrot.io.a_valid := in.a.valid
    tlrot.io.a_bits_opcode := in.a.bits.opcode
    tlrot.io.a_bits_param := in.a.bits.param
    tlrot.io.a_bits_size := in.a.bits.size
    tlrot.io.a_bits_source := in.a.bits.source
    tlrot.io.a_bits_address := in.a.bits.address
    tlrot.io.a_bits_mask := in.a.bits.mask
    tlrot.io.a_bits_data := in.a.bits.data
    
    // dontTouch(in.d.ready)
    // in.d.ready :=  tlrot.io.d_ready
    // tlrot.io.d_valid :=  in.d.valid
    in.d.valid := tlrot.io.d_valid
    tlrot.io.d_ready := in.d.ready
    in.d.bits.opcode := tlrot.io.d_bits_opcode
    in.d.bits.param := tlrot.io.d_bits_param
    in.d.bits.size := tlrot.io.d_bits_size
    in.d.bits.source := tlrot.io.d_bits_source
    in.d.bits.sink := tlrot.io.d_bits_sink
    in.d.bits.data := tlrot.io.d_bits_data
    in.d.bits.denied := tlrot.io.d_bits_denied

   
    io_rot.intr(0) := tlrot.io.intr_hmac_hmac_done_o
    io_rot.intr(1) := tlrot.io.intr_hmac_fifo_empty_o
    io_rot.intr(2) := tlrot.io.intr_hmac_hmac_err_o

    io_rot.intr(3) := tlrot.io.intr_kmac_kmac_done_o
    io_rot.intr(4) := tlrot.io.intr_kmac_fifo_empty_o
    io_rot.intr(5) := tlrot.io.intr_kmac_kmac_err_o

    io_rot.intr(6) := tlrot.io.intr_keymgr_op_done_o

    io_rot.intr(7) := tlrot.io.intr_csrng_cs_cmd_req_done_o
    io_rot.intr(8) := tlrot.io.intr_csrng_cs_entropy_req_o
    io_rot.intr(9) := tlrot.io.intr_csrng_cs_hw_inst_exc_o
    io_rot.intr(10) := tlrot.io.intr_csrng_cs_fatal_err_o

    io_rot.intr(11) := tlrot.io.intr_entropy_src_es_entropy_valid_o
    io_rot.intr(12) := tlrot.io.intr_entropy_src_es_health_test_failed_o
    io_rot.intr(13) := tlrot.io.intr_entropy_src_es_observe_fifo_ready_o
    io_rot.intr(14) := tlrot.io.intr_entropy_src_es_fatal_err_o

    io_rot.intr(15) := tlrot.io.intr_edn0_edn_cmd_req_done_o
    io_rot.intr(16) := tlrot.io.intr_edn0_edn_fatal_err_o


    // in.a <> tlrot.io.tl_i
    // in.d <> tlrot.io.tl_o
    // in.d.ready := tlrot.io.tl_o.ready 
    tlrot.io.clk_i := io_rot.clock

    val rst_wire = Wire(Reset())
    rst_wire := io_rot.reset
    tlrot.io.rst_ni := rst_wire
    tlrot.io.rst_ni := io_rot.reset
  }
}

// class TLROT_blackbox(implicit p: Parameters) extends LazyModule {

//   val mem = SyncReadMem(0x1000, UInt(32.W))

//   val node = TLManagerNode(Seq(TLSlavePortParameters.v1(
//     Seq(TLSlaveParameters.v1(
//       address = Seq(AddressSet(0x3b000000L, 0xffff)),
//       resources = mem)),
//       beatBytes =8  
//   )))

//   lazy val module = new LazyModuleImp(this) {

//     val io_rot = IO(new Bundle { 
//       val clock = Input(Clock())
//       val reset = Input(AsyncReset())
//   })

//     val (in, edge) = node.in(0)
//     dontTouch(in)
    
//     val tlrot = Module(new TLROT_top) // 黑盒模块

//     // 连接黑盒和内存
//     tlrot.io.clk_i := io_rot.clock
//     tlrot.io.rst_ni := io_rot.reset

//     tlrot.io.tl_i <> mem.read
//     mem.write <> tlrot.io.tl_o

//     // 连接外部TL和内存 
//     in._1 <> mem.read
//     mem.write <> in._1

//   }

// }
