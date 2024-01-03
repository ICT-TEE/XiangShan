module MUL (
    input clk,
    input rst,
    input [1:0] in,
    output [1:0] out
);

reg [1:0] out_reg;
assign out = out_reg;

always @(posedge clk or negedge rst) begin
    if (!rst) begin
        out_reg <= 2'd0;
    end else begin
        out_reg <= in;
    end
    
end
    
endmodule