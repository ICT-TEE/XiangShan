`include "./tlul_pkg.sv"
import tlul_pkg::*;

module TLROT (
    input clk_i,
    input rst_ni,


    // Bus Interface
    input  tlul_pkg::tl_h2d_t tl_i,
    output tlul_pkg::tl_d2h_t tl_o,

    // Interrupt
    output logic done_o
);

  // Register for storing the value written to tl_i
  logic [31:0] reg_value = 0;

  // Logic for updating reg_value when tl_i is written
  always @(posedge clk_i) begin
    if (!rst_ni) begin
      reg_value <= 0;
    end else if (tl_i.a_valid && tl_i.a_opcode == tlul_pkg::PutFullData && tl_i.a_address == 32'h3b00_0000) begin
      reg_value <= tl_i.a_data;
    end
  end

  // Logic for setting done_o when reg_value is not zero
  always @(posedge clk_i) begin
    if (!rst_ni) begin
      done_o <= 0;
    end else if (reg_value != 0) begin
      done_o <= 1;
    end else begin
      done_o <= 0;
    end
  end

  // Logic for responding to tl_o reads
  always @(posedge clk_i) begin
    if (!rst_ni) begin
      tl_o <= '0;
    end else if (tl_i.d_ready && tl_i.a_opcode == tlul_pkg::Get && tl_i.a_address == 32'h3b00_0000) begin
      tl_o.d_data := reg_value;
      tl_o.d_opcode := tlul_pkg::AccessAckData;
      tl_o.d_size := tl_i.a_size;
      tl_o.d_param := tl_i.a_param;
      tl_o.d_error := 0;
      tl_o.a_ready := 1;
      tl_o.d_sink := tl_i.a_source;
    end else begin
      tl_o.a_ready := 0;
    end
  end

endmodule