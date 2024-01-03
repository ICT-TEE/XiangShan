package device_test

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.regmapper.{RegField, RegFieldAccessType, RegFieldDesc, RegFieldGroup}


// class TLROT(implicit p: Parameters) extends LazyModule {

//   lazy val module = new LazyModuleImp(this) {
//     val io = IO(new Bundle {
//       val in = Input(UInt(32.W))
//       val out = Output(UInt(32.W))
//     })

//     // 实现一个简单的循环移位操作
//     val out_3_0 = Wire(Vec(4, UInt(8.W)))
//     out_3_0(0) := io.in(7, 0)
//     out_3_0(1) := io.in(15, 8)
//     out_3_0(2) := io.in(23, 16)
//     out_3_0(3) := io.in(31, 24)

//     val out_0_3 = Wire(Vec(4, UInt(8.W)))
//     out_0_3(0) := out_3_0(1)
//     out_0_3(1) := out_3_0(2)
//     out_0_3(2) := out_3_0(3)
//     out_0_3(3) := out_3_0(0)

//     io.out := Cat(out_0_3(3), out_0_3(2), out_0_3(1), out_0_3(0))

//     // 实现寄存器映射
//     val reg = RegInit(0.U(32.W))
//     reg := io.in
//     io.out := reg
//   }
// }

class TLROT_test(implicit p: Parameters) extends LazyModule {

  val node = TLRegisterNode(
    address = Seq(AddressSet(0x3b000000L, 0xff)),
    device = new SimpleDevice("tlrot", Seq()),
    beatBytes = 8,
    concurrency = 1
  )

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val in = Output(UInt(32.W))
      val out = Output(UInt(32.W))
    })

    val out_3_0 = Wire(Vec(4, UInt(8.W)))
    out_3_0(0) := io.in(7, 0)
    out_3_0(1) := io.in(15, 8)
    out_3_0(2) := io.in(23, 16)
    out_3_0(3) := io.in(31, 24)

    val out_0_3 = Wire(Vec(4, UInt(8.W)))
    out_0_3(0) := out_3_0(1)
    out_0_3(1) := out_3_0(2)
    out_0_3(2) := out_3_0(3)
    out_0_3(3) := out_3_0(0)

    io.out := Cat(out_0_3(3), out_0_3(2), out_0_3(1), out_0_3(0))

    node.regmap(
      0x00 -> Seq(RegField(32, io.in, RegFieldDesc("in", "Input Register"))),
      0x04 -> Seq(RegField(32, io.out, RegFieldDesc("out", "Output Register")))
    )
 
  }
}

