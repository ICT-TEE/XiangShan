
// `include "/nfs/home/zhangdongrong/thinclient_drives/xs-env/XiangShan/src/main/resources/TLROT/lowrisc_dv_rot_top_verilator_sim_0.1.f"
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
    output         d_bits_denied
);

tlul_pkg::tl_h2d_t tl_i;
tlul_pkg::tl_d2h_t tl_o;
logic clk_edn_i;
logic rst_edn_ni;
logic rst_shadowed_ni;

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
    .tl_o(tl_o) 
);
    
endmodule