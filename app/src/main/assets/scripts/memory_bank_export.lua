local dir = "/sdcard/Download/"
local f = io.open(dir .. "bank0.csv", "w")
if f then
  f:write("Ch,Freq,Mode,BW\n")
  for ch=0, 99 do
    local m = radio.readMemory(0, ch)
    if m.freq > 0 then f:write(ch..","..m.freq..","..m.mode..","..m.bw.."\n") end
  end
  f:close()
  radio.log("Export complete")
end