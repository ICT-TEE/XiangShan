package tlul_pkg;

localparam int TL_AW=32;
localparam int TL_DW=32;    // = TL_DBW * 8; TL_DBW must be a power-of-two
localparam int TL_AIW=8;    // a_source, d_source
localparam int TL_DIW=1;    // d_sink
localparam int TL_AUW=21;   // a_user
localparam int TL_DUW=14;   // d_user
localparam int TL_DBW=(TL_DW>>3);
localparam int TL_SZW=$clog2($clog2(TL_DBW)+1);

  typedef enum logic [2:0] {
    PutFullData    = 3'h 0,
    PutPartialData = 3'h 1,
    Get            = 3'h 4
  } tl_a_op_e;

  typedef enum logic [2:0] {
    AccessAck     = 3'h 0,
    AccessAckData = 3'h 1
  } tl_d_op_e;


  typedef struct packed {
    logic                         a_valid;
    tl_a_op_e                     a_opcode;
    logic                  [2:0]  a_param;
    logic  [TL_SZW-1:0]  a_size;
    logic  [TL_AIW-1:0]  a_source;
    logic   [TL_AW-1:0]  a_address;
    logic  [TL_DBW-1:0]  a_mask;
    logic   [TL_DW-1:0]  a_data;
    logic                         d_ready;
  } tl_h2d_t;



  typedef struct packed {
    logic                         d_valid;
    tl_d_op_e                     d_opcode;
    logic                  [2:0]  d_param;
    logic  [TL_SZW-1:0]  d_size;   // Bouncing back a_size
    logic  [TL_AIW-1:0]  d_source;
    logic  [TL_DIW-1:0]  d_sink;
    logic   [TL_DW-1:0]  d_data;
    logic                         d_error;

    logic                         a_ready;

  } tl_d2h_t;
endpackage