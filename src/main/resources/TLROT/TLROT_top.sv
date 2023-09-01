
// `include "/nfs/home/zhangdongrong/thinclient_drives/xs-env/XiangShan/src/
// main/resources/TLROT/lowrisc_dv_rot_top_verilator_sim_0.1.f"
import tlul_pkg::*;

module TLROT_top (
    input clk_i,
    input rst_ni,
    output         a_ready,
    input          a_valid,
    input  [2:0]   a_bits_opcode,
    input  [2:0]   a_bits_param,
    input  [1:0]   a_bits_size,
    input  [7:0]   a_bits_source,
    input  [31:0]  a_bits_address,
    input  [3:0]   a_bits_mask,
    input  [31:0]  a_bits_data,
    input         d_ready,
    output          d_valid,
    output [2:0]   d_bits_opcode,
    output [2:0]   d_bits_param,
    output [1:0]   d_bits_size,
    output [7:0]   d_bits_source,
    output         d_bits_sink,
    output [31:0]  d_bits_data,
    output         d_bits_denied,

    output logic intr_hmac_hmac_done_o,
    output logic intr_hmac_fifo_empty_o,
    output logic intr_hmac_hmac_err_o,
    output logic intr_kmac_kmac_done_o,
    output logic intr_kmac_fifo_empty_o,
    output logic intr_kmac_kmac_err_o,
    output logic intr_keymgr_op_done_o,
    output logic intr_csrng_cs_cmd_req_done_o,
    output logic intr_csrng_cs_entropy_req_o,
    output logic intr_csrng_cs_hw_inst_exc_o,
    output logic intr_csrng_cs_fatal_err_o,
    output logic intr_entropy_src_es_entropy_valid_o,
    output logic intr_entropy_src_es_health_test_failed_o,
    output logic intr_entropy_src_es_observe_fifo_ready_o,
    output logic intr_entropy_src_es_fatal_err_o,
    output logic intr_edn0_edn_cmd_req_done_o,
    output logic intr_edn0_edn_fatal_err_o
);

tlul_pkg::tl_h2d_t tl_i;
tlul_pkg::tl_d2h_t tl_o;
logic clk_edn_i;
logic rst_edn_ni;
logic rst_shadowed_ni;



// always_comb begin
//   if (a_bits_opcode == 3'b001 && 
//       (a_bits_mask == 4'hf || a_bits_mask == 4'h0)) begin
//     tl_i.a_opcode = 3'b000; 
//     tl_i.a_mask = 4'hf;
//   end else if (a_bits_opcode == 3'b100) begin
//     tl_i.a_opcode = 3'b100; 
//     tl_i.a_mask = 4'hf;
//   end else begin
//     tl_i.a_opcode = a_bits_opcode;
//     tl_i.a_mask = a_bits_mask; 
//   end
// end

assign tl_i.a_valid = a_valid;
assign tl_i.a_opcode = a_bits_opcode;  
assign tl_i.a_param = a_bits_param;
assign tl_i.a_size = a_bits_size;
assign tl_i.a_source = a_bits_source;
assign tl_i.a_address = a_bits_address;
assign tl_i.a_mask = a_bits_mask;
assign tl_i.a_data = a_bits_data;

assign tl_i.a_user = tlul_pkg::TL_A_USER_DEFAULT;
// assign tl_o.d_user = tlul_pkg:TL_D_USER_DEFAULT;

assign a_ready = tl_o.a_ready;

assign d_valid = tl_o.d_valid;
assign d_bits_opcode = tl_o.d_opcode;
assign d_bits_param = tl_o.d_param;
assign d_bits_size = tl_o.d_size;  
assign d_bits_source = tl_o.d_source;
assign d_bits_sink  = tl_o.d_sink;
assign d_bits_data = tl_o.d_data;
assign d_bits_denied = tl_o.d_error;

assign tl_i.d_ready = d_ready;

//rst_ni reverse reset!
rot_top u_rot_top (
    .clk_i(clk_i),
    .rst_ni(~rst_ni),
    .rst_shadowed_ni(~rst_ni),
    .clk_edn_i(clk_i),
    .rst_edn_ni(~rst_ni),

    .tl_i(tl_i),
    .tl_o(tl_o),

    .intr_hmac_hmac_done_o(intr_hmac_hmac_done_o),
    .intr_hmac_fifo_empty_o(intr_hmac_fifo_empty_o),  
    .intr_hmac_hmac_err_o(intr_hmac_hmac_err_o),
    .intr_kmac_kmac_done_o(intr_kmac_kmac_done_o),
    .intr_kmac_fifo_empty_o(intr_kmac_fifo_empty_o),
    .intr_kmac_kmac_err_o(intr_kmac_kmac_err_o),
    .intr_keymgr_op_done_o(intr_keymgr_op_done_o),
    .intr_csrng_cs_cmd_req_done_o(intr_csrng_cs_cmd_req_done_o),
    .intr_csrng_cs_entropy_req_o(intr_csrng_cs_entropy_req_o),  
    .intr_csrng_cs_hw_inst_exc_o(intr_csrng_cs_hw_inst_exc_o),
    .intr_csrng_cs_fatal_err_o(intr_csrng_cs_fatal_err_o),
    .intr_entropy_src_es_entropy_valid_o(intr_entropy_src_es_entropy_valid_o),
    .intr_entropy_src_es_health_test_failed_o(intr_entropy_src_es_health_test_failed_o),  
    .intr_entropy_src_es_observe_fifo_ready_o(intr_entropy_src_es_observe_fifo_ready_o),
    .intr_entropy_src_es_fatal_err_o(intr_entropy_src_es_fatal_err_o),
    .intr_edn0_edn_cmd_req_done_o(intr_edn0_edn_cmd_req_done_o),
    .intr_edn0_edn_fatal_err_o(intr_edn0_edn_fatal_err_o)
);
    
endmodule